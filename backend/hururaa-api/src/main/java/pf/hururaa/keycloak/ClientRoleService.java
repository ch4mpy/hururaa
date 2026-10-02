package pf.hururaa.keycloak;

import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.ApplicationRole;
import pf.hururaa.problem.HururaaProblemException;

/**
 * <p>
 * Adapter in front of {@link CachingKeycloakRoleRepository}: the roles of an application, which are the client roles
 * of its {@code <prefix>-api} Keycloak client.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class ClientRoleService {

  private final CachingKeycloakRoleRepository roleRepo;

  private final KeycloakRepresentationMapper mapper;

  /**
   * @param clientId a Keycloak client ID (not its UUID)
   * @return the client's roles, by name
   */
  public List<ApplicationRole> findAll(String clientId) throws HururaaProblemException {
    return roleRepo
        .findAllClientRoles(clientId)
        .stream()
        .map(mapper::toApplicationRole)
        .sorted(Comparator.comparing(ApplicationRole::name))
        .toList();
  }

  /**
   * Idempotent: does nothing when the client already has a role with that name.
   *
   * @param clientId a Keycloak client ID (not its UUID)
   * @param name the role name
   * @param description what the role allows
   * @return whether the role was created (false if it existed already)
   */
  public boolean save(String clientId, String name, @Nullable String description)
      throws HururaaProblemException {
    final var existed = exists(clientId, name);
    roleRepo.saveClientRole(clientId, name, description);
    return !existed;
  }

  /**
   * Idempotent: does nothing when the client has no role with that name. Keycloak drops the role's
   * group mappings along with it.
   *
   * @param clientId a Keycloak client ID (not its UUID)
   * @param name the role name
   * @return whether the role was deleted (false if there was none)
   */
  public boolean delete(String clientId, String name) throws HururaaProblemException {
    if (!exists(clientId, name)) {
      return false;
    }
    roleRepo.deleteClientRole(clientId, name);
    return true;
  }

  private boolean exists(String clientId, String name) throws HururaaProblemException {
    return roleRepo
        .findAllClientRoles(clientId)
        .stream()
        .anyMatch(role -> name.equals(role.getName()));
  }
}
