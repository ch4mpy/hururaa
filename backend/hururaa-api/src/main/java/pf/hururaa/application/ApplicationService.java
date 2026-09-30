package pf.hururaa.application;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.keycloak.ClientProvisioningService;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;

/**
 * The rules tying an application to its direction and to its Keycloak clients.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

  private final ApplicationRepository applicationRepository;

  private final DirectionService directionService;

  private final GroupService groupService;

  private final ClientProvisioningService clientProvisioningService;

  private final KeycloakAdminApiProperties keycloakProperties;

  /**
   * Registers an application, creating its Keycloak clients ({@code <prefix>-bff} and
   * {@code <prefix>-api}) when they don't exist yet.
   *
   * <p>
   * The clients are created before the application is saved, and are not removed if the save then
   * fails: registering again reuses them.
   * </p>
   *
   * @throws HururaaProblemException {@code APPLICATION_ALREADY_EXISTS} if the client prefix is
   *         taken, {@code DIRECTION_NOT_FOUND} if the direction does not exist
   */
  public Application create(Application application) throws HururaaProblemException {
    if (applicationRepository.existsByClientPrefix(application.getClientPrefix())) {
      throw new HururaaProblemException(
          ProblemType.APPLICATION_ALREADY_EXISTS,
          "An application with client prefix %s already exists"
              .formatted(application.getClientPrefix()),
          Map.of("clientPrefix", application.getClientPrefix()));
    }
    requireDirection(application.getDirection());
    clientProvisioningService
        .ensureApplicationClients(application.getClientPrefix(), application.getName());
    return applicationRepository.save(application);
  }

  /**
   * Renames an application and (re)assigns it to a direction. Moving it to another direction drops
   * its managers, who are members of the former one.
   *
   * @throws HururaaProblemException {@code APPLICATION_ROLES_STILL_GRANTED} when moving an
   *         application whose roles groups of its current direction still grant,
   *         {@code DIRECTION_NOT_FOUND} if the new direction does not exist
   */
  public Application update(Application application, String name, String direction)
      throws HururaaProblemException {
    if (!application.getDirection().equals(direction)) {
      requireDirection(direction);
      requireNotGranted(application);
      application.setDirection(direction);
      application.getManagers().clear();
    }
    application.setName(name);
    return applicationRepository.save(application);
  }

  /**
   * Unregisters an application. Its Keycloak clients are left untouched.
   *
   * @throws HururaaProblemException {@code APPLICATION_ROLES_STILL_GRANTED} when groups of the
   *         application's direction still grant its roles
   */
  public void delete(Application application) throws HururaaProblemException {
    requireNotGranted(application);
    applicationRepository.delete(application);
  }

  /**
   * An application leaving its direction must not leave behind groups granting its roles: nobody
   * could manage these mappings anymore (they are out of reach of the new direction's groups, and
   * of the former direction's application managers).
   */
  private void requireNotGranted(Application application) throws HururaaProblemException {
    final var clientId = keycloakProperties.apiClientId(application.getClientPrefix());
    final var granting = new ArrayList<String>();
    for (final var group : groupService.findAll(application.getDirection())) {
      if (!groupService.findClientRoles(application.getDirection(), group.name(), clientId)
          .isEmpty()) {
        granting.add(group.name());
      }
    }
    if (!granting.isEmpty()) {
      throw new HururaaProblemException(
          ProblemType.APPLICATION_ROLES_STILL_GRANTED,
          "Groups %s of %s still grant roles of %s"
              .formatted(granting, application.getDirection(), application.getClientPrefix()),
          Map.of("applicationId", Objects.requireNonNull(application.getId()), "groups",
              String.join(",", granting)));
    }
  }

  private void requireDirection(String direction) throws HururaaProblemException {
    if (!directionService.exists(direction)) {
      throw new HururaaProblemException(
          ProblemType.DIRECTION_NOT_FOUND,
          "No direction with alias %s".formatted(direction),
          Map.of("direction", direction));
    }
  }
}
