package xbuild;

import java.math.*;
import java.util.*;
import java.util.regex.*;

import com.google.common.collect.*;

public class HumanComparator implements Comparator<String> {

  @Override
  public int compare(String lhs, String rhs) {
    return Comparators.lexicographical(new Comparator<Object>() {
      @Override
      public int compare(Object lhs, Object rhs) {
        if (lhs instanceof String) {
          if (rhs instanceof String)
            return ((String) lhs).compareTo((String) rhs);
          return 1;
        }
        if (lhs instanceof BigInteger) {
          if (rhs instanceof BigInteger)
            return ((BigInteger) lhs).compareTo((BigInteger) rhs);
          return -1;
        }
        throw new RuntimeException();
      }
    }).compare(list(lhs), list(rhs));
  }

  private List<Object> list(String input) {
    List<Object> list = Lists.newArrayList();
    Matcher m = p.matcher(input);
    while (m.find()) {
      String atom = m.group(0);
      if (atom.matches("[0-9]+"))
        list.add(new BigInteger(atom));
      else
        list.add(atom);
    }
    return list;
  }

  private final Pattern p = Pattern.compile("[0-9]+|[^0-9]+");

}
