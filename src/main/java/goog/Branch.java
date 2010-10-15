package goog;

public class Branch {
  private final String m_path;
  private final String m_name;
  private final long m_startRevision;

  public Branch(String name, String path, long startRevision) {
    m_name = name;
    m_path = path;
    m_startRevision = startRevision;
  }

  public String path() {
    return m_path;
  }

  public String name() {
    return m_name;
  }

  public long startRevision() {
    return m_startRevision;
  }
}
