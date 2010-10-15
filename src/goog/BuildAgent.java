package goog;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class BuildAgent {
  private static final File WORKING = new File("working");

  private static List<Long> unhandledRevisions(List<SVNLogEntry> log, List<Long> fromArchive) {
    // TODO(knorton): Actually return the right list of revisions.
    final List<Long> revisions = new LinkedList<Long>();
    for (SVNLogEntry e : log)
      revisions.add(e.getRevision());
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

  public static void main(String[] args) throws SVNException, InterruptedException {
    DAVRepositoryFactory.setup();

    // We are only looking at trunk right now.
    final Branch branch = new Branch("trunk", "/trunk", 9022);
    final Archive archive = Archive.create("archive");

    // Checkout /tools.
    final WorkingCopy tools = new WorkingCopy(new File("working/tools"));
    tools.checkout("/tools");

    final Repo trunk = new Repo(branch);
    final WorkerBee.MessageQueue channel = letLoseTheBees(2);

    while (true) {
      tools.update();
      final List<Long> revisions = unhandledRevisions(trunk.newRevisions(), archive.revisions());
      System.out.println(revisions.size() + " unhandled revisions.");
      for (Long revision : revisions)
        channel.send(new WorkerBee.TaskMessage(branch, revision));

      Thread.sleep(10000);
    }
  }
}
