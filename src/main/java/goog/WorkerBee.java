package goog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class WorkerBee implements Runnable {
  public interface MessageQueue {
    Message receive() throws InterruptedException;

    void send(Message message);
  }

  public static class Channel implements MessageQueue {
    private final LinkedBlockingQueue<Message> m_toBees = new LinkedBlockingQueue<WorkerBee.Message>();
    private final LinkedBlockingQueue<Message> m_frBees = new LinkedBlockingQueue<WorkerBee.Message>();

    public int backlog() {
      return m_toBees.size();
    }

    @Override
    public Message receive() throws InterruptedException {
      return m_frBees.take();
    }

    public Message receive(int timeout) throws InterruptedException {
      return m_frBees.poll(timeout, TimeUnit.MILLISECONDS);
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

  public static class TaskException implements Message {
    private final TaskRequest m_request;
    private final Throwable m_exception;

    public TaskException(TaskRequest request, Throwable exception) {
      m_request = request;
      m_exception = exception;
    }

    public Throwable exception() {
      return m_exception;
    }

    public TaskRequest request() {
      return m_request;
    }
  }

  public static class TaskRequest implements Message {
    private final Branch m_branch;
    private final long m_revision;
    private final File m_destination;

    public TaskRequest(Branch branch, long revision, File destination) {
      m_branch = branch;
      m_revision = revision;
      m_destination = destination;
    }

    public Branch branch() {
      return m_branch;
    }

    public long revision() {
      return m_revision;
    }

    public File destination() {
      return m_destination;
    }
  }

  private final WorkingCopy m_workingCopy;
  private final MessageQueue m_channel;

  public WorkerBee(File directory, Channel channel) {
    m_channel = channel.channel();
    m_workingCopy = new WorkingCopy(directory);
  }

  private void streamTo(final InputStream stream, File file) throws FileNotFoundException {
    final FileOutputStream out = new FileOutputStream(file);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          final byte[] data = new byte[1024];
          int n = 0;
          while ((n = stream.read(data)) >= 0)
            out.write(data, 0, n);
        } catch (IOException e) {
        } finally {
          try {
            out.close();
          } catch (IOException e) {
          }
        }
      }
    }).start();
  }

  private static void copyDirectory(File sourceLocation, File targetLocation) throws IOException {
    if (sourceLocation.isDirectory()) {
      if (!targetLocation.exists()) {
        targetLocation.mkdir();
      }

      String[] children = sourceLocation.list();
      for (int i = 0; i < children.length; i++) {
        copyDirectory(new File(sourceLocation, children[i]), new File(targetLocation, children[i]));
      }
    } else {
      InputStream in = new FileInputStream(sourceLocation);
      OutputStream out = new FileOutputStream(targetLocation);

      // Copy the bits from instream to outstream
      byte[] buf = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }
      in.close();
      out.close();
    }
  }

  private boolean build(File destination) throws IOException, InterruptedException {
    // TODO(knorton): Where is ant? Assume it is on the PATH.
    final Process process = new ProcessBuilder("ant", "clean", "dist").directory(m_workingCopy.directory()).start();
    streamTo(process.getInputStream(), new File(destination, "stdout.txt"));
    streamTo(process.getErrorStream(), new File(destination, "stderr.txt"));
    final boolean success = process.waitFor() == 0;
    if (success)
      copyDirectory(new File(m_workingCopy.directory(), "build/dist"), destination);
    return success;
  }

  private void handleTaskRequest(TaskRequest request) {
    final Branch branch = request.branch();
    final long revision = request.revision();
    final File destination = request.destination();
    try {
      // Update the working copy.
      m_workingCopy.checkout(branch.path(), revision);

      if (!destination.exists())
        destination.mkdirs();

      // Run ant dist.
      if (!build(destination)) {
        m_channel.send(new TaskResponse(request, false));
        return;
      }

      // Send message back to agent.
      m_channel.send(new TaskResponse(request, true));
    } catch (Exception e) {
      // TODO(knorton): Send error to agent. The weird thing about these is that
      // they many not indicate that the build is busted, so we need to make
      // this kind of a special condition.
      m_channel.send(new TaskException(request, e));
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
