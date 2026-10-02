package pf.hururaa.direction.domain;

import org.jspecify.annotations.Nullable;
import pf.hururaa.application.domain.Application;

/**
 * A group of a direction, with what it takes to manage it: what {@code @PreAuthorize} rules of the
 * endpoints addressing a group are written against.
 *
 * <p>
 * A group belongs to the application its name starts with ({@code escales.agent} to Escales), and
 * only grants that application's roles: it is managed by the direction's administrators and by the
 * application's managers. A group whose name matches no application of its direction (created
 * outside of Hurura'a) is managed by the direction's administrators only.
 * </p>
 *
 * @param id Keycloak's group id
 * @param name the group's name, unique within its direction
 * @param direction the direction owning the group
 * @param application the application the group belongs to, if any
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record DelegatedGroup(String id, String name, DelegatedDirection direction,
    @Nullable Application application) {

  /** Whether the user may change the roles the group grants, its members, or delete it. */
  public boolean isManageableBy(String userId) {
    return direction.isAdministeredBy(userId)
        || (application != null && application.isManagedBy(userId));
  }
}
