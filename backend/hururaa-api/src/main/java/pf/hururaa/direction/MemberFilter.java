package pf.hururaa.direction;

import org.jspecify.annotations.Nullable;
import pf.hururaa.application.domain.Application;

/**
 * Criteria of a search among a direction's members. Without {@code group}, {@code application} nor
 * {@code role}, the search is Keycloak's own; otherwise, the members of the direction's groups
 * matching all of them are searched.
 *
 * @param search a string contained in the username, first or last name, or e-mail of the members;
 *        blank for all of them
 * @param group only the members of this group
 * @param application only the members of this application's groups
 * @param role only the members of a group granting a role with this name of its application
 */
public record MemberFilter(
    String search,
    @Nullable String group,
    @Nullable Application application,
    @Nullable String role) {

  /** Whether the search is narrowed to the members of some of the direction's groups. */
  public boolean narrowsGroups() {
    return group != null || application != null || role != null;
  }
}
