package goog;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

public class GwtSvn {
  private static final String URL = "http://google-web-toolkit.googlecode.com/svn";

  public static String urlFor(String path) {
    return URL + path;
  }

  public static SVNURL svnUrlFor(String path) throws SVNException {
    return SVNURL.parseURIDecoded(urlFor(path));
  }

  private GwtSvn() {
  }
}
