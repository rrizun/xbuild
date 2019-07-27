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
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.treewalk.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

import com.google.common.collect.*;

/**
 * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle
 */
@SpringBootApplication
public class Main implements ApplicationRunner {

	public static void main(String[] args) {
//	  args = new String[] {"--tag"};
		SpringApplication.run(Main.class, args);
	}

  @Override
	public void run(ApplicationArguments args) throws Exception {

		log("run", Lists.newArrayList(args.getSourceArgs()));

    Repository repository = new FileRepositoryBuilder()
        .setGitDir(new File(".git"))
//         .readEnvironment() // scan environment GIT_* variables
//         .findGitDir() // scan up the file system tree
        .build();

    // branch
    String branch = repository.getBranch();

    try (Git git = new Git(repository)) {

      // allTags
      Set<String> allTags = Sets.newTreeSet(new HumanComparator());
      for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_TAGS)) {
        if (ref.getName().contains("xbuild"))
          allTags.add(ref.getName());
      }

      int buildNumber = 1;
      String revision = "HEAD";
      String tag = String.format("xbuild-%s-%s", branch, buildNumber);
      
      if (allTags.size() > 0) {
        
        buildNumber = Integer.parseInt(Iterables.getLast(search("[0-9]+", Iterables.getLast(allTags))));
        revision = String.format("refs/tags/xbuild-%s-%s", branch, buildNumber);
        tag = String.format("xbuild-%s-%s", branch, buildNumber);
        
        // are we creating a new tag?
        if (isCreateNewTag(args)) { // yes
          
          ++buildNumber;
          revision = "HEAD";
          tag = String.format("xbuild-%s-%s", branch, buildNumber);
          
          // is there a diff?
          String lastTag = Iterables.getLast(allTags);
          log("lastTag", lastTag);
          try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
            oldTreeParser.reset(reader, repository.resolve(lastTag+"^{tree}"));
            CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
            newTreeParser.reset(reader, repository.resolve(revision+"^{tree}"));
            if (git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).call().size() == 0)
              throw new RuntimeException("no diff"); // no
          }
        }
      }
      
      // objectId
      ObjectId objectId = repository.resolve(revision+"^{commit}");
      // commit
      String commit = objectId.abbreviate(7).name();
      // timestamp
      Instant commitTime = Instant.ofEpochSecond(repository.parseCommit(objectId).getCommitTime());
      String timestamp = commitTime.toString();

      Map<String, String> env = Maps.newHashMap();
      env.put("XBUILD_BRANCH", branch);
      env.put("XBUILD_COMMIT", commit);
      env.put("XBUILD_NUMBER", ""+buildNumber);
      env.put("XBUILD_TIMESTAMP", timestamp);
      
      log(env);

      ArchiveFormats.registerAll();
      
      // archive
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      
      git.archive()
        .setFormat("tar")
        .setOutputStream(baos)
        .setTree(objectId)
        .call();
      
      Path tmpDir = Files.createTempDirectory("xbuild");
      log(tmpDir);
      
      ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
      
      untar(in, tmpDir);
      
      // invoke xbuildfile

      run(tmpDir, env, "./xbuildfile");

      // xbuild --tag
      if (isCreateNewTag(args)) {
        log("tag", tag);
        git.tag().setName(tag).call();
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
