package pf.hururaa.journal;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.Application;
import pf.hururaa.journal.domain.PermissionEvent;
import pf.hururaa.journal.domain.PermissionEventType;
import pf.hururaa.journal.jpa.PermissionEventRepository;

/**
 * Journals the permission changes Hurura'a makes in Keycloak, on behalf of the authenticated user.
 *
 * <p>
 * Called once the change is made in Keycloak, and only when it changed something (the Keycloak
 * operations are idempotent): within the endpoint's transaction, so a failing change journals
 * nothing. Should the commit fail after Keycloak accepted the change, that change would go
 * unjournaled; the application logs keep a trace of it anyway.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class PermissionJournal {

  private final PermissionEventRepository repository;

  public void directionCreated(String direction) {
    save(event(direction, PermissionEventType.DIRECTION_CREATED));
  }

  /**
   * @param userId the administrator designated (made a member of the direction's
   *        {@code hururaa.admins} group)
   */
  public void directionAdminGranted(String direction, String userId) {
    save(event(direction, PermissionEventType.DIRECTION_ADMIN_GRANTED).userId(userId));
  }

  public void directionAdminRevoked(String direction, String userId) {
    save(event(direction, PermissionEventType.DIRECTION_ADMIN_REVOKED).userId(userId));
  }

  /**
   * @param userId the manager designated (made a member of the application's
   *        {@code hururaa.<prefix>.product-owners} group)
   */
  public void applicationManagerGranted(Application application, String userId) {
    save(applicationEvent(PermissionEventType.APPLICATION_MANAGER_GRANTED, application)
        .userId(userId));
  }

  public void applicationManagerRevoked(Application application, String userId) {
    save(applicationEvent(PermissionEventType.APPLICATION_MANAGER_REVOKED, application)
        .userId(userId));
  }

  public void applicationRoleCreated(Application application, String role) {
    save(roleEvent(application.getDirection(), PermissionEventType.APPLICATION_ROLE_CREATED,
        application, role));
  }

  public void applicationRoleDeleted(Application application, String role) {
    save(roleEvent(application.getDirection(), PermissionEventType.APPLICATION_ROLE_DELETED,
        application, role));
  }

  /**
   * @param application the application the group belongs to (in whose direction it is created)
   */
  public void groupCreated(Application application, String group) {
    save(groupEvent(application.getDirection(), PermissionEventType.GROUP_CREATED, application,
        group));
  }

  /**
   * @param application the application the group belonged to, if any
   */
  public void groupDeleted(String direction, @Nullable Application application, String group) {
    save(groupEvent(direction, PermissionEventType.GROUP_DELETED, application, group));
  }

  /**
   * @param application the application the group belongs to, whose role it grants
   */
  public void groupRoleGranted(Application application, String group, String role) {
    save(roleEvent(application.getDirection(), PermissionEventType.GROUP_ROLE_GRANTED,
        application, role).groupName(group));
  }

  /**
   * @param application the application the group belongs to, whose role it no longer grants
   */
  public void groupRoleRevoked(Application application, String group, String role) {
    save(roleEvent(application.getDirection(), PermissionEventType.GROUP_ROLE_REVOKED,
        application, role).groupName(group));
  }

  /**
   * @param application the application the group belongs to, if any
   */
  public void groupMemberAdded(String direction, @Nullable Application application, String group,
      String userId) {
    save(groupEvent(direction, PermissionEventType.GROUP_MEMBER_ADDED, application, group)
        .userId(userId));
  }

  /**
   * @param application the application the group belongs to, if any
   */
  public void groupMemberRemoved(String direction, @Nullable Application application,
      String group, String userId) {
    save(groupEvent(direction, PermissionEventType.GROUP_MEMBER_REMOVED, application, group)
        .userId(userId));
  }

  private static PermissionEvent.PermissionEventBuilder applicationEvent(
      PermissionEventType type, Application application) {
    return event(application.getDirection(), type)
        .applicationId(application.getId())
        .applicationName(application.getName());
  }

  private static PermissionEvent.PermissionEventBuilder roleEvent(String direction,
      PermissionEventType type, Application application, String role) {
    return event(direction, type)
        .applicationId(application.getId())
        .applicationName(application.getName())
        .role(role);
  }

  private static PermissionEvent.PermissionEventBuilder groupEvent(String direction,
      PermissionEventType type, @Nullable Application application, String group) {
    final var event = event(direction, type).groupName(group);
    return application == null ? event
        : event.applicationId(application.getId()).applicationName(application.getName());
  }

  /** The author is the authenticated user: the {@code Authentication}'s name is their id. */
  private static PermissionEvent.PermissionEventBuilder event(String direction,
      PermissionEventType type) {
    final var authentication = Objects.requireNonNull(
        SecurityContextHolder.getContext().getAuthentication(),
        "a permission change is journaled on behalf of an authenticated user");
    return PermissionEvent
        .builder()
        .occurredAt(Instant.now())
        .authorId(authentication.getName())
        .direction(direction)
        .type(type);
  }

  private void save(PermissionEvent.PermissionEventBuilder event) {
    repository.save(event.build());
  }
}
