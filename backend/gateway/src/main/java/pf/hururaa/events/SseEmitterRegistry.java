package pf.hururaa.events;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pf.hururaa.commons.events.ResourceEvent;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps track of the {@link SseEmitter}s opened by the connected browsers, each subscribed for one
 * tenant with the subject and the permissions the user holds on that tenant, so that a
 * {@link ResourceEvent} can be relayed to whoever owns the resource or holds one of its audience
 * permissions, without the gateway needing to know anything about what the resource actually is.
 *
 * <p>
 * Events are never relayed across tenants: only the subscriptions for the event's tenant are
 * considered. The permissions are those held when subscribing: a browser must re-subscribe (which
 * it does when the session is renewed) for a grant or revocation to take effect.
 * </p>
 *
 * <p>
 * Subscriptions also remember the HTTP session they were opened from, so that
 * {@link #endSession(String)} can tell the browsers of a session that is over, and only those, that
 * they are not authenticated anymore (see {@link SessionEndListener}).
 * </p>
 *
 * <p>
 * In-memory only, one list per tenant: an event is matched against the subscriptions of its
 * tenant alone. A single gateway instance is a decision (docs/decisions/0002): with several, each
 * would only know its own browsers.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseEmitterRegistry {

  private final SseProperties properties;

  /**
   * The {@link ResourceEvent#resourceType()} of the events this gateway emits by itself when a
   * session ends (the ones relayed from the business services are about business resources). The
   * resource is the user themselves: {@link ResourceEvent#resourceId()} is their {@code sub}.
   */
  public static final String SESSION_RESOURCE_TYPE = "session";

  private record Subscription(String sessionId, String tenant, String subject, Set<String> permissions, SseEmitter emitter) {

    boolean isFor(ResourceEvent event) {
      return tenant.equals(event.tenant()) && (isOwnerOf(event) || isInAudienceOf(event));
    }

    private boolean isOwnerOf(ResourceEvent event) {
      final @Nullable String owner = event.resourceOwner();
      return owner != null && owner.equals(subject);
    }

    private boolean isInAudienceOf(ResourceEvent event) {
      return event.audience().contains(ResourceEvent.ALL_MEMBERS)
          || event.audience().stream().anyMatch(permissions::contains);
    }

    /** The "your session is over" event for this very subscription. Empty audience: it is addressed, not broadcast. */
    ResourceEvent sessionEndEvent() {
      return new ResourceEvent(
          tenant, SESSION_RESOURCE_TYPE, subject, null, subject, List.of(), EventType.DELETE,
          Instant.now());
    }
  }

  private final Map<String, List<Subscription>> subscriptionsByTenant = new ConcurrentHashMap<>();

  private Stream<Subscription> subscriptions() {
    return subscriptionsByTenant.values().stream().flatMap(List::stream);
  }

  private List<Subscription> subscriptionsOf(String tenant) {
    return subscriptionsByTenant.computeIfAbsent(tenant, t -> new CopyOnWriteArrayList<>());
  }

  /**
   * @param sessionId the id of the HTTP session the subscription is opened from, so that the
   *        browser can be told when that session ends. Never sent over the wire: it is the value of
   *        the {@code HttpOnly} session cookie.
   * @param tenant the tenant the browser is subscribing for (the one its SPA instance is deployed
   *        for)
   * @param subject the user's {@code sub}, compared with {@link ResourceEvent#resourceOwner()}
   * @param permissions the permissions the user holds on {@code tenant}, compared with
   *        {@link ResourceEvent#audience()}
   * @param emitter the emitter to push the relevant events to
   */
  public boolean register(String sessionId, String tenant, String subject, Set<String> permissions, SseEmitter emitter) {
    final var open = size();
    if (open >= properties.getMaxSubscriptions()) {
      log.warn("Refusing a subscription to the events of {}: the gateway already holds {} streams",
          tenant, open);
      completeWithError(emitter, new IllegalStateException("Too many open event streams"));
      return false;
    }
    evictOldestOfSessionIfFull(sessionId);
    final var subscription = new Subscription(sessionId, tenant, subject, Set.copyOf(permissions), emitter);
    final var ofTenant = subscriptionsOf(tenant);
    ofTenant.add(subscription);
    final Runnable cleanup = () -> ofTenant.remove(subscription);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(t -> cleanup.run());
    return true;
  }

  /**
   * Keeps a single session from accumulating streams (a reload loop, a tab that never completes its
   * request). The oldest is recycled rather than the newcomer refused: the tabs a user is actually
   * looking at are the most recent ones.
   */
  private void evictOldestOfSessionIfFull(String sessionId) {
    var openForSession = subscriptions().filter(s -> sessionId.equals(s.sessionId())).count();
    while (openForSession >= properties.getMaxSubscriptionsPerSession()) {
      final var oldest = subscriptions()
          .filter(s -> sessionId.equals(s.sessionId()))
          .findFirst()
          .orElse(null);
      if (oldest == null) {
        return;
      }
      log.debug("Recycling the oldest of the {} streams already open for a session", openForSession);
      complete(oldest);
      openForSession--;
    }
  }

  /**
   * Pushes the event to the browsers subscribed for its tenant whose user either owns the resource
   * or holds one of the audience permissions.
   */
  public void relay(ResourceEvent event) {
    for (final var subscription : subscriptionsByTenant.getOrDefault(event.tenant(), List.of())) {
      if (subscription.isFor(event)) {
        send(subscription, event);
      }
    }
  }

  /**
   * Tells the browsers subscribed from the given HTTP session that it is over, then closes their
   * streams. Routing is on the session and not on the user: the same user can have other sessions
   * (another browser, another device) which are still perfectly valid.
   *
   * @param sessionId the id of the session that has just been destroyed
   */
  public void endSession(String sessionId) {
    // collected first: complete() removes from the lists being iterated
    final var ofSession = subscriptions().filter(s -> sessionId.equals(s.sessionId())).toList();
    for (final var subscription : ofSession) {
      send(subscription, subscription.sessionEndEvent());
      complete(subscription);
    }
  }

  /** @return how many browsers are currently subscribed (for tests and diagnostics) */
  public int size() {
    return (int) subscriptions().count();
  }

  private void send(Subscription subscription, ResourceEvent event) {
    try {
      subscription.emitter().send(event);
    } catch (IOException | IllegalStateException e) {
      log.debug("Failed to send {} to {}, dropping emitter", event, subscription.subject(), e);
      subscriptionsOf(subscription.tenant()).remove(subscription);
      // Dropping the subscription is not enough: without this, the Servlet async context stays
      // open on a connection we already know is unusable.
      completeWithError(subscription.emitter(), e);
    }
  }

  private static void completeWithError(SseEmitter emitter, Throwable cause) {
    try {
      emitter.completeWithError(cause);
    } catch (RuntimeException e) {
      // already completed, or a response the container has recycled in the meantime
      log.debug("Failed to complete an emitter with an error", e);
    }
  }

  private void complete(Subscription subscription) {
    try {
      // also removes the subscription, through the onCompletion callback set when registering
      subscription.emitter().complete();
    } catch (RuntimeException e) {
      // already completed, or a response the container has recycled in the meantime
      log.debug("Failed to complete the emitter of {}", subscription.subject(), e);
    } finally {
      subscriptionsOf(subscription.tenant()).remove(subscription);
    }
  }
}
