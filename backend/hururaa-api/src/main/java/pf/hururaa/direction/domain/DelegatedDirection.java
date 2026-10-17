package pf.hururaa.direction.domain;

import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import pf.hururaa.application.domain.Application;
import pf.hururaa.uaa.DelegationGroups;
import pf.hururaa.uaa.HururaaPermission;

/**
 * A direction (a Keycloak organization) with what it takes to read its delegations in a token:
 * what {@code @PreAuthorize} rules of the endpoints addressing a direction are written against.
 *
 * <p>
 * Delegations are {@link HururaaPermission Hurura'a roles} held in the direction (through the
 * {@link pf.hururaa.uaa.DelegationGroups delegation groups}), read from the user's token: they take
 * effect when the token is renewed.
 * </p>
 *
 * @param alias the organization's alias, how the direction is addressed in this API
 * @param name the organization's name
 * @param platformOrganization alias of the DSI, whose administrators are the Hurura'a
 *        administrators, acting in every direction
 * @param applicationPrefixes the client prefixes of the direction's applications
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record DelegatedDirection(String alias, String name, String platformOrganization,
    Set<String> applicationPrefixes) {

  public DelegatedDirection {
    applicationPrefixes = Set.copyOf(applicationPrefixes);
  }

  /**
   * Whether the user administers the direction: registers its applications, designates their
   * managers, manages their groups. Administrators of the DSI administer every direction.
   */
  public boolean isAdministeredBy(@Nullable Authentication authentication) {
    return HururaaPermission.heldIn(authentication, alias)
        .contains(HururaaPermission.Names.DIRECTION_ADMIN)
        || HururaaPermission.heldIn(authentication, platformOrganization)
            .contains(HururaaPermission.Names.DIRECTION_ADMIN);
  }

  /**
   * Whether the user manages at least one of the direction's applications, or Hurura'a, whose
   * delegation groups live in every direction (manager role held in the DSI).
   */
  public boolean isManagedBy(@Nullable Authentication authentication) {
    final var held = HururaaPermission.heldIn(authentication, alias);
    return applicationPrefixes
        .stream()
        .anyMatch(prefix -> held.contains(HururaaPermission.applicationManager(prefix)))
        || HururaaPermission.heldIn(authentication, platformOrganization)
            .contains(HururaaPermission.applicationManager(DelegationGroups.HURURAA_CLIENT_PREFIX));
  }

  /** Whether the user has a say on the direction, at any level of the delegation chain. */
  public boolean hasDelegate(@Nullable Authentication authentication) {
    return isAdministeredBy(authentication) || isManagedBy(authentication);
  }

  /**
   * Whether the user may read an application's roles and history under this direction: the
   * direction's administrators and the application's managers; for Hurura'a, which is available
   * in every direction, any delegate of the direction.
   */
  public boolean canReadApplication(Application application,
      @Nullable Authentication authentication) {
    if (!application.isAvailableIn(alias)) {
      return false;
    }
    if (application.isHururaa()) {
      return hasDelegate(authentication);
    }
    return isAdministeredBy(authentication) || application.isManagedBy(authentication);
  }
}
