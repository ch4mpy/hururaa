package pf.hururaa.direction.domain;

import java.util.Set;

/**
 * A group of a direction, with what it takes to manage it: what {@code @PreAuthorize} rules of the
 * endpoints addressing a group are written against.
 *
 * <p>
 * A group grants the roles of possibly several applications of its direction: changing its
 * members, or deleting it, changes the permissions of users on each of them. It is therefore
 * manageable by the direction's administrators, who have a say on all of its applications, and
 * otherwise only by a user managing every application whose roles it grants (by any manager in the
 * direction for a group granting nothing yet).
 * </p>
 *
 * @param id Keycloak's group id
 * @param name the group's name, unique within its direction
 * @param direction the direction owning the group
 * @param grantingApplicationsManagers for each application whose roles the group grants, the ids
 *        of its managers
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record DelegatedGroup(String id, String name, DelegatedDirection direction,
    Set<Set<String>> grantingApplicationsManagers) {

  public DelegatedGroup {
    grantingApplicationsManagers = Set.copyOf(grantingApplicationsManagers);
  }

  /** Whether the user may change the group's members, or delete it. */
  public boolean isManageableBy(String userId) {
    return direction.isAdministeredBy(userId) || (direction.isManagedBy(userId)
        && grantingApplicationsManagers.stream().allMatch(managers -> managers.contains(userId)));
  }
}
