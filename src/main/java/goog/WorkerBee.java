package goog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

  public static class QuitRequest implements Message {
  }

  public static class QuitResponse implements Message {
  }

  public static class TaskResponse implements Message {
    private final TaskRequest m_request;
    private final boolean m_success;

    private TaskResponse(TaskRequest request, boolean success) {
      m_request = request;
      m_success = success;
    }

    public TaskRequest request() {
      return m_request;
    }

    public boolean success() {
      return m_success;
    }
  }

  public static class TaskRequest implements Message {
    private final Branch m_branch;
    private final long m_revision;

    public TaskRequest(Branch branch, long revision) {
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

  private void drain(final InputStream stream) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
          final byte[] data = new byte[1024];
          int n = 0;
          while ((n = stream.read(data)) >= 0)
            buffer.write(data, 0, n);
          System.out.println(new String(buffer.toByteArray()));
        } catch (IOException e) {
          // Ignore these because we're only draining.
        }
      }
    }).start();
  }

  private boolean build() throws IOException, InterruptedException {
    // TODO(knorton): Where is ant? Assume it is on the PATH.
    final Process process = new ProcessBuilder("ant", "dist").directory(m_workingCopy.directory()).redirectErrorStream(true).start();

    // TODO(knorton): We will completely disreguard stdin/stdout at this point,
    // but eventually write it into the artifacts directory.
    drain(process.getInputStream());

    return process.waitFor() == 0;
  }

  private void handleTaskRequest(TaskRequest request) {
    final Branch branch = request.branch();
    final long revision = request.revision();
    try {
      System.out.println("Starting task " + branch.name() + " @r" + revision);
      // Update the working copy.
      m_workingCopy.checkout(branch.path(), revision);

      // Run ant dist.
      final boolean success = build();

      // Move results into archive directory.

      // Send message back to agent.
      System.out.println("Finishing task " + branch.name() + " @r" + revision);
      m_channel.send(new TaskResponse(request, success));
    } catch (Exception e) {
      // TODO(knorton): Send error to agent. The weird thing about these is that
      // they many not indicate that the build is busted, so we need to make
      // this kind of a special condition.
      e.printStackTrace();
      m_channel.send(new TaskResponse(request, false));
    }
  }

  @Override
  public void run() {
    try {
      while (true) {
        final Message message = m_channel.receive();
        if (message instanceof QuitRequest) {
          m_channel.send(new QuitResponse());
          return;
        } else if (message instanceof TaskRequest) {
          handleTaskRequest((TaskRequest)message);
        }
      }
    } catch (InterruptedException e) {
    }
  }
}
