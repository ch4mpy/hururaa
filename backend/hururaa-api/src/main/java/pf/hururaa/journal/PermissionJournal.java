package pf.hururaa.journal;

import java.time.Instant;
import java.util.Objects;
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

  public void applicationRoleCreated(Application application, String role) {
    save(roleEvent(application.getDirection(), PermissionEventType.APPLICATION_ROLE_CREATED,
        application, role));
  }

  public void applicationRoleDeleted(Application application, String role) {
    save(roleEvent(application.getDirection(), PermissionEventType.APPLICATION_ROLE_DELETED,
        application, role));
  }

  public void groupCreated(String direction, String group) {
    save(event(direction, PermissionEventType.GROUP_CREATED).groupName(group));
  }

  public void groupDeleted(String direction, String group) {
    save(event(direction, PermissionEventType.GROUP_DELETED).groupName(group));
  }

  public void groupRoleGranted(String direction, String group, Application application,
      String role) {
    save(roleEvent(direction, PermissionEventType.GROUP_ROLE_GRANTED, application, role)
        .groupName(group));
  }

  public void groupRoleRevoked(String direction, String group, Application application,
      String role) {
    save(roleEvent(direction, PermissionEventType.GROUP_ROLE_REVOKED, application, role)
        .groupName(group));
  }

  public void groupMemberAdded(String direction, String group, String userId) {
    save(event(direction, PermissionEventType.GROUP_MEMBER_ADDED).groupName(group).userId(userId));
  }

  public void groupMemberRemoved(String direction, String group, String userId) {
    save(event(direction, PermissionEventType.GROUP_MEMBER_REMOVED).groupName(group)
        .userId(userId));
  }

  private static PermissionEvent.PermissionEventBuilder roleEvent(String direction,
      PermissionEventType type, Application application, String role) {
    return event(direction, type)
        .applicationId(application.getId())
        .applicationName(application.getName())
        .role(role);
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
