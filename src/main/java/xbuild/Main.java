package xbuild;

import java.io.*;
import java.math.BigInteger;
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
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;

import com.google.common.collect.*;

// https://semver.org
class Version {

  private final String major;
  private final String minor;
  private final String patch;
  private final String pre;
  private final String build;

  public Version(String version) throws Exception {
    Matcher m = p.matcher(version);
    if (m.find()) {
      major = m.group(1);
      minor = m.group(2);
      patch = m.group(3);
      pre = m.group(4);
      build = m.group(5);
    } else
      throw new Exception(version);
  }

  public Version(String major, String minor, String patch) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.pre = null;
    this.build = null;
  }

  public String render() {
    String result = String.format("%s.%s.%s", major, minor, patch);
    if (pre!=null) {
      result = String.format("%s-%s", result, pre);
      if (build!=null)
        result = String.format("%s+%s", result, build);
    }
    return result;
  }

  public Version incrementMajor() {
    String major = new BigInteger(this.major).add(BigInteger.ONE).toString();
    String minor = "0";
    String patch = "0";
    return new Version(major, minor, patch);
  }

  // https://semver.org
  static final Pattern p = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?");
}

/**
 * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle
 */
@SpringBootApplication
public class Main implements ApplicationRunner {

	public static void main(String[] args) throws Exception {
//	  args = new String[] {"--tag"};
    SpringApplication.run(Main.class, args);

    // System.out.println(new Version("xbuild-2.3.4-alpha+asdf-master").render());
    // System.out.println(new Version("2.3.4-abc").render());
    // System.out.println(new Version("2.3.4-abc+123").render());

  }
	
  static {
    ArchiveFormats.registerAll();
  }

  public Main(ApplicationContext context) {
    BuildProperties buildProperties = context.getBeanProvider(BuildProperties.class).getIfAvailable();
    if (buildProperties != null)
      log("buildProperties", buildProperties);
  }

  @Override
	public void run(ApplicationArguments args) throws Exception {

    // log("run", Lists.newArrayList(args.getSourceArgs()));

    //###TODO finalize this
    //###TODO finalize this
    //###TODO finalize this
    Repository repository = new FileRepositoryBuilder()
        // .setGitDir(new File(".git"))
         .readEnvironment() // scan environment GIT_* variables
         .findGitDir() // scan up the file system tree
        .build();
    //###TODO finalize this
    //###TODO finalize this
    //###TODO finalize this

    //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
    //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
    //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
    // remote
    final String remote = repository.getRemoteNames().iterator().next(); // e.g., "origin"
    //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
    //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
    //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
    
    // branch
    final String branch = repository.getBranch(); // e.g., "master"
    
    try (Git git = new Git(repository)) {

      // rev=remote/branch
      // if script then rev=latest
      // if number then rev=number

      // xbuild # build remote/branch (e.g., origin/master) and create new tag
      // xbuild deploy-dev # build latest and run deploy script
      // xbuild deploy-dev 234 # build 234 and run deploy script

      // number
      int number = 0;
      // revision
      String revision = String.format("%s/%s", remote, branch);

      // get latest buildNumber
      Map<Integer, String> allTags = Maps.newTreeMap();
      Map<String, Integer> tagToNumber = Maps.newTreeMap();
      for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_TAGS)) {
        // e.g., xbuild-234-master
        if (ref.getName().contains("xbuild")) {
          // extract num from ref
          int num = Integer.parseInt(search("[0-9]+", ref.getName()).iterator().next());
          allTags.put(num, ref.getName());
        }
      }
      if (allTags.size()>0)
        number = Iterables.getLast(allTags.keySet());
      
      // % xbuild ?
      if (args.getNonOptionArgs().size() == 0) {
        // yes
        ++number;
        String lastTag = Iterables.getLast(allTags.values()); // e.g., xbuild-234-master
        // log("lastTag", lastTag);
        try (ObjectReader reader = repository.newObjectReader()) {
          CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
          oldTreeParser.reset(reader, repository.resolve(lastTag + "^{tree}"));
          CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
          newTreeParser.reset(reader, repository.resolve(revision + "^{tree}"));
          log("git diff", lastTag, revision);
          if (git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).call().size() == 0) {
            throw new RuntimeException("no diff");
          }
        }
      }

      // % xbuild number ?
      for (String arg : args.getNonOptionArgs()) {
        if (arg.matches("[0-9]+")) {
          number = Integer.parseInt(arg);
          revision = Objects.requireNonNull(allTags.get(number), "bad xbuild number");
        }
      }

      // commit
      RevCommit commit = repository.parseCommit(repository.resolve(revision+"^{commit}"));
      // timestamp
      String commitTime = Instant.ofEpochSecond(commit.getCommitTime()).toString();

      Map<String, String> env = Maps.newTreeMap();
      env.put("XBUILD", "1"); // "xbuild is running" signal
      env.put("XBUILD_BRANCH", branch);
      env.put("XBUILD_NUMBER", ""+number);
      env.put("XBUILD_COMMIT", commit.abbreviate(7).name());
      env.put("XBUILD_COMMITTIME", commitTime);
      
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
      else if (new File(".xbuild").exists()) // legacy
        run(tmpDir, env, "./.xbuild");

      // % xbuild ?
      if (args.getNonOptionArgs().size()==0) {
        String tag = String.format("xbuild-%s-%s", number, branch);
        // git tag
        log("git tag", tag);
        Ref ref = git.tag().setName(tag).call();
        // git push origin tag
        log("git push", remote, tag);
        RefSpec refSpec = new RefSpec(ref.getName());
        git.push().setRemote(remote).setRefSpecs(refSpec).call();
      }
      
      // run deploy script, e.g., xdeploy-dev
      for (String arg : args.getNonOptionArgs()) {
        if (new File(arg).exists())
          run(tmpDir, env, String.format("./%s", arg));
      }

    }
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
			throw new Exception(cwd.toString()+env.toString()+command.toString());
	}

	/**
	 * search
	 * 
	 * @param regex
	 * @param input
	 * @return
	 */
  private List<String> search(String regex, String input) {
    List<String> list = new ArrayList<>();
    Matcher m = Pattern.compile(regex).matcher(input);
    while (m.find())
      list.add(m.group(0));
    return list;
  }

	private void log(Object... args) {
		new LogHelper(Main.class).log(args);
	}

}
