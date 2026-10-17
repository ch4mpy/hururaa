package pf.hururaa.direction;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.direction.domain.DelegatedDirection;
import pf.hururaa.direction.domain.DelegatedGroup;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.uaa.DelegationGroups;
import pf.hururaa.uaa.UaaProperties;

/**
 * Joins what Keycloak knows of a direction or a group (it exists, its name) with the applications
 * Hurura'a registered in that direction, into the objects the access rules are written against
 * (which read the delegations in the user's token).
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class DelegationResolver {

  private final DirectionService directionService;

  private final GroupService groupService;

  private final ApplicationRepository applicationRepository;

  private final UaaProperties uaaProperties;

  /**
   * @throws HururaaProblemException {@code DIRECTION_NOT_FOUND} if the direction does not exist
   */
  public DelegatedDirection direction(String alias) throws HururaaProblemException {
    return direction(alias, applicationRepository.findAvailableIn(alias));
  }

  /**
   * @param available the applications available in the direction (its own and Hurura'a), read once
   *        for a direction and its group
   */
  private DelegatedDirection direction(String alias, List<Application> available)
      throws HururaaProblemException {
    final var direction = directionService
        .findByAlias(alias)
        .orElseThrow(() -> new HururaaProblemException(ProblemType.DIRECTION_NOT_FOUND,
            "No direction with alias %s".formatted(alias), Map.of("direction", alias)));
    final var applicationPrefixes = available
        .stream()
        .filter(application -> application.getDirection().equals(alias))
        .map(Application::getClientPrefix)
        .collect(Collectors.toSet());
    return new DelegatedDirection(direction.alias(), direction.name(),
        uaaProperties.getPlatformOrganization(), applicationPrefixes);
  }

  /**
   * @throws HururaaProblemException {@code DIRECTION_NOT_FOUND} if the direction does not exist,
   *         {@code GROUP_NOT_FOUND} if the group does not
   */
  public DelegatedGroup group(String directionAlias, String groupName)
      throws HururaaProblemException {
    final var applications = applicationRepository.findAvailableIn(directionAlias);
    final var direction = direction(directionAlias, applications);
    final var group = groupService
        .findByName(directionAlias, groupName)
        .orElseThrow(() -> new HururaaProblemException(ProblemType.GROUP_NOT_FOUND,
            "No group %s in direction %s".formatted(groupName, directionAlias),
            Map.of("direction", directionAlias, "group", groupName)));
    final var coManaged = DelegationGroups
        .productOwnersPrefix(group.name())
        .flatMap(prefix -> applications
            .stream()
            .filter(application -> application.getClientPrefix().equals(prefix)
                && application.getDirection().equals(directionAlias))
            .findAny())
        .orElse(null);
    return new DelegatedGroup(group.id(), group.name(), direction,
        Application.owningGroup(applications, group.name()).orElse(null), coManaged);
  }
}
