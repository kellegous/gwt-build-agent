package goog;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

public class Archive {
  private final File m_directory;

  private List<Long> m_revisions;

  public static Archive create(String directory) {
    final File d = new File(directory);
    if (!d.exists())
      d.mkdirs();
    return new Archive(d);
  }

  private Archive(File directory) {
    m_directory = directory;
  }

  private static boolean looksRevisiony(String name) {
    for (int i = 0, n = name.length(); i < n; ++i) {
      if (!Character.isDigit(name.charAt(i)))
        return false;
    }
    return true;
  }

  private List<Long> load() {
    final File[] files = m_directory.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return pathname.isDirectory() && looksRevisiony(pathname.getName());
      }
    });
    final List<Long> result = new ArrayList<Long>();
    for (int i = 0, n = files.length; i < n; ++i)
      result.add(Long.parseLong(files[i].getName()));
    return result;
  }

  public List<Long> revisions() {
    if (m_revisions == null)
      m_revisions = load();
    return m_revisions;
  }
}
