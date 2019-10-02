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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.archive.ArchiveFormats;
import org.eclipse.jgit.lib.ObjectId;
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
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;

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

  private final ApplicationContext context;

  public MainTwo(ApplicationContext context) {
    this.context = context;
  //   BuildProperties buildProperties = context.getBeanProvider(BuildProperties.class).getIfAvailable();
  //   if (buildProperties != null) {
  //     log("version", buildProperties.getVersion());
  //     // for (BuildProperties.Entry entry : buildProperties)
  //     //   log(entry.getKey(), entry.getValue());
  //   }
}

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

  // git rev-list master --count --first-parent
  // https://stackoverflow.com/questions/14895123/auto-version-numbering-your-android-app-using-git-and-eclipse/20584169#20584169
  private BiMap<Integer, RevCommit> countFirstParent(Repository repo, String branch) throws Exception {
    BiMap<Integer, RevCommit> numberToCommit = HashBiMap.create();

    try (RevWalk walk = new RevWalk(repo)) {
      int count = -1;
      String revision = String.format("refs/heads/%s", branch);
      RevCommit head = walk.parseCommit(repo.findRef(revision).getObjectId());
      while (head != null) {
        ++count;
        numberToCommit.put(count, head);
        RevCommit[] parents = head.getParents();
        head = null;
        if (parents != null && parents.length > 0)
          head = walk.parseCommit(parents[0]);
      }
    }

    return reverse(numberToCommit);
  }

  private boolean verbose;

  @Override
  public void run(ApplicationArguments args) throws Exception {

    verbose = args.getOptionNames().contains("verbose");

    if (args.getOptionNames().contains("version")) {
      String version = "version";
      BuildProperties buildProperties = context.getBeanProvider(BuildProperties.class).getIfAvailable();
      if (buildProperties != null) {
        version = buildProperties.getVersion();
        // for (BuildProperties.Entry entry : buildProperties)
        // log(entry.getKey(), entry.getValue());
      }
      log(version);
    } else {

      try (Git git = createGit(args)) {
        Repository repo = git.getRepository();
  
        String branch = repo.getBranch(); // e.g., "master"
        BiMap<Integer, RevCommit> numberToCommit = countFirstParent(repo, branch);
        int number = 0;
        RevCommit commit = null;
        List<String> scripts = Lists.newArrayList();

        for (String arg : args.getNonOptionArgs()) {
          // is it a branch?
          Ref ref = repo.findRef(arg);
          if (ref != null) {
            log("branch", arg);
            branch = arg;
            numberToCommit = countFirstParent(repo, branch);
          } else {
            // is it a commit?
            ObjectId objectId = null;
            //###TODO THINK ABOUT NUMBER AND COMMIT AMBIGUITY
            //###TODO THINK ABOUT NUMBER AND COMMIT AMBIGUITY
            //###TODO THINK ABOUT NUMBER AND COMMIT AMBIGUITY
            if (arg.length()>=7)
              objectId = repo.resolve(arg);
            //###TODO THINK ABOUT NUMBER AND COMMIT AMBIGUITY
            //###TODO THINK ABOUT NUMBER AND COMMIT AMBIGUITY
            //###TODO THINK ABOUT NUMBER AND COMMIT AMBIGUITY
            if (objectId != null) {
              log("commit", arg);
              commit = repo.parseCommit(objectId);
            } else {
              // is it a number?
              if (arg.matches("[0-9]+")) {
                log("number", arg);
                number = Integer.parseInt(arg);
                // commit = Objects.requireNonNull(numberToCommit.get(number), "bad commit
                // number");
              } else {
                // is it a script?
                log("script", arg);
                scripts.add(arg);
              }
            }
          }
        }
  
        // refs/remotes/origin/master
        // bare: c7c03329ef0ae21496552219a38caa6d16dfb73f refs/heads/master
        // not bare: 514dc7579c43e673bdf613e01690371438661260 refs/remotes/origin/master
  
        // if (!repo.isBare())
        // revision = String.format("refs/remotes/%s/%s", remote, branch);
  
  
                  // // git rev-list master --count --first-parent
                  // // https://stackoverflow.com/questions/14895123/auto-version-numbering-your-android-app-using-git-and-eclipse/20584169#20584169
                  // try (RevWalk walk = new RevWalk(repo)) {
                  //   int count = -1;
                  //   String revision = String.format("refs/heads/%s", branch);
                  //   RevCommit head = walk.parseCommit(repo.findRef(revision).getObjectId());
                  //   while (head != null) {
                  //     ++count;
                  //     numberToCommit.put(count, head);
                  //     RevCommit[] parents = head.getParents();
                  //     head = null;
                  //     if (parents != null && parents.length > 0)
                  //       head = walk.parseCommit(parents[0]);
                  //   }
                  // }
            
                  // numberToCommit = fix(numberToCommit);
  
        // for (RevCommit commit : commitList)
        {
          // String name = String.format("%s-%s-%s", "master", count,
          // commit.abbreviate(7).name());
          // log(name);
          // Ref ref = repo.findRef(name);
          // if (ref==null) {
          // //
          // log(git.tag().setName(name).setObjectId(commit).setForceUpdate(true).call());
          // // annotated
          // //
          // log(git.tag().setName(name).setObjectId(commit).setAnnotated(false).setForceUpdate(true).call());
          // // not annotated
          // }
          // --count;
        }
  
        // latest commit number
        if (number != 0)
          commit = Objects.requireNonNull(numberToCommit.get(number), "bad commit number");
        else if (commit != null)
          number = numberToCommit.inverse().get(commit);
        else {
          number = Iterables.getLast(Sets.newTreeSet(numberToCommit.keySet()));
          commit = Objects.requireNonNull(numberToCommit.get(number), "bad commit number");
        }
  
        // // % xbuild number ?
        // for (String arg : args.getNonOptionArgs()) {
        //   if (arg.matches("[0-9]+")) {
        //     number = Integer.parseInt(arg);
        //     commit = Objects.requireNonNull(numberToCommit.get(number), "bad commit number");
        //   }
        // }
  
        // // query branch+commit?
        // // for (String arg : args.getNonOptionArgs())
        // {
        //   ObjectId objectId = repo.resolve(arg);
        //   if (objectId != null) {
        //     commit = repo.parseCommit(objectId);
        //     number = numberToCommit.inverse().get(commit);
            // String xbuild = String.format("%s-%s-%s", branch, number, commit.abbreviate(7).name());
            // System.out.println(xbuild);
        //     exit();
        //   }
        // }
  
        // log("number", number);
        // log("commit", commit);
  
        // timestamp
        String commitTime = Instant.ofEpochSecond(commit.getCommitTime()).toString();
  
        Map<String, String> env = Maps.newTreeMap();
  
        String xbuild = String.format("%s-%s-%s", branch, number, commit.abbreviate(7).name());
        env.put("XBUILD", xbuild); // "xbuild is running"
        env.put("XBUILD_BRANCH", branch);
        env.put("XBUILD_NUMBER", "" + number);
        env.put("XBUILD_COMMIT", commit.abbreviate(7).name());
        env.put("XBUILD_COMMITTIME", commitTime);
        env.put("XBUILD_DATETIME", commitTime); // ###LEGACY###
  
        log(env);
  
        if (args.getOptionNames().contains("build")) {
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
          for (String script : scripts) {
            File file = new File(tmpDir.toFile(), script);
            if (file.exists()) {
              if (file.isFile())
                Posix.run(tmpDir, env, String.format("./%s", script));
            }
          }
        }
      }
  
    }

  }

  private BiMap<Integer, RevCommit> reverse(BiMap<Integer, RevCommit> input) {
    BiMap<Integer, RevCommit> output = HashBiMap.create();;
    for (Map.Entry<Integer, RevCommit> entry : input.entrySet())
      output.put(input.size()-entry.getKey(), entry.getValue());
    return output;
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

  // private void exit() {
  //   System.exit(SpringApplication.exit(context, ()->0));
  // }

	private void log(Object... args) {
		new LogHelper(this).log(args);
	}

}
