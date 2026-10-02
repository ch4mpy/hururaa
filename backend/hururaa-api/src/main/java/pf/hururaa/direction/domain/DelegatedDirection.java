package pf.hururaa.direction.domain;

import java.util.Set;

/**
 * A direction (a Keycloak organization) with the delegations Hurura'a stores for it: what
 * {@code @PreAuthorize} rules of the endpoints addressing a direction are written against.
 *
 * @param alias the organization's alias, how the direction is addressed in this API
 * @param name the organization's name
 * @param admins the ids of the direction's administrators
 * @param managers the ids of the users managing at least one of the direction's applications
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record DelegatedDirection(String alias, String name, Set<String> admins,
    Set<String> managers) {

  public DelegatedDirection {
    admins = Set.copyOf(admins);
    managers = Set.copyOf(managers);
  }

  /** Whether the user designates the managers of the direction's applications. */
  public boolean isAdministeredBy(String userId) {
    return admins.contains(userId);
  }

  /** Whether the user manages at least one of the direction's applications. */
  public boolean isManagedBy(String userId) {
    return managers.contains(userId);
  }

  /** Whether the user has a say on the direction, at any level of the delegation chain. */
  public boolean hasDelegate(String userId) {
    return isAdministeredBy(userId) || isManagedBy(userId);
  }
}
