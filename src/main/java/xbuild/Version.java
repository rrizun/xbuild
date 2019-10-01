package xbuild;

import java.math.*;
import java.util.*;
import java.util.regex.*;

import com.google.common.collect.*;

// https://semver.org
public class Version {
  
  private final List<String> list = Lists.newArrayList();
//  private final int[] semver; // major|minor|patch
  
  private int semver(int semver) {
    int index = -1;
    for (String s : list) {
      ++index;
      if (s.matches("[0-9]+")) {
        if (--semver==-1)
          return index;
      }
    }
    return -1;
  }

  public Version(String version) {
    Matcher m = p.matcher(version);
    while (m.find())
      list.add(m.group(0));
  }
  
  private Version(List<String> list) {
    this.list.addAll(list);
//    int group = -1;
//    int index = -1;
//    for (String s : list) {
//      ++group;
//      if (s.matches("[0-9]+"))
//        semver[++index] = group;
//    }
  }

//  public Version(String major, String minor, String patch) {
//    this.major = major;
//    this.minor = minor;
//    this.patch = patch;
//    this.pre = null;
//    this.build = null;
//  }

  public String render() {
    return String.join("", list);
//    String result = String.format("%s.%s.%s", major, minor, patch);
//    if (pre!=null) {
//      result = String.format("%s-%s", result, pre);
//      if (build!=null)
//        result = String.format("%s+%s", result, build);
//    }
//    return result;
  }

  public Version incrementMajor() {
    list.set(semver(0), increment(list.get(semver(0)))); // major
    list.set(semver(1), "0"); // minor
    list.set(semver(2), "0"); // patch
    return this;
  }
  
  public Version incrementMinor() {
    list.set(semver(1), increment(list.get(semver(1)))); // minor
    list.set(semver(2), "0"); // patch
    return this;
  }

  public Version incrementPatch() {
    list.set(semver(2), increment(list.get(semver(2)))); // patch
    return this;
  }

  public Version release(String release) {
    list.set(semver(3), release);
    return this;
  }

  public Version build(String build) {
    return this;
  }
  
  private String increment(String input) {
    return new Integer(new Integer(input) + 1).toString();
  }

  private static Pattern p = Pattern.compile("[0-9]+|[^0-9]+");
//  // https://semver.org
//  static final Pattern p = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?");

  public static void main(String... args) {
    System.out.println(new Version("0.9.1").incrementMajor().build("master").render());
  }
  
}