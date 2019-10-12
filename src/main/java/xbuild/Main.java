package xbuild;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

import org.apache.commons.compress.archivers.tar.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.archive.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.info.*;
import org.springframework.context.*;

import com.google.common.collect.*;

/**
 * xbuild
 */
@SpringBootApplication // https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle
public class Main implements ApplicationRunner {

  static {
    ArchiveFormats.registerAll();
  }

  public static void main(String[] args) throws Exception {
    // args = new String[]{"2add6c"};
    // args = new String[]{"git@github.com:xbuild-jar/xbuild-jar.git"};
    SpringApplication.run(Main.class, args);
  }

  private final ApplicationContext context;
  private final Optional<BuildProperties> buildProperties;

  public Main(ApplicationContext context, Optional<BuildProperties> buildProperties) {
    this.context = context;
    this.buildProperties = buildProperties;
  }

  private File getGitDir(ApplicationArguments args) {
    List<String> values = args.getOptionValues("git-dir");
    if (values != null) {
      for (String value : values)
        return new File(value);
    }
    return null;
  }

  private boolean debug;
  private boolean verbose = true;

  private File gitDir;
  private Git privateGit;
  // private String branch;
  // private final BiMap<String/*number*/, RevCommit> commitMap = walkFirstParent(git().getRepository(), branch);
  private String number;
  private RevCommit commit;
  private Path archivePath;
  private final List<String> scripts = Lists.newArrayList();

  // lazy
  private Git git() throws Exception {
    if (privateGit == null) {
      Repository repository = new FileRepositoryBuilder()
          // --git-dir if supplied, no-op if null
          .setGitDir(gitDir)
          // scan environment GIT_* variables
          .readEnvironment()
          // scan up the file system tree
          .findGitDir()
          //
          .build();
      privateGit = new Git(repository);
    }
    return privateGit;
  }

