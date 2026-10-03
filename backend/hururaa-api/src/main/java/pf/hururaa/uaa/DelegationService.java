package pf.hururaa.uaa;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pf.hururaa.application.domain.Application;
import pf.hururaa.direction.domain.User;
import pf.hururaa.keycloak.ClientRoleService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.problem.HururaaProblemException;

/**
 * Hurura'a's delegations in Keycloak: the {@link DelegationGroups delegation groups} and the
 * {@link HururaaPermission reserved roles} they grant, created along with the directions and
 * applications, and their members, who are the delegates.
 *
 * <p>
 * Every operation is idempotent, and those changing members first make sure the group exists and
 * grants its role: a direction or an application created before Hurura'a provisioned its
 * delegations (or in Keycloak's console) gets them on its first designation.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
public class DelegationService {

  private final GroupService groupService;

  private final ClientRoleService clientRoleService;

  /** The ID of {@code hururaa-api}, the client carrying Hurura'a's roles. */
  private final String rolesNamespace;

  public DelegationService(GroupService groupService, ClientRoleService clientRoleService,
      @Value("${roles-namespace}") String rolesNamespace) {
    this.groupService = groupService;
    this.clientRoleService = clientRoleService;
    this.rolesNamespace = rolesNamespace;
  }

  /**
   * Creates the direction's {@value DelegationGroups#ADMINS} group, granting
   * {@link HururaaPermission#DIRECTION_ADMIN}.
   */
  public void provisionDirection(String direction) throws HururaaProblemException {
    provision(direction, DelegationGroups.ADMINS, HururaaPermission.Names.DIRECTION_ADMIN,
        "Administer the direction (in the DSI: every direction)");
  }

  /**
   * Creates the application's manager role and its {@code hururaa.<prefix>.product-owners} group,
   * granting it, in the application's direction.
   */
  public void provisionApplication(Application application) throws HururaaProblemException {
    provision(application.getDirection(), productOwners(application), managerRole(application),
        "Manage " + application.getName());
  }

  /**
   * Deletes the application's {@code hururaa.<prefix>.product-owners} group and manager role: once
   * unregistered, an application has no managers.
   */
  public void deprovisionApplication(Application application) throws HururaaProblemException {
    groupService.delete(application.getDirection(), productOwners(application));
    clientRoleService.delete(rolesNamespace, managerRole(application));
  }

  /**
   * @return the direction's administrators, by username (none when the direction has no
   *         {@value DelegationGroups#ADMINS} group yet)
   */
  public List<User> findAdmins(String direction) throws HururaaProblemException {
    return members(direction, DelegationGroups.ADMINS);
  }

  /**
   * @return whether the user was designated (false if already an administrator)
   * @throws HururaaProblemException {@code NOT_A_MEMBER} when the user is not a member of the
   *         direction
   */
  public boolean addAdmin(String direction, String userId) throws HururaaProblemException {
    provisionDirection(direction);
    return groupService.addMember(direction, DelegationGroups.ADMINS, userId);
  }

  /**
   * @return whether the user was revoked (false if not an administrator)
   */
  public boolean removeAdmin(String direction, String userId) throws HururaaProblemException {
    return groupService.findByName(direction, DelegationGroups.ADMINS).isPresent()
        && groupService.removeMember(direction, DelegationGroups.ADMINS, userId);
  }

  /**
   * @return the application's managers, by username
   */
  public List<User> findManagers(Application application) throws HururaaProblemException {
    return members(application.getDirection(), productOwners(application));
  }

  /**
   * @return whether the user was designated (false if already a manager)
   * @throws HururaaProblemException {@code NOT_A_MEMBER} when the user is not a member of the
   *         application's direction
   */
  public boolean addManager(Application application, String userId)
      throws HururaaProblemException {
    provisionApplication(application);
    return groupService.addMember(application.getDirection(), productOwners(application), userId);
  }

  /**
   * @return whether the user was revoked (false if not a manager)
   */
  public boolean removeManager(Application application, String userId)
      throws HururaaProblemException {
    final var group = productOwners(application);
    return groupService.findByName(application.getDirection(), group).isPresent()
        && groupService.removeMember(application.getDirection(), group, userId);
  }

  private void provision(String direction, String group, String role, String description)
      throws HururaaProblemException {
    clientRoleService.save(rolesNamespace, role, description);
    groupService.save(direction, group);
    groupService.addClientRole(direction, group, rolesNamespace, role);
  }

  private List<User> members(String direction, String group) throws HururaaProblemException {
    return groupService.findByName(direction, group).isPresent()
        ? groupService.findAllMembers(direction, group)
        : List.of();
  }

  private static String productOwners(Application application) {
    return DelegationGroups.productOwners(application.getClientPrefix());
  }

  private static String managerRole(Application application) {
    return HururaaPermission.applicationManager(application.getClientPrefix());
  }
}
