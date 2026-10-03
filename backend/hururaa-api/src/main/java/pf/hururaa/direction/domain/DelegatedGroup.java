package pf.hururaa.direction.domain;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import pf.hururaa.application.domain.Application;
import pf.hururaa.uaa.DelegationGroups;

/**
 * A group of a direction, with what it takes to manage it: what {@code @PreAuthorize} rules of the
 * endpoints addressing a group are written against.
 *
 * <p>
 * A group belongs to the application its name starts with ({@code escales.agent} to Escales), and
 * only grants that application's roles: it is managed by the direction's administrators and by the
 * application's managers. A group whose name matches no application of its direction (created
 * outside of Hurura'a) is managed by the direction's administrators only. The
 * {@link DelegationGroups delegation groups} are {@link #isReserved() reserved}: nobody changes them
 * through the groups API.
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
  public boolean isManageableBy(@Nullable Authentication authentication) {
    return direction.isAdministeredBy(authentication)
        || (application != null && application.isManagedBy(authentication));
  }

  /** Whether the group carries a delegation, which only Hurura'a changes. */
  public boolean isReserved() {
    return DelegationGroups.isReserved(name);
  }
}
