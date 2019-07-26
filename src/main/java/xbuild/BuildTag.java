package xbuild;

import java.util.*;
import java.util.regex.*;

public class BuildTag {
  
  public final String branch;
  public final String number;
  
  public BuildTag(String branch, int number) {
    this.branch = branch;
    this.number = ""+number;
  }

//  public static BuildTag parseTag(String tag) {
//    
//    Pattern p = Pattern.compile("build-\\w-\\d");
//    
//    
//  }
  
  public String renderTag() {
    return String.format("xbuild-%s-%s", branch, number);
  }

  private List<String> search(String regex, String s) {
    List<String> list = new ArrayList<>();
    Matcher m = Pattern.compile(regex).matcher(s);
    while (m.find())
      list.add(m.group(0));
    return list;
  }

}
