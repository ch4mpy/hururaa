package pf.hururaa.events;

import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.extern.slf4j.Slf4j;

/**
 * Notifies the browsers of a session that it is over, whatever ended it: expiration after the
 * configured idle time, RP-initiated logout, or a back-channel logout sent by the authorization
 * server when the user logs out of another application. All three end up destroying the HTTP
 * session, which is what this listener hooks onto. Spring Boot registers beans implementing
 * {@link HttpSessionListener} with the servlet container by itself.
 *
 * <p>
 * The work is handed over to a {@link TaskExecutor} on purpose: on the expiration path, this
 * listener runs on Tomcat's background thread, inside a block synchronized on the session, while
 * that same thread sweeps every session of the context. Writing to a socket from there would delay
 * the expiration of all the other sessions. The registry keeps the emitters, which stay usable
 * once this method has returned.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
@Slf4j
public class SessionEndListener implements HttpSessionListener {

  private final SseEmitterRegistry registry;
  private final TaskExecutor executor;

  public SessionEndListener(
      SseEmitterRegistry registry,
      @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) TaskExecutor executor) {
    this.registry = registry;
    this.executor = executor;
  }

  @Override
  public void sessionDestroyed(HttpSessionEvent event) {
    // the session id is the value of the session cookie: never log it, never send it over the wire
    final var sessionId = event.getSession().getId();
    try {
      executor.execute(() -> registry.endSession(sessionId));
    } catch (RejectedExecutionException e) {
      // the application is most likely shutting down, in which case the streams die with it
      log.debug("Could not notify the event streams of a destroyed session", e);
    }
  }
}
