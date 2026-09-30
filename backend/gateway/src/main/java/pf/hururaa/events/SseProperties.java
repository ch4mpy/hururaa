package pf.hururaa.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Bounds for the server-sent event subscriptions the gateway keeps open (see
 * {@link SseEmitterRegistry}).
 *
 * <p>
 * An un-bounded stream registry is a denial-of-service surface: emitters are held in memory along
 * with an open Servlet async context each, and a browser that never completes its request is
 * indistinguishable from one that has vanished until the next write fails. These three values are
 * what keeps that growth finite.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@ConfigurationProperties(prefix = "sse")
@Data
public class SseProperties {

  /**
   * How long a stream is kept open before the gateway completes it. A browser {@code EventSource}
   * reconnects on its own when the response ends, so this recycles dead connections without any
   * heartbeat: whatever the network did in the meantime, nothing survives more than this.
   */
  private Duration timeout = Duration.ofMinutes(5);

  /**
   * Maximum number of concurrent streams for one HTTP session. Beyond it, the oldest stream of that
   * session is completed and dropped: a legitimate user has one per open tab, and recycling the
   * oldest keeps the most recent tabs working instead of failing the newcomer.
   */
  private int maxSubscriptionsPerSession = 8;

  /**
   * Maximum number of concurrent streams, all sessions taken together. Beyond it, new subscriptions
   * are refused: the gateway sheds load rather than exhausting the container's async capacity.
   */
  private int maxSubscriptions = 1000;
}
