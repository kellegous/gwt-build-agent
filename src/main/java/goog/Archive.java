package goog;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

public class Archive {
  public static Archive create(String directory) {
    final File d = new File(directory);
    if (!d.exists())
      d.mkdirs();
    return new Archive(d);
  }

  private static boolean looksRevisiony(String name) {
    for (int i = 0, n = name.length(); i < n; ++i) {
      if (!Character.isDigit(name.charAt(i)))
        return false;
    }
    return true;
  }

  private static String nameFor(long revision) {
    final StringBuilder buffer = new StringBuilder(Long.toString(revision));
    while (buffer.length() < 6)
      buffer.insert(0, '0');
    return buffer.toString();
  }

  private final File m_directory;

  private TreeSet<Long> m_revisions;

  private Archive(File directory) {
    m_directory = directory;
  }

  public File directoryFor(long revision) {
    return new File(m_directory, nameFor(revision));
  }

  public void commit(long revision) throws IOException {
    new FileOutputStream(new File(directoryFor(revision), ".commit")).close();
    m_revisions.add(revision);
  }

  public SortedSet<Long> revisions() {
    if (m_revisions == null)
      m_revisions = load();
    return m_revisions;
  }

  private TreeSet<Long> load() {
    final File[] files = m_directory.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        final File file = new File(pathname, ".commit");
        return pathname.isDirectory() && looksRevisiony(pathname.getName()) && file.exists();
      }
    });
    final TreeSet<Long> result = new TreeSet<Long>();
    for (int i = 0, n = files.length; i < n; ++i)
      result.add(Long.parseLong(files[i].getName()));
    return result;
  }
}
