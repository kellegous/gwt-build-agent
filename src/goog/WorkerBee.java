package goog;

import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;

public class WorkerBee implements Runnable {
  public interface MessageQueue {
    Message receive() throws InterruptedException;

    void send(Message message);
  }

  public static class Channel implements MessageQueue {
    private final LinkedBlockingQueue<Message> m_toBees = new LinkedBlockingQueue<WorkerBee.Message>();
    private final LinkedBlockingQueue<Message> m_frBees = new LinkedBlockingQueue<WorkerBee.Message>();

    @Override
    public Message receive() throws InterruptedException {
      return m_frBees.take();
    }

    @Override
    public void send(Message message) {
      m_toBees.offer(message);
    }

    private MessageQueue channel() {
      return new MessageQueue() {

        @Override
        public Message receive() throws InterruptedException {
          return m_toBees.take();
        }

        @Override
        public void send(Message message) {
          m_frBees.offer(message);
        }
      };
    }
  }

  public interface Message {
  }

  public static class QuitMessage implements Message {
  }

  public static class TaskMessage implements Message {
    private final Branch m_branch;
    private final long m_revision;

    public TaskMessage(Branch branch, long revision) {
      m_branch = branch;
      m_revision = revision;
    }

    public Branch branch() {
      return m_branch;
    }

    public long revision() {
      return m_revision;
    }
  }

  private final WorkingCopy m_workingCopy;
  private final MessageQueue m_channel;

  public WorkerBee(File directory, Channel channel) {
    m_channel = channel.channel();
    m_workingCopy = new WorkingCopy(directory);
  }

  private void doTask(Branch branch, long revision) {
    try {
      System.out.println("Starting task " + branch.name() + " @r" + revision);
      // Update the working copy.
      m_workingCopy.checkout(branch.path(), revision);

      // Run ant dist.

      // Move results into archive directory.

      // Send message back to agent.
      System.out.println("Finishing task " + branch.name() + " @r" + revision);
    } catch (SVNException e) {
      // TODO(knorton): Send error to agent.
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    try {
      while (true) {
        final Message message = m_channel.receive();
        if (message instanceof QuitMessage) {
          System.out.println("Quitting...");
          return;
        } else if (message instanceof TaskMessage) {
          final TaskMessage task = (TaskMessage)message;
          doTask(task.branch(), task.revision());
        }
      }
    } catch (InterruptedException e) {
    }
  }
}
