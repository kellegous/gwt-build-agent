package goog;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import goog.WorkerBee.TaskResponse;

public class BuildAgent {
  private static final File WORKING = new File("working");
  private static final int MAX_WORKERS = 12;

  private Set<Long> revisionsInProgress() {
    final HashSet<Long> revisions = new HashSet<Long>();
    for (WorkerBee.TaskRequest req : m_inProgress)
      revisions.add(req.revision());
    return revisions;
  }

  private static List<Long> revisionsToBuild(List<SVNLogEntry> log, Set<Long> inArchive, Set<Long> inProgress) {
    final List<Long> revisions = new LinkedList<Long>();
    for (int i = log.size() - 1; i >= 0; --i) {
      final long rev = log.get(i).getRevision();
      if (!inArchive.contains(rev) && !inProgress.contains(rev))
        revisions.add(rev);
    }
    return revisions;
  }

  private void handleMessage(WorkerBee.Message message) throws IOException {
    if (message instanceof WorkerBee.TaskResponse) {
      completeTask((TaskResponse)message);
      return;
    }

    if (message instanceof WorkerBee.QuitResponse) {
      m_numBees--;
      return;
    }

    if (message instanceof WorkerBee.TaskException) {
      final WorkerBee.TaskException ex = (WorkerBee.TaskException)message;
      m_inProgress.remove(ex.request());
      System.err.println("Exception on r" + ex.request().revision());
      ex.exception().printStackTrace(System.err);
      return;
    }
  }

  private void completeTask(WorkerBee.TaskResponse response) throws IOException {
    final WorkerBee.TaskRequest request = response.request();
    m_inProgress.remove(request);
    m_archive.commit(response.request().revision());
  }

  private void startTask(WorkerBee.TaskRequest request) {
    m_inProgress.add(request);
    if (m_numBees == 0 || (m_channel.backlog() > 0 && m_numBees < MAX_WORKERS))
      new Thread(new WorkerBee(new File(WORKING, "bee-" + (m_numBees++)), m_channel)).start();
    m_channel.send(request);
  }

  private void run() throws SVNException, InterruptedException, IOException {
    // TODO(knorton): Multi-branch support.

    // We are only looking at trunk right now.
    final Branch branch = new Branch("trunk", "/trunk", 6000);

    // Setup a working copy of /tools.
    final WorkingCopy tools = new WorkingCopy(new File("working/tools"));

    final Repo trunk = new Repo(branch);

    while (true) {
      tools.checkout("/tools");
      final List<Long> revisions = revisionsToBuild(trunk.newRevisions(), m_archive.revisions(), revisionsInProgress());
      System.out.println(revisions.size() + " unhandled revisions.");
      for (Long revision : revisions)
        startTask(new WorkerBee.TaskRequest(branch, revision, m_archive.directoryFor(revision)));
      handleMessage(m_channel.receive());
    }
  }

  public static void main(String[] args) throws SVNException, InterruptedException, IOException {
    DAVRepositoryFactory.setup();
    new BuildAgent().run();
  }

  private final Archive m_archive = Archive.create("archive/trunk");

  private final HashSet<WorkerBee.TaskRequest> m_inProgress = new HashSet<WorkerBee.TaskRequest>();

  private final WorkerBee.Channel m_channel = new WorkerBee.Channel();

  private int m_numBees;

  private BuildAgent() {
  }
}
