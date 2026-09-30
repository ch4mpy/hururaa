package pf.hururaa.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

class SessionEndListenerTest {

  /** Runs the task on the calling thread: what the listener hands over is what we assert on. */
  private static final TaskExecutor SYNCHRONOUS = Runnable::run;

  private final SseEmitterRegistry registry = mock(SseEmitterRegistry.class);

  private static HttpSessionEvent sessionEvent(String sessionId) {
    final var session = mock(HttpSession.class);
    when(session.getId()).thenReturn(sessionId);
    return new HttpSessionEvent(session);
  }

  @Test
  void whenSessionDestroyed_thenItsEventStreamsAreEnded() {
    new SessionEndListener(registry, SYNCHRONOUS).sessionDestroyed(sessionEvent("session-1"));

    verify(registry).endSession("session-1");
  }

  @Test
  void givenExecutorRejectsTheTask_whenSessionDestroyed_thenItDoesNotThrow() {
    final TaskExecutor rejecting = task -> {
      throw new RejectedExecutionException("shutting down");
    };

    new SessionEndListener(registry, rejecting).sessionDestroyed(sessionEvent("session-1"));
  }
}
