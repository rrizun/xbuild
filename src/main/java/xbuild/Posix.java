package xbuild;

import java.nio.file.attribute.*;
import java.util.*;

public class Posix {
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
}
