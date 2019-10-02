package xbuild;

import java.util.*;

import com.google.common.base.*;
import com.google.common.collect.*;

/**
 * LogHelper
 */
public class LogHelper {
  private final Object object;

  public LogHelper(Object object) {
    this.object = object;
  }

  public void log(Object... args) {
    List<Object> parts = Lists.newArrayList();
//    parts.add(new Date());
//    parts.add(object);
    for (Object arg : args)
      parts.add(arg);
    System.err.println(Joiner.on(" ").useForNull("null").join(parts));
  }
}