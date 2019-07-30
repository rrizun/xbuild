package xbuild;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

import org.apache.commons.compress.archivers.tar.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.archive.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.*;

import com.google.common.collect.*;

/**
 * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle
 */
@SpringBootApplication
public class Main implements ApplicationRunner {

  static {
    ArchiveFormats.registerAll();
  }

	public static void main(String[] args) {
//	  args = new String[] {"--tag"};
		SpringApplication.run(Main.class, args);
  }
	
	@Value("${spring.application.name}")
	private String applicationName;
	
  private final Environment env;

  public Main(BuildProperties buildProperties, Environment env) {
    this.env = env;
    Map<String, Object> m = Maps.newHashMap();
    for (BuildProperties.Entry entry : buildProperties)
      m.put(entry.getKey(), entry.getValue());
    log(m);
  }

  @Override
	public void run(ApplicationArguments args) throws Exception {

    log("app", applicationName);
    log("profiles", Lists.newArrayList(env.getActiveProfiles()));
    log("defaults", Lists.newArrayList(env.getDefaultProfiles()));

    log("run", Lists.newArrayList(args.getSourceArgs()));

    Repository repository = new FileRepositoryBuilder()
        .setGitDir(new File(".git"))
//         .readEnvironment() // scan environment GIT_* variables
//         .findGitDir() // scan up the file system tree
        .build();

    // remote
    //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
    String remote = repository.getRemoteNames().iterator().next(); // e.g., "origin"
    // branch
    String branch = repository.getBranch(); // e.g., "master"

    try (Git git = new Git(repository)) {

      // allTags
      Set<String> allTags = Sets.newTreeSet(new HumanComparator());
      for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_TAGS)) {
        if (ref.getName().contains("xbuild"))
          allTags.add(ref.getName());
      }

      int buildNumber = 1;
      String revision = String.format("%s/%s", remote, branch);
      String tag = String.format("xbuild-%s-%s", branch, buildNumber);
      
      if (allTags.size() > 0) {
        
        buildNumber = Integer.parseInt(Iterables.getLast(search("[0-9]+", Iterables.getLast(allTags))));
        revision = String.format("refs/tags/xbuild-%s-%s", branch, buildNumber);
        tag = String.format("xbuild-%s-%s", branch, buildNumber);
        
        // are we trying to create a new tag?
        if (isCreateNewTag(args)) { // yes

          ++buildNumber;
          revision = String.format("%s/%s", remote, branch);
          tag = String.format("xbuild-%s-%s", branch, buildNumber);

          // is there a diff?
          String lastTag = Iterables.getLast(allTags); // e.g., xbuild-master-5
          log("lastTag", lastTag);
          try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
            oldTreeParser.reset(reader, repository.resolve(lastTag+"^{tree}"));
            CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
            newTreeParser.reset(reader, repository.resolve(revision+"^{tree}"));
            log("git diff", lastTag, revision);
            if (git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).call().size() == 0) {
              // no- there is no diff
              throw new RuntimeException("no diff");
            }
          }
        }
        
      }
      
      // commit
      RevCommit commit = repository.parseCommit(repository.resolve(revision+"^{commit}"));
      // timestamp
      String timestamp = Instant.ofEpochSecond(commit.getCommitTime()).toString();

      Map<String, String> env = Maps.newHashMap();
      env.put("XBUILD", "1");
      env.put("XBUILD_BRANCH", branch);
      env.put("XBUILD_NUMBER", ""+buildNumber);
      env.put("XBUILD_COMMIT", commit.abbreviate(7).name());
      env.put("XBUILD_DATETIME", timestamp);
      
      log(env);

      // archive
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      
      log("git archive", commit.abbreviate(7).name());
      git.archive()
        .setFormat("tar")
        .setOutputStream(baos)
        .setTree(commit)
        .call();
      
      Path tmpDir = Files.createTempDirectory("xbuild");
      log(tmpDir);
      
      ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
      
      untar(in, tmpDir);
      
      // invoke xbuildfile
      if (new File("xbuildfile").exists())
        run(tmpDir, env, "./xbuildfile");
      else if (new File(".xbuild").exists())
        run(tmpDir, env, "./.xbuild"); // legacy

      // xbuild --tag
      if (isCreateNewTag(args)) {
        // git tag
        log("git tag", tag);
        Ref ref = git.tag().setName(tag).call();
        // git push origin tag
        log("git push", remote, tag);
        RefSpec refSpec = new RefSpec(ref.getName());
        git.push().setRemote(remote).setRefSpecs(refSpec).call();
      }
      
      // xbuild xdeploy-prod
      if (isRunDeployScript(args)) {
        run(tmpDir, env, String.format("./%s", args.getNonOptionArgs().get(0)));
      }

    }
	}
	
  // isCreateNewTag
	private boolean isCreateNewTag(ApplicationArguments args) {
	  return args.getOptionNames().contains("tag");
	}
	
	// isRunDeployScript
  private boolean isRunDeployScript(ApplicationArguments args) {
    return args.getNonOptionArgs().size() > 0;
  }
	
	/**
	 * untar
	 * 
	 * @param in
	 * @param outputPath
	 * @throws Exception
	 */
  private void untar(InputStream in, Path outputPath) throws Exception {
    TarArchiveEntry entry = null;
    TarArchiveInputStream tar = new TarArchiveInputStream(in);
    while ((entry = tar.getNextTarEntry()) != null) {
      Path entryPath = outputPath.resolve(entry.getName());
      if (entry.isDirectory()) {
        Files.createDirectories(entryPath);
      } else {
        Files.createDirectories(entryPath.getParent());
        Files.copy(tar, entryPath);
        Files.setPosixFilePermissions(entryPath, Posix.perms(entry.getMode()));
      }
    }
  }

	/**
	 * run
	 * 
	 * @param cwd
	 * @param env
	 * @param command
	 * @throws Exception
	 */
	private void run(Path cwd, Map<String, String> env, String... command) throws Exception {
		log("----------------------------------------------------------------------");
		log("run", Lists.newArrayList(command));
		log("----------------------------------------------------------------------");

		ProcessBuilder builder = new ProcessBuilder(command);
		  builder.directory(cwd.toFile());
			builder.environment().putAll(env);
			builder.redirectError(ProcessBuilder.Redirect.INHERIT);
			builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		if (builder.start().waitFor() != 0)
			throw new Exception();
	}

	/**
	 * search
	 * 
	 * @param regex
	 * @param s
	 * @return
	 */
  private List<String> search(String regex, String s) {
    List<String> list = new ArrayList<>();
    Matcher m = Pattern.compile(regex).matcher(s);
    while (m.find())
      list.add(m.group(0));
    return list;
  }

	private void log(Object... args) {
		new LogHelper(Main.class).log(args);
	}

}
