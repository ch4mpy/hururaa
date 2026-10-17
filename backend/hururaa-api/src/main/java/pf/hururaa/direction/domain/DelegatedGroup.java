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
 * outside of Hurura'a) is managed by the direction's administrators only.
 * </p>
 *
 * <p>
 * The {@link DelegationGroups delegation groups} ({@link #isReserved() reserved}) belong to the
 * Hurura'a application, in every direction: their members are managed like those of any group,
 * and the managers of an application co-manage its {@code hururaa.<prefix>.product-owners} group
 * ({@link #coManaged()}). The groups themselves and the roles they grant are never changed through
 * the API.
 * </p>
 *
 * @param id Keycloak's group id
 * @param name the group's name, unique within its direction
 * @param direction the direction owning the group
 * @param application the application the group belongs to, if any
 * @param coManaged for a {@code hururaa.<prefix>.product-owners} group, the application of that
 *        prefix in the direction, whose managers co-manage the group
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record DelegatedGroup(String id, String name, DelegatedDirection direction,
    @Nullable Application application, @Nullable Application coManaged) {

  /**
   * Whether the user may change the group's members, and, for a group which is not
   * {@link #isReserved() reserved}, the roles it grants, or delete it.
   */
  public boolean isManageableBy(@Nullable Authentication authentication) {
    return direction.isAdministeredBy(authentication)
        || (application != null && application.isManagedBy(authentication))
        || (coManaged != null && coManaged.isManagedBy(authentication));
  }

  /** Whether the group carries a delegation: only its members change through the API. */
  public boolean isReserved() {
    return DelegationGroups.isReserved(name);
  }
}
