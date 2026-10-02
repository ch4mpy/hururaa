package pf.hururaa.application;

import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.keycloak.ClientProvisioningService;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
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
   * @throws HururaaProblemException {@code APPLICATION_HAS_GROUPS} when moving an application
   *         which still has groups in its current direction, {@code DIRECTION_NOT_FOUND} if the new
   *         direction does not exist
   */
  public Application update(Application application, String name, String direction)
      throws HururaaProblemException {
    if (!application.getDirection().equals(direction)) {
      requireDirection(direction);
      requireNoGroups(application);
      application.setDirection(direction);
      application.getManagers().clear();
    }
    application.setName(name);
    return applicationRepository.save(application);
  }

  /**
   * Unregisters an application. Its Keycloak clients are left untouched.
   *
   * @throws HururaaProblemException {@code APPLICATION_HAS_GROUPS} when the application still has
   *         groups in its direction
   */
  public void delete(Application application) throws HururaaProblemException {
    requireNoGroups(application);
    applicationRepository.delete(application);
  }

  /**
   * An application leaving its direction must not leave its groups behind: they would be out of
   * reach of its managers, and still grant its roles to their members.
   */
  private void requireNoGroups(Application application) throws HururaaProblemException {
    final var groups = groupService
        .findAll(application.getDirection())
        .stream()
        .map(Group::name)
        .filter(application::ownsGroup)
        .toList();
    if (!groups.isEmpty()) {
      throw new HururaaProblemException(
          ProblemType.APPLICATION_HAS_GROUPS,
          "%s still has groups %s in %s"
              .formatted(application.getClientPrefix(), groups, application.getDirection()),
          Map.of("applicationId", Objects.requireNonNull(application.getId()), "groups",
              String.join(",", groups)));
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
