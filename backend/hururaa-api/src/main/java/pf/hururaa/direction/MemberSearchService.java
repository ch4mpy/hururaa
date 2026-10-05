package pf.hururaa.direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.direction.domain.User;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.uaa.DelegationGroups;

/**
 * Searches a direction's members, optionally narrowed to the members of some of its groups: one
 * group, the groups of one of its applications, or those granting a role.
 *
 * <p>
 * Keycloak can't search the members of several groups at once: when narrowed, the members of the
 * matching groups are read whole, then filtered, sorted by username and paged here. Hurura'a's
 * reserved groups ({@code hururaa.*}) are never searched.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class MemberSearchService {

  private final DirectionService directionService;

  private final GroupService groupService;

  private final ApplicationRepository applicationRepository;

  private final KeycloakAdminApiProperties keycloakProperties;

  /**
   * @param direction a direction alias
   * @param filter the search criteria
   * @param pageable page index and size; sort is ignored
   * @return the direction's members matching {@code filter}
   * @throws HururaaProblemException {@code GROUP_NOT_FOUND} when {@code filter.group()} is not a
   *         group of the direction, {@code RESERVED_NAME} when it is one of Hurura'a's
   */
  public Page<User> search(String direction, MemberFilter filter, Pageable pageable)
      throws HururaaProblemException {
    if (!filter.narrowsGroups()) {
      return directionService.searchMembers(direction, filter.search(), pageable);
    }
    final var members = new LinkedHashMap<String, User>();
    for (final var group : matchingGroups(direction, filter)) {
      for (final var member : groupService.findAllMembers(direction, group)) {
        if (contains(member, filter.search())) {
          members.putIfAbsent(member.id(), member);
        }
      }
    }
    final var sorted =
        members.values().stream().sorted(Comparator.comparing(User::username)).toList();
    final var from = (int) Math.min(pageable.getOffset(), sorted.size());
    final var to = Math.min(from + pageable.getPageSize(), sorted.size());
    return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
  }

  /** The names of the direction's groups matching the group, application and role criteria. */
  private List<String> matchingGroups(String direction, MemberFilter filter)
      throws HururaaProblemException {
    final var applications = applicationRepository.findByDirectionOrderByNameAsc(direction);
    final var matching = new ArrayList<String>();
    for (final var group : candidateGroups(direction, filter.group())) {
      final var owner = Application.owningGroup(applications, group);
      final var application = filter.application();
      if (application != null
          && !owner.map(a -> Objects.equals(a.getId(), application.getId())).orElse(false)) {
        continue;
      }
      if (filter.role() != null && (owner.isEmpty() || !groupService
          .findClientRoles(direction, group,
              keycloakProperties.apiClientId(owner.get().getClientPrefix()))
          .contains(filter.role()))) {
        continue;
      }
      matching.add(group);
    }
    return matching;
  }

  private List<String> candidateGroups(String direction, @Nullable String group)
      throws HururaaProblemException {
    if (group == null) {
      return groupService
          .findAll(direction)
          .stream()
          .map(Group::name)
          .filter(name -> !DelegationGroups.isReserved(name))
          .toList();
    }
    if (DelegationGroups.isReserved(group)) {
      throw new HururaaProblemException(ProblemType.RESERVED_NAME,
          "Group %s is reserved to Hurura'a".formatted(group), Map.of("name", group));
    }
    if (groupService.findByName(direction, group).isEmpty()) {
      throw new HururaaProblemException(ProblemType.GROUP_NOT_FOUND,
          "No group %s in direction %s".formatted(group, direction),
          Map.of("direction", direction, "group", group));
    }
    return List.of(group);
  }

  /** Mirrors Keycloak's search: contained in the username, first or last name, or e-mail. */
  private static boolean contains(User user, String search) {
    if (search.isBlank()) {
      return true;
    }
    final var needle = search.trim().toLowerCase(Locale.ROOT);
    return Stream.of(user.username(), user.firstName(), user.lastName(), user.email())
        .filter(Objects::nonNull)
        .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(needle));
  }
}
