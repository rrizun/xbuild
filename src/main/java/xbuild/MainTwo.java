package xbuild;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Maps;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.archive.ArchiveFormats;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle
 * 
 * if arg is a git url then it is interpreted as the git url
 * if arg is a number then it is interpreted as the build number
 * if arg is a file then it is interpreted as the deploy script
 * if arg is a commitish then it is interpreted as the build commit
 */
@SpringBootApplication
public class MainTwo implements ApplicationRunner {

	public static void main(String[] args) throws Exception {
    // args = new String[]{"git@github.com:rrizun/xbuild-java.git"};
    SpringApplication.run(MainTwo.class, args);
  }
	
  static {
    ArchiveFormats.registerAll();
  }

  // public MainTwo(ApplicationContext context) {
  //   BuildProperties buildProperties = context.getBeanProvider(BuildProperties.class).getIfAvailable();
  //   if (buildProperties != null) {
  //     log("version", buildProperties.getVersion());
  //     // for (BuildProperties.Entry entry : buildProperties)
  //     //   log(entry.getKey(), entry.getValue());
  //   }
  // }

  private File gitDir(ApplicationArguments args) {
    List<String> values = args.getOptionValues("git-dir");
    if (values != null) {
      for (String value : values)
        return new File(value);
    }
    return null;
  }

  private Git createGit(ApplicationArguments args) throws Exception {

    // try to infer git url, e.g.,
    // git@github.com:torvalds/linux.git
    // https://github.com/torvalds/linux.git
    for (String arg : args.getNonOptionArgs()) {
      if (arg.contains(":")) {
        Path tempDirectory = Files.createTempDirectory("xbuild");
        log(tempDirectory);
        CloneCommand cloneCommand = Git.cloneRepository()
            // .setBare(true)
            .setDirectory(tempDirectory.toFile())
            .setURI(args.getNonOptionArgs().iterator().next())
        // .setURI("/home/rrizun/git/ground-service-old")
        // .setURI("git@github.com:rrizun/xbuild-java.git")
        ;
        return cloneCommand.call();
      }
    }

    // try to infer local git dir
    Repository repository = new FileRepositoryBuilder()
        .setGitDir(gitDir(args)) // --git-dir if supplied, no-op if null
        .readEnvironment() // scan environment GIT_* variables
        .findGitDir() // scan up the file system tree
        // .setBare()
        .build();
    return new Git(repository);
  }

  @Override
	public void run(ApplicationArguments args) throws Exception {
    try (Git git = createGit(args)) {
      Repository repo = git.getRepository();
  
      //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
      //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
      //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
      // remote
      final String remote = repo.getRemoteNames().iterator().next(); // e.g., "origin"
      log("remote", remote);
      //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
      //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
      //###TODO throw if getRemoteNames().size()!=1 and have a --remote option
      final String branch = repo.getBranch(); // e.g., "master"
      log("branch", branch);
  
      String revision = String.format("refs/remotes/%s/%s", remote, branch);
  
      Map<Integer, RevCommit> commits = Maps.newTreeMap();

      // git rev-list master --count --first-parent
      // https://stackoverflow.com/questions/14895123/auto-version-numbering-your-android-app-using-git-and-eclipse/20584169#20584169
      try (RevWalk walk = new RevWalk(repo)) {
        int count = 0;
        RevCommit head = walk.parseCommit(repo.findRef(revision).getObjectId());
        while (head != null) {
          commits.put(count++, head);
          RevCommit[] parents = head.getParents();
          if (parents != null && parents.length > 0) {
            head = walk.parseCommit(parents[0]);
          } else {
            head = null;
          }
        }
      }
  
      // for (RevCommit commit : commitList)
      {
        // String name = String.format("%s-%s-%s", "master", count, commit.abbreviate(7).name());
        // log(name);
        // Ref ref = repo.findRef(name);
        // if (ref==null) {
        //   // log(git.tag().setName(name).setObjectId(commit).setForceUpdate(true).call()); // annotated
        //   // log(git.tag().setName(name).setObjectId(commit).setAnnotated(false).setForceUpdate(true).call()); // not annotated
        // }
        // --count;
      }
  
        // commit number
        int number = commits.size() - commits.keySet().iterator().next();
        RevCommit commit = commits.get(commits.size() - number);
        // RevCommit commit = Iterables.getLast(commits.values());
  
      // % xbuild number ?
      for (String arg : args.getNonOptionArgs()) {
        if (arg.matches("[0-9]+")) {
          number = Integer.parseInt(arg);
          commit = Objects.requireNonNull(commits.get(commits.size() - number), "bad commit number");
        }
      }
  
      log("number", number);
      log("commit", commit);


            // timestamp
            String commitTime = Instant.ofEpochSecond(commit.getCommitTime()).toString();

            Map<String, String> env = Maps.newTreeMap();
            env.put("XBUILD", "1"); // "xbuild is running"
            env.put("XBUILD_BRANCH", branch);
            env.put("XBUILD_COMMIT", commit.abbreviate(7).name());
            env.put("XBUILD_COMMITTIME", commitTime);
            env.put("XBUILD_DATETIME", commitTime); // ###LEGACY###
            env.put("XBUILD_NUMBER", ""+number);
            
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
        Posix.run(tmpDir, env, "./xbuildfile");
      else if (new File(".xbuild").exists())
        Posix.run(tmpDir, env, "./.xbuild"); // legacy

      // run deploy script, e.g., xdeploy-dev
      for (String arg : args.getNonOptionArgs()) {
        File file = new File(arg);
        if (file.exists()) {
          if (file.isFile())
            Posix.run(tmpDir, env, String.format("./%s", arg));
        }
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
    TarArchiveEntry entry;
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

	private void log(Object... args) {
		new LogHelper(this).log(args);
	}

}
