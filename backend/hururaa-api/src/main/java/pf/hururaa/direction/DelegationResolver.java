package pf.hururaa.direction;

import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.direction.domain.DelegatedDirection;
import pf.hururaa.direction.domain.DelegatedGroup;
import pf.hururaa.direction.domain.DirectionAdmin;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;

/**
 * Joins what Keycloak knows of a direction or a group (it exists, its name) with the delegations
 * Hurura'a stores for it, into the objects the access rules are written against.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class DelegationResolver {

  private final DirectionService directionService;

  private final GroupService groupService;

  private final DirectionAdminRepository directionAdminRepository;

  private final ApplicationRepository applicationRepository;

  /**
   * @throws HururaaProblemException {@code DIRECTION_NOT_FOUND} if the direction does not exist
   */
  public DelegatedDirection direction(String alias) throws HururaaProblemException {
    final var direction = directionService
        .findByAlias(alias)
        .orElseThrow(() -> new HururaaProblemException(ProblemType.DIRECTION_NOT_FOUND,
            "No direction with alias %s".formatted(alias), Map.of("direction", alias)));
    final var admins = directionAdminRepository
        .findByDirectionOrderByUserId(alias)
        .stream()
        .map(DirectionAdmin::getUserId)
        .collect(Collectors.toSet());
    final var managers = new HashSet<String>();
    for (final var application : applicationRepository.findByDirectionOrderByNameAsc(alias)) {
      managers.addAll(application.getManagers());
    }
    return new DelegatedDirection(direction.alias(), direction.name(), admins, managers);
  }

  /**
   * @throws HururaaProblemException {@code DIRECTION_NOT_FOUND} if the direction does not exist,
   *         {@code GROUP_NOT_FOUND} if the group does not
   */
  public DelegatedGroup group(String directionAlias, String groupName)
      throws HururaaProblemException {
    final var direction = direction(directionAlias);
    final var group = groupService
        .findByName(directionAlias, groupName)
        .orElseThrow(() -> new HururaaProblemException(ProblemType.GROUP_NOT_FOUND,
            "No group %s in direction %s".formatted(groupName, directionAlias),
            Map.of("direction", directionAlias, "group", groupName)));
    return new DelegatedGroup(group.id(), group.name(), direction, Application
        .owningGroup(applicationRepository.findByDirectionOrderByNameAsc(directionAlias),
            group.name())
        .orElse(null));
  }
}
