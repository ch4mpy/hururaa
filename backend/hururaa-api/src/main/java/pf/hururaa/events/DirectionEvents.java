package pf.hururaa.events;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pf.hururaa.commons.events.ResourceEvent;
import pf.hururaa.commons.events.ResourceEvent.EventType;

/**
 * The {@link ResourceEvent}s Hurura'a publishes: always addressed to every member of the direction
 * concerned ({@link ResourceEvent#ALL_MEMBERS}), since who may read a direction's resources is
 * decided by delegations stored in Hurura'a's database, which the gateway knows nothing about. The
 * events carry no data: the frontend refetches with its own access rights.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public final class DirectionEvents {

  /** An application, its roles or its managers changed. {@code resourceId} is its id. */
  public static final String APPLICATION = "application";

  /** A direction's administrators changed. {@code resourceId} is the direction alias. */
  public static final String DIRECTION = "direction";

  /** A group, its roles or its members changed. {@code resourceId} is the group name. */
  public static final String GROUP = "group";

  private DirectionEvents() {}

  /**
   * @param direction the direction whose members are notified
   * @param resourceType one of this class' constants
   * @param resourceId the changed resource's identifier
   * @param eventType what happened
   */
  public static ResourceEvent of(
      String direction,
      String resourceType,
      String resourceId,
      EventType eventType) {
    return of(direction, resourceType, resourceId, null, eventType);
  }

  /**
   * @param parentId the identifier of the resource the changed one belongs to
   */
  public static ResourceEvent of(
      String direction,
      String resourceType,
      String resourceId,
      @Nullable String parentId,
      EventType eventType) {
    return new ResourceEvent(
        direction,
        resourceType,
        resourceId,
        parentId,
        null,
        List.of(ResourceEvent.ALL_MEMBERS),
        eventType,
        Instant.now());
  }
}
