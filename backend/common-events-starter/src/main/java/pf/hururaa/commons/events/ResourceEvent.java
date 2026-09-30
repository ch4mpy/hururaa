package pf.hururaa.commons.events;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A change on a business resource, published by the service owning it and relayed by the gateway
 * to the browsers of the users allowed to see the resource (which are expected to refetch it over
 * REST). It carries no resource data: only what is needed to tell whether a subscriber should be
 * notified, and for the frontend to tell which of its views is concerned.
 *
 * <p>
 * Events are never relayed across tenants: a subscriber is notified only when subscribed to the
 * event's {@link #tenant()}, and either owning the resource or holding on that tenant at least
 * one of the {@link #audience()} permissions.
 * </p>
 *
 * @param tenant the tenant (Keycloak organization) owning the resource
 * @param resourceType a type name the frontend routes the event with (e.g. {@code application})
 * @param resourceId the identifier of the resource, as a string (ids are opaque to the gateway)
 * @param parentId the identifier of the resource this one is a sub-resource of (an application for
 *        one of its roles),
 *        {@code null} for top-level resources
 * @param resourceOwner the {@code sub} of the user owning the resource, notified
 *        whatever their permissions; {@code null} when the resource has no owner
 * @param audience the permissions on {@code tenant} any of which entitles a user to be notified, or
 *        {@link #ALL_MEMBERS} to notify every member of the tenant
 * @param eventType what happened to the resource
 * @param occurredAt when it happened
 */
public record ResourceEvent(
    String tenant,
    String resourceType,
    String resourceId,
    @Nullable String parentId,
    @Nullable String resourceOwner,
    List<String> audience,
    EventType eventType,
    Instant occurredAt) {

  /**
   * An {@link #audience()} entry notifying every subscriber of the tenant (all its members): for
   * resources whose readers are not decided by a token role (Hurura'a's delegations are stored in
   * its own database). Events carry no resource data, subscribers refetch it over REST with their
   * own access rights.
   */
  public static final String ALL_MEMBERS = "*";

  public enum EventType {
    CREATE, UPDATE, DELETE;
  }
}
