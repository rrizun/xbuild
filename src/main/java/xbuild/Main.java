package xbuild;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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

		log("optionNames", args.getOptionNames());
		log("nonOptionArgs", args.getNonOptionArgs());

		FileRepositoryBuilder builder = new FileRepositoryBuilder();
Repository repository = builder.setGitDir(new File("."))
  .readEnvironment() // scan environment GIT_* variables
  .findGitDir() // scan up the file system tree
	.build();
	
	log(repository.getBranch());

		run("./xbuildfile");
	}

	private void run(String... args) throws Exception {
		log("----------------------------------------------------------------------");
		log("run", Lists.newArrayList(args));
		log("----------------------------------------------------------------------");

		final Process p = new ProcessBuilder(args)
			.redirectError(Redirect.INHERIT)
			.redirectOutput(Redirect.INHERIT)
			.start();

		if (p.waitFor() != 0)
			throw new Exception();
	}

	private void log(Object... args) {
		new LogHelper(this).log(args);
	}

}