  private void setGit(String url) throws Exception {
    privateGit = Git.cloneRepository()
        //
        .setBare(true)
        //
        .setDirectory(Files.createTempDirectory("xbuild").toFile())
        //
        .setURI(url)
        //
        .setProgressMonitor(new ProgressMonitor() {
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
        //
        .call();

    log(privateGit.getRepository().getDirectory());
  }

  // git rev-list master --count --first-parent
  // https://stackoverflow.com/questions/14895123/auto-version-numbering-your-android-app-using-git-and-eclipse/20584169#20584169
  // https://stackoverflow.com/questions/33038224/how-to-call-git-show-first-parent-in-jgit
  private BiMap<String/*number*/, RevCommit> walkFirstParent(Repository repo, String branch) throws Exception {
    BiMap<String/*number*/, RevCommit> commitMap = HashBiMap.create();
    try (RevWalk walk = new RevWalk(repo)) {
      int count = -1;
      RevCommit head = walk.parseCommit(repo.resolve(branch));
      while (head != null) {
        ++count;
        commitMap.put(""+count, head);
        RevCommit[] parents = head.getParents();
        head = null;
        if (parents != null && parents.length > 0)
          head = walk.parseCommit(parents[0]);
      }
    }
    return reverse(commitMap);
  }

  private BiMap<String/*number*/, RevCommit> reverse(BiMap<String/*number*/, RevCommit> input) {
    BiMap<String/*number*/, RevCommit> output = HashBiMap.create();
    for (Map.Entry<String/*number*/, RevCommit> entry : input.entrySet())
      output.put("" + (input.size() - Integer.parseInt(entry.getKey())), entry.getValue());
    return output;
  }

  // lazy
  private Path archive() throws Exception {
    if (archivePath == null) {
      archivePath = Files.createTempDirectory("xbuild");
      try (PipedInputStream in = new PipedInputStream()) {
        try (PipedOutputStream out = new PipedOutputStream(in)) {
          new Thread(() -> {
            try {
              log("archive", archivePath);
              git()
                  //
                  .archive()
                  //
                  .setFormat("tar")
                  //
                  .setOutputStream(out)
                  //
                  .setTree(commit)
                  //
                  .call();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }).start();
          untar(in, archivePath);
        }
      }
    }
    return archivePath;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {

    try {

      // --git-dir
      gitDir = getGitDir(args);

      if (args.getOptionNames().contains("debug"))
        debug = true;

      if (args.getOptionNames().contains("silent"))
        verbose = false;
      if (args.getOptionNames().contains("verbose"))
        verbose = true;

      if (args.getOptionNames().contains("version")) {
        buildProperties.ifPresent((props) -> {
          System.err.println(String.format("xbuild[%s]", props.getVersion()));
        });
      }
  
      List<String> nonOptionArgs = Lists.newCopyOnWriteArrayList(args.getNonOptionArgs());

      // url?
      for (String arg : nonOptionArgs) {
        if (arg.contains(":")) {
          log("url", arg);
          setGit(arg);
          nonOptionArgs.remove(arg);
        }
      }

      // branch?
      String branch = git().getRepository().getBranch();
      for (String arg : nonOptionArgs) {
        Ref ref = git().getRepository().findRef(arg);
        if (ref != null) {
          log("branch", arg);
          branch = Repository.shortenRefName(ref.getName());
          nonOptionArgs.remove(arg);
        }
      }

      BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(git().getRepository(), branch);
      if (trackingStatus != null) {
        String remoteName = git().getRepository().getRemoteName(trackingStatus.getRemoteTrackingBranch());
        String remoteNameAndBranch = String.format("%s/%s", remoteName, branch);
        // "Your branch is ahead 'origin/master' by 1 commit."
        if (trackingStatus.getAheadCount()>0)
          log(String.format("### %s is ahead %s by %s commit(s)", branch, remoteNameAndBranch, trackingStatus.getAheadCount()));
        // "Your branch is behind 'origin/master' by 2 commits.""
        if (trackingStatus.getBehindCount()>0)
          log(String.format("### %s is behind %s by %s commit(s)", branch, remoteNameAndBranch, trackingStatus.getBehindCount()));
      }

      BiMap<String/*number*/, RevCommit> commitMap = walkFirstParent(git().getRepository(), branch);

      // number?
      for (String arg : nonOptionArgs) {
        if (commitMap.containsKey(arg)) {
          log("number", arg);
          number = arg;
          nonOptionArgs.remove(arg);
        }
      }

      // commit?
      for (String arg : nonOptionArgs) {
        if (Repository.isValidRefName(arg)) {
          ObjectId objectId = git().getRepository().resolve(arg);
          if (objectId != null) {
            log("commit[1]", arg);
            commit = git().getRepository().parseCommit(objectId);
            log("commit[2]", commit.name());
            nonOptionArgs.remove(arg);
          }
        }
      }

      // resolve number and commit

      if (number != null) // explicit number?
        commit = Objects.requireNonNull(commitMap.get(number), String.format("bad number: %s", number));
      else if (commit != null) // explicit commit?
        number = Objects.requireNonNull(commitMap.inverse().get(commit), String.format("bad branch/commit: %s/%s", branch, commit.name()));
      else {
        // latest number and commit
        number = latest(commitMap.keySet());
        commit = Objects.requireNonNull(commitMap.get(number), String.format("bad number: %s", number));
      }

      // scripts?
      for (String arg : nonOptionArgs) {
        File file = new File(archive().toFile(), arg);
        if (file.exists()) {
          if (file.isFile()) {
            log("script", arg);
            scripts.add(arg);
            nonOptionArgs.remove(arg);
          }
        }
      }

      if (nonOptionArgs.size()>0)
        throw new Exception("bad arg(s):"+nonOptionArgs.toString());

      String xbuild = String.format("%s-%s-%s", branch, number, commit.abbreviate(7).name());
      String commitTime = Instant.ofEpochSecond(commit.getCommitTime()).toString();

      Map<String, String> env = Maps.newTreeMap();
      env.put("XBUILD", xbuild); // "xbuild is running"
      env.put("XBUILD_BRANCH", branch);
      env.put("XBUILD_NUMBER", number);
      env.put("XBUILD_COMMIT", commit.name());
      env.put("XBUILD_COMMITTIME", commitTime);
      env.put("XBUILD_DATETIME", commitTime); // ###LEGACY###

      for (Map.Entry<String, String> entry : env.entrySet())
        System.out.println(String.format("export %s=\"%s\"", entry.getKey(), entry.getValue()));

      if (scripts.size() > 0) {
        // run xbuildfile
        if (new File(archive().toFile(), "xbuildfile").exists())
          Posix.run(archive(), env, ImmutableList.of("./xbuildfile"));
        else if (new File(archive().toFile(), ".xbuild").exists())
          Posix.run(archive(), env, ImmutableList.of("./.xbuild")); // legacy

        // run deploy scripts, e.g., xdeploy-dev
        for (String script : scripts)
          Posix.run(archive(), env, ImmutableList.of(String.format("./%s", script)));
      }
  
    } catch (Exception e) {
      System.err.println(e.toString());
      if (debug)
        e.printStackTrace();
      exit(1);
    }

  }

  private String latest(Set<String> numbers) {
    int number = 0;
    for (String s : numbers)
      number = Math.max(number, Integer.parseInt(s));
    return ""+number;
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

  private void exit(int exitCode) {
    System.exit(SpringApplication.exit(context, () -> exitCode));
  }

	private void log(Object... args) {
    if (verbose)
  		new LogHelper(this).log(args);
	}

}
