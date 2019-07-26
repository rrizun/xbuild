package xbuild;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.archive.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
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

		log("run", args);

		// e.g., 20191231235959
		final String timestamp = CharMatcher.anyOf("0123456789").retainFrom(Instant.now().toString()).substring(0,14);

		log("optionNames", args.getOptionNames());
		log("nonOptionArgs", args.getNonOptionArgs());

		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder
				.setGitDir(new File(".git"))
				// .readEnvironment() // scan environment GIT_* variables
				// .findGitDir() // scan up the file system tree
				.build();

		String branch = repository.getBranch();

		Map<Integer, String> allTags = Maps.newTreeMap();
		Map<Integer, String> branchTags = Maps.newTreeMap();

		for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_TAGS)) {
			String tag = new File(ref.getName()).getName();
			log(tag);

			if (tag.contains("xbuild")) {

				int num = Integer.parseInt(Iterables.getLast(findall("[0-9]+", tag)));
				allTags.put(num, tag);

			}

			
		}

		int buildNumber = 0;
		if (allTags.size()>0)
			buildNumber = Iterables.getLast(allTags.keySet());

		int buildNumberNext = ++buildNumber;

		log("buildNumberNext", buildNumberNext);

		String newTag = String.format("xbuild-%s-%s-%s", branch, buildNumberNext, timestamp);
		log("newTag", newTag);

		Map<String, String> env = Maps.newHashMap();
		env.put("XBUILD_BRANCH", branch);
		env.put("XBUILD_NUMBER", ""+buildNumberNext);
		env.put("XBUILD_TIMESTAMP", timestamp);

		Path tmp = Files.createTempDirectory("xbuild");

		log(tmp);

		Path tempFile = Files.createTempFile("xbuild", ".zip");
		log(tempFile);
		
		final OutputStream out = Files.newOutputStream(tempFile);
		
		ArchiveFormats.registerAll();
		
//    ArchiveCommand.registerFormat("zip", null);
    
    try (Git git = new Git(repository)) {
      
      git.archive()
      .setFormat("zip")
      .setOutputStream(out)
      .setTree(repository.resolve("master"))
      .call();

//  try {
//         git.archive()
//                 .setTree(db.resolve("HEAD"))
//                 .setOutputStream(out)
//                 .call();
//  } finally {
//         ArchiveCommand.unregisterFormat("tar");
//  }
 
    }
    
		run(env, "./xbuildfile");

		

	}

//	private void archive() throws Exception {
//		java.util.zip.ZipFile zipFile = new ZipFile(file);
//		try {
//			Enumeration<? extends ZipEntry> entries = zipFile.entries();
//			while (entries.hasMoreElements()) {
//				ZipEntry entry = entries.nextElement();
//				File entryDestination = new File(outputDir, entry.getName());
//				if (entry.isDirectory()) {
//					entryDestination.mkdirs();
//				} else {
//					entryDestination.getParentFile().mkdirs();
//					InputStream in = zipFile.getInputStream(entry);
//					OutputStream out = new FileOutputStream(entryDestination);
//					IOUtils.copy(in, out);
//					IOUtils.closeQuietly(in);
//					out.close();
//				}
//			}
//		} finally {
//			zipFile.close();
//		}
//	}

	private void run(Map<String, String> env, String... command) throws Exception {
		log("----------------------------------------------------------------------");
		log("run", Lists.newArrayList(command));
		log("----------------------------------------------------------------------");

		ProcessBuilder builder = new ProcessBuilder(command);
			builder.environment().putAll(env);
			builder.redirectError(ProcessBuilder.Redirect.INHERIT);
			builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		if (builder.start().waitFor() != 0)
			throw new Exception();
	}

  private List<String> findall(String regex, String s) {
    List<String> list = new ArrayList<>();
    Matcher m = Pattern.compile(regex).matcher(s);
    while (m.find())
      list.add(m.group(0));
    return list;
  }

	private void log(Object... args) {
		new LogHelper(this).log(args);
	}

}
