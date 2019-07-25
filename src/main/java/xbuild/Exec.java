package xbuild;

import java.io.*;
import java.lang.ProcessBuilder.Redirect;
import java.util.*;

import com.google.common.io.*;

// exec
public class Exec {
  // return stdout, throw stderr
  public static ByteSource asByteSourceOut(final List<String> args) {
    return asByteSource(args, 1);
  }
  // return stderr, throw stdout
  public static ByteSource asByteSourceErr(final List<String> args) {
    return asByteSource(args, 2);
  }
  private static ByteSource asByteSource(final List<String> args, final int fd) {
    return new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
          //###TODO redirectOutput vs redirectError wrt fd
          final Process p = new ProcessBuilder(args).redirectError(Redirect.INHERIT).start();
          return new FilterInputStream(fd == 1 ? p.getInputStream() : p.getErrorStream()) {
            public void close() throws IOException {
              try {
                if (p.waitFor() != 0)
                  throw new IOException(""+p.exitValue());
              } catch (InterruptedException ie) {
                throw new IOException(ie);
              }
          }
          };
      }
    };
  }
}