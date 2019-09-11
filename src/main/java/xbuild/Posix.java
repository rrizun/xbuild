package xbuild;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

import com.google.common.collect.*;

public class Posix {
  /**
   * run
   * 
   * @param cwd
   * @param env
   * @param command
   * @throws Exception
   */
  public static void run(Path cwd, Map<String, String> env, String... command) throws Exception {
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
   * returns the set of posix file permissions for the given posix mode
   * 
   * @param intMode
   * @return
   */
  public static Set<PosixFilePermission> perms(int intMode) {
    Set<PosixFilePermission> set = new TreeSet<>();
    for (int i = 0; i < PosixFilePermission.values().length; i++) {
      if ((intMode & 1) == 1)
        set.add(PosixFilePermission.values()[PosixFilePermission.values().length - i - 1]);
      intMode >>= 1;
    }
    return set;
  }
  
  static void log(Object... args) {
    new LogHelper(Posix.class).log(args);
  }

}
