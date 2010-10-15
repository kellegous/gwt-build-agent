package goog;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;

public class WorkingCopy {
  private final File m_directory;
  private final SVNClientManager m_manager = SVNClientManager.newInstance(SVNWCUtil.createDefaultOptions(false));

  public WorkingCopy(File directory) {
    m_directory = directory;
  }

  public void checkout(String path) throws SVNException {
    doCheckout(path, SVNRevision.HEAD);
  }

  public void checkout(String path, long revision) throws SVNException {
    doCheckout(path, SVNRevision.create(revision));
  }

  private void doCheckout(String path, SVNRevision revision) throws SVNException {
    final SVNURL url = GwtSvn.svnUrlFor(path);

    if (!m_directory.exists())
      m_directory.mkdirs();

    if (!new File(m_directory, ".svn").exists()) {
      m_manager.getUpdateClient().doCheckout(url, m_directory, revision, revision, SVNDepth.UNKNOWN, true);
      return;
    }

    final SVNInfo info = m_manager.getWCClient().doInfo(m_directory, SVNRevision.WORKING);
    if (info.getURL().equals(url)) {
      m_manager.getUpdateClient().doUpdate(m_directory, revision, SVNDepth.UNKNOWN, false, false);
      return;
    }

    m_manager.getUpdateClient().doSwitch(m_directory, url, revision, revision, SVNDepth.UNKNOWN, false, false);
  }

  public void update(SVNRevision revision) throws SVNException {
    m_manager.getUpdateClient().doUpdate(m_directory, revision, SVNDepth.UNKNOWN, false, false);
  }

  public void update() throws SVNException {
    update(SVNRevision.HEAD);
  }
}
