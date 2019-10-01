package xbuild;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

import org.apache.commons.compress.archivers.tar.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.archive.*;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
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

/**
 * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle
 */
@SpringBootApplication
public class MainTwo implements ApplicationRunner {

	public static void main(String[] args) throws Exception {
    SpringApplication.run(MainTwo.class, args);
  }
	
  static {
    ArchiveFormats.registerAll();
  }

  // public MainTwo(ApplicationContext context) {
  //   BuildProperties buildProperties = context.getBeanProvider(BuildProperties.class).getIfAvailable();
  //   if (buildProperties != null) {
  //     for (BuildProperties.Entry entry : buildProperties)
  //       log(entry.getKey(), entry.getValue());
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

    // try to infer git url
    for (String arg : args.getNonOptionArgs()) {
      if (arg.contains("@")) {
        File directory = Files.createTempDirectory("xbuild").toFile();
        log(directory);
        CloneCommand cloneCommand = Git.cloneRepository()
            .setBare(true)
            .setDirectory(directory)
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
        .build();
    return new Git(repository);
  }

  @Override
	public void run(ApplicationArguments args) throws Exception {
    try (Git git = createGit(args)) {
      doit(args, git);
    }
  }
  
  private void doit(ApplicationArguments args, Git git) throws Exception {
    int count = 0;
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

    String revision = String.format("%s/%s", remote, branch);

    List<RevCommit> commitList = Lists.newArrayList();
    try (RevWalk walk = new RevWalk(repo)) {
      RevCommit head = walk.parseCommit(repo.findRef("HEAD").getObjectId());
      while (head != null) {
        count++;
        commitList.add(head);
        RevCommit[] parents = head.getParents();
        if (parents != null && parents.length > 0) {
          head = walk.parseCommit(parents[0]);
        } else {
          head = null;
        }
      }
    }

    Map<Integer, RevCommit> commits = Maps.newTreeMap();
    for (RevCommit commit : commitList) {

      commits.put(count--, commit);

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
      int number = Iterables.getLast(commits.keySet());
      RevCommit commit = Iterables.getLast(commits.values());

    // % xbuild number ?
    for (String arg : args.getNonOptionArgs()) {
      if (arg.matches("[0-9]+")) {
        number = Integer.parseInt(arg);
        commit = Objects.requireNonNull(commits.get(number), "bad commit number");
      }
    }

    log("number", number);
    log("commit", commit);
    

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

	static void log(Object... args) {
		new LogHelper(Main.class).log(args);
	}

}
