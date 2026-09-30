package pf.hururaa.uaa;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.security.HururaaPermissionEvaluator;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.problem.HururaaProblemException;

/**
 * The access rules of Hurura'a's delegation chain, exposed as the {@code @uaa} bean for
 * {@code @PreAuthorize} expressions:
 *
 * <ol>
 * <li><b>platform administrators</b> hold {@link HururaaPermission Hurura'a's roles} in the
 * {@link UaaProperties#getPlatformOrganization() platform organization} (a token role): they
 * register applications, assign them to directions, and designate each direction's
 * administrators;</li>
 * <li><b>direction administrators</b> (a {@code DirectionAdmin} row) designate, among the members of
 * their direction, the managers of each of its applications;</li>
 * <li><b>application managers</b> (a user id in {@link Application#getManagers()}) define the
 * application's roles, aggregate them into groups of the direction, and assign users to those
 * groups.</li>
 * </ol>
 *
 * <p>
 * Levels 2 and 3 are read from the database on every request rather than from the token: a
 * delegation takes effect (or is revoked) at once, without the delegate having to log in again.
 * </p>
 *
 * <p>
 * The {@code Authentication}'s name is the user's {@code sub}, which is the Keycloak user id the
 * delegations are stored with.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component("uaa")
@RequiredArgsConstructor
public class UaaAuthorization {

  private final UaaProperties properties;

  private final HururaaPermissionEvaluator permissionEvaluator;

  private final DirectionAdminRepository directionAdminRepository;

  private final ApplicationRepository applicationRepository;

  private final GroupService groupService;

  private final KeycloakAdminApiProperties keycloakProperties;

  /**
   * @param permission one of {@link HururaaPermission.Names}
   * @return whether the user holds {@code permission} in the platform organization
   */
  public boolean hasPlatformPermission(Authentication authentication, String permission) {
    return permissionEvaluator
        .hasPermission(authentication, properties.getPlatformOrganization(), permission);
  }

  /**
   * @return the {@link HururaaPermission Hurura'a roles} the user holds in the platform
   *         organization
   */
  public List<String> platformPermissions(Authentication authentication) {
    return HururaaPermission.ALL
        .stream()
        .filter(permission -> hasPlatformPermission(authentication, permission))
        .sorted()
        .toList();
  }

  /**
   * @return whether the user was designated administrator of {@code direction}
   */
  public boolean isDirectionAdmin(Authentication authentication, String direction) {
    return directionAdminRepository.existsByDirectionAndUserId(direction, authentication.getName());
  }

  /**
   * @return whether the user was designated manager of {@code application}
   */
  public boolean isApplicationManager(Authentication authentication, Application application) {
    return application.getManagers().contains(authentication.getName());
  }

  /**
   * @return whether the user manages at least one of {@code direction}'s applications (which is
   *         what it takes to create groups in that direction)
   */
  public boolean isManagerInDirection(Authentication authentication, String direction) {
    return applicationRepository.existsByDirectionAndManager(direction, authentication.getName());
  }

  /**
   * Who may see an application's roles and managers: whoever has a say on the application, at any
   * level of the delegation chain.
   */
  public boolean canReadApplication(Authentication authentication, Application application) {
    return hasPlatformPermission(authentication, HururaaPermission.Names.APPLICATIONS_MANAGE)
        || isDirectionAdmin(authentication, application.getDirection())
        || isApplicationManager(authentication, application);
  }

  /**
   * Who may see a direction's administrators, groups and members: whoever has a say on the
   * direction, at any level of the delegation chain.
   */
  public boolean canReadDirection(Authentication authentication, String direction) {
    return hasPlatformPermission(authentication, HururaaPermission.Names.DIRECTION_ADMINS_MANAGE)
        || isDirectionAdmin(authentication, direction)
        || isManagerInDirection(authentication, direction);
  }

  /**
   * A group grants the roles of possibly several applications of its direction: changing its
   * members, or deleting it, changes the permissions of users on each of them. It is allowed only
   * to a user managing every application whose roles the group grants (any manager in the
   * direction for a group granting nothing yet).
   *
   * <p>
   * Checked in the endpoint rather than in its {@code @PreAuthorize}: it depends on the group's role
   * mappings in Keycloak, and a {@code 404} for an unknown group is more helpful than a
   * {@code 403}.
   * </p>
   *
   * @throws AccessDeniedException if the user may not manage the group
   * @throws HururaaProblemException {@code GROUP_NOT_FOUND} if the group does not exist
   */
  public void checkCanManageGroup(Authentication authentication, String direction, String group)
      throws HururaaProblemException {
    if (!isManagerInDirection(authentication, direction)) {
      throw new AccessDeniedException("Not a manager of any application of " + direction);
    }
    for (final var application : applicationRepository.findByDirectionOrderByNameAsc(direction)) {
      if (!isApplicationManager(authentication, application)
          && !groupService
              .findClientRoles(direction, group,
                  keycloakProperties.apiClientId(application.getClientPrefix()))
              .isEmpty()) {
        throw new AccessDeniedException(
            "Group %s of %s grants roles of %s, which %s does not manage"
                .formatted(group, direction, application.getClientPrefix(),
                    authentication.getName()));
      }
    }
  }
}
