package xbuild;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
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

import com.google.common.base.*;
import com.google.common.collect.*;

/**
 * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle
 */
@SpringBootApplication
public class Main implements ApplicationRunner {

	public static void main(String[] args) {
		SpringApplication.run(Main.class, args);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {

		log("run");

		// e.g., 20191231235959
    final String timestamp = CharMatcher.anyOf("0123456789").retainFrom(Instant.now().toString()).substring(0, 14);

		log("optionNames", args.getOptionNames());
		log("nonOptionArgs", args.getNonOptionArgs());

		Repository repository = new FileRepositoryBuilder()
				.setGitDir(new File(".git"))
//				 .readEnvironment() // scan environment GIT_* variables
//				 .findGitDir() // scan up the file system tree
				.build();

    try (Git git = new Git(repository)) {

      String branch = repository.getBranch();
      ObjectId objectId = repository.resolve(branch);

      Map<Integer, String> allTags = Maps.newTreeMap();
      Map<Integer, String> branchTags = Maps.newTreeMap();
      
//      repository.getTags()

      for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_TAGS)) {
        String tag = new File(ref.getName()).getName();
        log(tag);
        if (tag.contains("xbuild")) {
          int num = Integer.parseInt(Iterables.getLast(search("[0-9]+", tag)));
          allTags.put(num, tag);
        }
      }
      
      int buildNumber = 0;
      if (allTags.size()>0) {
        buildNumber = Iterables.getLast(allTags.keySet());

        String tag = Iterables.getLast(allTags.values());
        log("tag", tag);
        
        try (ObjectReader reader = repository.newObjectReader()) {
          CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
          oldTreeParser.reset(reader, repository.resolve(tag+"^{tree}"));
          CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
          newTreeParser.reset(reader, repository.resolve(branch+"^{tree}"));

          if (git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).call().size()==0) {
            throw new RuntimeException("zero diff");
          }
        }
        
        
        
      }

      int buildNumberNext = ++buildNumber;

      log("buildNumberNext", buildNumberNext);
      

      // e.g., xbuild-master-20191231235959-234
      String newTag = String.format("xbuild-%s-%s-%s", branch, timestamp, buildNumberNext);
      
      log("newTag", newTag);
      
      String commit = objectId.abbreviate(7).name();

      Map<String, String> env = Maps.newHashMap();
      env.put("XBUILD_BRANCH", branch);
      env.put("XBUILD_COMMIT", commit);
      env.put("XBUILD_NUMBER", ""+buildNumberNext);
      env.put("XBUILD_TIMESTAMP", timestamp);
      
      log(env);

      ArchiveFormats.registerAll();
      
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

      run(tmpDir, env, "./xbuildfile");

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
