package goog;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.util.ArrayList;
import java.util.List;

public class Repo {
  private final Branch m_branch;
  private final SVNClientManager m_manager = SVNClientManager.newInstance(SVNWCUtil.createDefaultOptions(false));

  private long lastRevision = 0;

  public Repo(Branch branch) throws SVNException {
    m_branch = branch;
    lastRevision = branch.startRevision();
  }
  
  public List<SVNLogEntry> newRevisions() throws SVNException {
    final List<SVNLogEntry> entries = new ArrayList<SVNLogEntry>();
    // TODO(knorton): Make limit-less instead of 1,000.
    m_manager.getLogClient().doLog(GwtSvn.svnUrlFor(m_branch.path()), new String[] {""}, SVNRevision.create(lastRevision), SVNRevision.create(lastRevision), SVNRevision.HEAD, true, false, 1000,
        new ISVNLogEntryHandler() {
          @Override
          public void handleLogEntry(SVNLogEntry e) throws SVNException {
            final long revision = e.getRevision();
            if (revision > lastRevision)
              lastRevision = revision;
            entries.add(e);
          }
        });
    return entries;
  }
}
