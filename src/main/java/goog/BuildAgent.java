package goog;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import goog.WorkerBee.TaskResponse;

public class BuildAgent {
  private static final File WORKING = new File("working");

  private Set<Long> revisionsInProgress() {
    final HashSet<Long> revisions = new HashSet<Long>();
    for (WorkerBee.TaskRequest req : m_inProgress)
      revisions.add(req.revision());
    return revisions;
  }

  private static List<Long> revisionsToBuild(List<SVNLogEntry> log, Set<Long> inArchive, Set<Long> inProgress) {
    final List<Long> revisions = new LinkedList<Long>();
    for (int i = log.size() - 1; i <= 0; --i) {
      final long rev = log.get(i).getRevision();
      if (!inArchive.contains(rev) && !inProgress.contains(rev))
        revisions.add(rev);
    }
    return revisions;
  }

  private static WorkerBee.MessageQueue letLoseTheBees(int n) {
    final WorkerBee.Channel channel = new WorkerBee.Channel();
    for (int i = 0; i < n; ++i) {
      final File directory = new File(WORKING, "bee-" + i);
      new Thread(new WorkerBee(directory, channel)).start();
    }
    return channel;
  }

  private boolean handleMessage(WorkerBee.Message message) {
    if (message instanceof WorkerBee.TaskResponse) {
      final WorkerBee.TaskResponse response = (TaskResponse)message;
      System.out.println("WorkerBee has completed task " + response.request().branch().path() + " @r" + response.request().revision());
      m_inProgress.remove(response.request());
      return false;
    }

    return true;
  }

  private void run() throws SVNException, InterruptedException {
    // TODO(knorton): Multi-branch support will require an archive for each
    // branch.

    // We are only looking at trunk right now.
    final Branch branch = new Branch("trunk", "/trunk", 9022);
    final Archive archive = Archive.create("archive/trunk");

    // Setup a working copy of /tools.
    final WorkingCopy tools = new WorkingCopy(new File("working/tools"));

    final Repo trunk = new Repo(branch);
    final WorkerBee.MessageQueue channel = letLoseTheBees(1);

    while (true) {
      tools.checkout("/tools");
      final List<Long> revisions = revisionsToBuild(trunk.newRevisions(), archive.revisions(), revisionsInProgress());
      System.out.println(revisions.size() + " unhandled revisions.");
      for (Long revision : revisions) {
        final WorkerBee.TaskRequest request = new WorkerBee.TaskRequest(branch, revision, archive.directoryFor(revision));
        m_inProgress.add(request);
        channel.send(request);
      }

      if (!handleMessage(channel.receive()))
        return;
    }
  }

  public static void main(String[] args) throws SVNException, InterruptedException {
    DAVRepositoryFactory.setup();
    new BuildAgent().run();
  }

  private final HashSet<WorkerBee.TaskRequest> m_inProgress = new HashSet<WorkerBee.TaskRequest>();

  private BuildAgent() {

  }
}
