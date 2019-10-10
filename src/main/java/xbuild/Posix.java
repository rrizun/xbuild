package xbuild;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

public class Posix {
  /**
   * run
   * 
   * @param cwd
   * @param env
   * @param command
   * @throws Exception
   */
  public static void run(Path cwd, Map<String, String> env, List<String> command) throws Exception {
    log("----------------------------------------------------------------------");
    log("run", command);
    log("----------------------------------------------------------------------");

    final ProcessBuilder builder = new ProcessBuilder(command);
    
    builder.directory(cwd.toFile());
    builder.environment().putAll(env);
    builder.inheritIO();

    if (builder.start().waitFor() != 0)
      throw new Exception("" + cwd + env + command);
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
