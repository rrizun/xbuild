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
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * xbuild
 * 
 * if arg is a url then it is interpreted as the remote git url
 * if arg is a branch then it is interpreted as the build branch
 * if arg is a number then it is interpreted as the build number
 * if arg is a commit then it is interpreted as the build commit
 * if arg is a file then it is interpreted as the deploy script
 */
@SpringBootApplication // https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle
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

  private File getGitDir(ApplicationArguments args) {
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
            .setBare(true)
            .setDirectory(tempDirectory.toFile())
            .setURI(arg)
            .setProgressMonitor(new ProgressMonitor(){
              @Override
              public void start(int totalTasks) {
              }
              @Override
              public void beginTask(String title, int totalWork) {
                log(title, totalWork);
              }
              @Override
              public void update(int completed) {
              }
              @Override
              public void endTask() {
              }
              @Override
              public boolean isCancelled() {
                return false;
              }
            })
        // .setURI("/home/rrizun/git/ground-service-old")
        // .setURI("git@github.com:rrizun/xbuild-java.git")
        ;
        return cloneCommand.call();
      }
    }

    // try to infer local git dir
    Repository repository = new FileRepositoryBuilder()
        .setGitDir(getGitDir(args)) // --git-dir if supplied, no-op if null
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
      String branch = repo.getBranch(); // e.g., "master"

      for (String arg : args.getNonOptionArgs()) {
        Ref ref = repo.findRef(arg);
        if (ref!=null)
          branch = arg;
      }

      log("branch", branch);

      // refs/remotes/origin/master
      // bare: c7c03329ef0ae21496552219a38caa6d16dfb73f refs/heads/master
      // not bare: 514dc7579c43e673bdf613e01690371438661260 refs/remotes/origin/master

      String revision = String.format("refs/heads/%s", branch);
      // if (!repo.isBare())
      //   revision = String.format("refs/remotes/%s/%s", remote, branch);
  
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

            String xbuild = String.format("%s-%s-%s", branch, number, commit.abbreviate(7).name());
            env.put("XBUILD", xbuild); // "xbuild is running"
            env.put("XBUILD_BRANCH", branch);
            env.put("XBUILD_NUMBER", ""+number);
            env.put("XBUILD_COMMIT", commit.abbreviate(7).name());
            env.put("XBUILD_COMMITTIME", commitTime);
            env.put("XBUILD_DATETIME", commitTime); // ###LEGACY###
            
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
      if (new File(tmpDir.toFile(), "xbuildfile").exists())
        Posix.run(tmpDir, env, "./xbuildfile");
      else if (new File(tmpDir.toFile(), ".xbuild").exists())
        Posix.run(tmpDir, env, "./.xbuild"); // legacy

      // run deploy script, e.g., xdeploy-dev
      for (String arg : args.getNonOptionArgs()) {
        File file = new File(tmpDir.toFile(), arg);
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
