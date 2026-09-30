package pf.hururaa.keycloak;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.keycloak.admin.model.GroupRepresentation;
import org.keycloak.admin.model.RoleRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.direction.domain.User;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;

/**
 * <p>
 * Adapter in front of {@link CachingKeycloakOrganizationGroupRepository}: the groups of a direction
 * (Keycloak organization groups), their members and the client roles they grant, addressed by
 * direction alias and group name rather than Keycloak's internal ids.
 * </p>
 *
 * <p>
 * Business rules about <em>which</em> client roles a group may grant (only those of the
 * applications of its direction) belong to the callers: this service knows nothing about
 * applications.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class GroupService {

  private final DirectionService directionService;

  private final CachingKeycloakOrganizationGroupRepository groupRepo;

  private final KeycloakRepresentationMapper mapper;

  /**
   * @param direction a direction alias
   * @return the direction's (root level) groups, by name
   */
  public List<Group> findAll(String direction) throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    return groupRepo
        .findGroups(orgId)
        .stream()
        .map(group -> mapper.toGroup(group, direction))
        .sorted(Comparator.comparing(Group::name))
        .toList();
  }

  public Optional<Group> findByName(String direction, String groupName)
      throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    final var group = groupRepo.findGroupByName(orgId, groupName);
    return group.isPresent() ? Optional.of(mapper.toGroup(group.get(), direction))
        : Optional.empty();
  }

  /**
   * Idempotent: returns the existing group when one already has that name, otherwise creates it.
   */
  public Group save(String direction, String groupName) throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    final var existing = groupRepo.findGroupByName(orgId, groupName);
    final var group = existing.isPresent() ? existing.get()
        : groupRepo.saveGroup(orgId, new GroupRepresentation().name(groupName));
    return mapper.toGroup(group, direction);
  }

  /**
   * Idempotent: does nothing when no group has that name.
   */
  public void delete(String direction, String groupName) throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    final var group = groupRepo.findGroupByName(orgId, groupName);
    if (group.isPresent()) {
      groupRepo.deleteGroup(orgId, requireId(group.get()));
    }
  }

  public Page<User> findMembers(String direction, String groupName, Pageable pageable)
      throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    final var groupId = requireGroupId(direction, orgId, groupName);
    return groupRepo.findGroupMembers(orgId, groupId, pageable).map(mapper::toUser);
  }

  /**
   * @return the direction's groups the user is a member of
   * @throws HururaaProblemException {@code NOT_A_MEMBER} when the user is not a member of the
   *         direction
   */
  public List<Group> findByMember(String direction, String userId)
      throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    return groupRepo
        .findMemberGroups(orgId, userId)
        .stream()
        .map(group -> mapper.toGroup(group, direction))
        .sorted(Comparator.comparing(Group::name))
        .toList();
  }

  /**
   * Idempotent: does nothing when the user is already a member.
   *
   * @throws HururaaProblemException {@code NOT_A_MEMBER} when the user is not a member of the
   *         direction (only its members can join its groups)
   */
  public void addMember(String direction, String groupName, String userId)
      throws HururaaProblemException {
    directionService.requireMember(direction, userId);
    final var orgId = directionService.requireOrgId(direction);
    final var groupId = requireGroupId(direction, orgId, groupName);
    groupRepo.addGroupMember(orgId, groupId, userId);
  }

  /**
   * Idempotent: does nothing when the user is not a member.
   */
  public void removeMember(String direction, String groupName, String userId)
      throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    final var groupId = requireGroupId(direction, orgId, groupName);
    groupRepo.removeGroupMember(orgId, groupId, userId);
  }

  /**
   * @param clientId the ID of the Keycloak client whose roles are wanted
   * @return the names of that client's roles the group grants to its members
   */
  public List<String> findClientRoles(String direction, String groupName, String clientId)
      throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    final var groupId = requireGroupId(direction, orgId, groupName);
    return groupRepo
        .findClientRolesByGroupId(clientId, orgId, groupId)
        .stream()
        .map(RoleRepresentation::getName)
        .filter(Objects::nonNull)
        .sorted()
        .toList();
  }

  /**
   * Idempotent: does nothing when the group already grants the role.
   *
   * @throws HururaaProblemException {@code APPLICATION_ROLE_NOT_FOUND} when the client has no such
   *         role
   */
  public void addClientRole(String direction, String groupName, String clientId, String role)
      throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    final var groupId = requireGroupId(direction, orgId, groupName);
    groupRepo.addClientRoleToGroup(clientId, orgId, groupId, role);
  }

  /**
   * Idempotent: does nothing when the group does not grant the role.
   */
  public void removeClientRole(String direction, String groupName, String clientId, String role)
      throws HururaaProblemException {
    final var orgId = directionService.requireOrgId(direction);
    final var groupId = requireGroupId(direction, orgId, groupName);
    groupRepo.removeClientRoleFromGroup(clientId, orgId, groupId, role);
  }

  private String requireGroupId(String direction, String orgId, String groupName)
      throws HururaaProblemException {
    final var group = groupRepo.findGroupByName(orgId, groupName);
    if (group.isEmpty()) {
      throw new HururaaProblemException(
          ProblemType.GROUP_NOT_FOUND,
          "No group named %s in direction %s".formatted(groupName, direction),
          Map.of("direction", direction, "group", groupName));
    }
    return requireId(group.get());
  }

  private static String requireId(GroupRepresentation group) {
    return Objects.requireNonNull(group.getId(), "group id");
  }
}
