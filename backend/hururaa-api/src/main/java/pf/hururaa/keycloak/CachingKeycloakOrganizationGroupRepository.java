package pf.hururaa.keycloak;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.keycloak.admin.api.ClientRoleMappingsApi;
import org.keycloak.admin.api.OrganizationsApi;
import org.keycloak.admin.model.GroupRepresentation;
import org.keycloak.admin.model.MemberRepresentation;
import org.keycloak.admin.model.RoleRepresentation;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.problem.HururaaProblemException;

/**
 * Facade in front of Keycloak's {@link OrganizationsApi} and {@link ClientRoleMappingsApi} for
 * <b>organization groups</b>: the groups themselves, their members, and the client
 * roles (Hururaa permissions) they grant to their members. It provides:
 * <ul>
 * <li>caching</li>
 * <li>Mapping of the {@link org.springframework.web.client.RestClient RestClient's}
 * {@link HttpClientErrorException} to a checked {@link HururaaProblemException} ({@link
 * ProblemType#IDENTITY_PROVIDER_ERROR}, or a business type where a {@code 404} has one)</li>
 * </ul>
 *
 * <p>
 * Keycloak rejects any operation on an organization group through the realm-level
 * {@code /groups/{id}/...} endpoints ({@code 400 Cannot manage organization related group via non
 * Organization API}): every call here goes through {@code /organizations/{orgId}/groups/...},
 * hence the {@code orgId} parameters.
 * </p>
 *
 * @see CachingKeycloakOrganizationRepository CachingKeycloakOrganizationRepository for the
 *      organizations themselves
 * @see GroupService GroupService for a public, domain-oriented facade
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = {CachingKeycloakOrganizationGroupRepository.GROUPS_CACHE})
class CachingKeycloakOrganizationGroupRepository {
  static final String GROUPS_CACHE = "organizationGroups";
  static final String GROUP_CLIENT_ROLES_CACHE = "groupClientRoles";

  private final KeycloakAdminApiProperties apiProperties;

  private final OrganizationsApi organizationsApi;

  private final ClientRoleMappingsApi clientRoleMappingsApi;

  private final CachingKeycloakRoleRepository rolesService;

  private final CachingKeycloakClientRepository clientsService;

  /**
   * @param orgId
   * @return Oganization's root level groups
   */
  @Cacheable(cacheNames = GROUPS_CACHE, key = "#orgId")
  public List<GroupRepresentation> findGroups(String orgId) throws HururaaProblemException {
    try {
      final var response = organizationsApi
          .adminRealmsRealmOrganizationsOrgIdGroupsGet(
              apiProperties.getRealmName(),
              orgId,
              Optional.empty(), // briefRepresentation
              Optional.of(false), // exact
              Optional.of(0), // first
              Optional.of(Integer.MAX_VALUE), // max
              Optional.empty(), // populateHierarchy (optional, default to false)
              Optional.empty(), // q (optional)
              Optional.empty(), // search
              Optional.empty()// searchsubGroupsCount (optional, default to false)
          );

      return response.getBody() == null || response.getBody().isEmpty() ? List.of()
          : response.getBody();

    } catch (HttpClientErrorException e) {
      log.error("Failed to retrieve {} organization groups : {}", orgId, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching organization %s groups: %s".formatted(orgId, e.getMessage()),
          Map.of());
    }
  }

  @Cacheable(cacheNames = GROUPS_CACHE, key = "#orgId + '_' + #groupName")
  public Optional<GroupRepresentation> findGroupByName(String orgId, String groupName)
      throws HururaaProblemException {
    try {
      return fetchGroupByName(orgId, groupName);
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to retrieve group {} for organization {}: {}",
              groupName,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching group %s for organization %s: %s"
              .formatted(groupName, orgId, e.getMessage()),
          Map.of());
    }
  }

  /**
   * Un-cached counterpart of {@link #findGroupByName(String, String)}: the read that follows a
   * write must not be served by the cache, which still holds the state from before it (typically
   * the empty result of the "does it already exist?" lookup made just before a creation).
   */
  private Optional<GroupRepresentation> fetchGroupByName(String orgId, String groupName)
      throws HttpClientErrorException {
    final var response = organizationsApi
        .adminRealmsRealmOrganizationsOrgIdGroupsGet(
            apiProperties.getRealmName(),
            orgId,
            Optional.empty(), // briefRepresentation
            Optional.of(true), // exact
            Optional.of(0), // first
            Optional.of(1), // max
            Optional.empty(), // populateHierarchy (optional, default to false)
            Optional.empty(), // q (optional)
            Optional.of(groupName), // search
            Optional.empty()// searchsubGroupsCount (optional, default to false)
        );

    final List<GroupRepresentation> groups =
        response.getBody() == null || response.getBody().isEmpty() ? List.of()
            : response.getBody();

    return groups.isEmpty() ? Optional.empty() : Optional.of(groups.get(0));
  }

  @Caching(
      put = @CachePut(cacheNames = GROUPS_CACHE, key = "#orgId + '_' + #result.name"),
      evict = @CacheEvict(cacheNames = GROUPS_CACHE, key = "#orgId"))
  public GroupRepresentation saveGroup(String orgId, GroupRepresentation group)
      throws HururaaProblemException {
    try {
      return group.getId() == null ? createGroup(orgId, group) : updateGroup(orgId, group);
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to save group {} for organization {}: {}",
              group.getName(),
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while saving group %s for organization %s: %s"
              .formatted(group.getName(), orgId, e.getMessage()),
          Map.of());
    }
  }

  private GroupRepresentation createGroup(String orgId, GroupRepresentation group)
      throws HttpClientErrorException, HururaaProblemException {
    organizationsApi
        .adminRealmsRealmOrganizationsOrgIdGroupsPost(
            apiProperties.getRealmName(),
            orgId,
            Optional.of(group));

    // Deliberately un-cached: findGroupByName would return the entry cached by the existence
    // check made before this creation, that is an empty Optional.
    return fetchGroupByName(orgId, group.getName()).orElseThrow(() -> {
      log
          .error(
              "Failed to retrieve group {} for organization {} after creation",
              group.getName(),
              orgId);
      return new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while retrieving group %s for organization %s after creation"
              .formatted(group.getName(), orgId),
          Map.of());
    });
  }

  private GroupRepresentation updateGroup(String orgId, GroupRepresentation group)
      throws HttpClientErrorException {
    organizationsApi
        .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdPut(
            apiProperties.getRealmName(),
            orgId,
            group.getId(),
            Optional.of(group));
    return group;
  }

  @CacheEvict(cacheNames = GROUPS_CACHE, allEntries = true)
  public void deleteGroup(String orgId, String groupId) throws HururaaProblemException {
    try {
      organizationsApi
          .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdDelete(
              apiProperties.getRealmName(),
              orgId,
              groupId);
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to delete group {} for organization {}: {}",
              groupId,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while deleting group %s for organization %s: %s"
              .formatted(groupId, orgId, e.getMessage()),
          Map.of());
    }
  }

  /**
   * @param orgId
   * @param groupId
   * @param pageable page index and size; sort is ignored
   * @return the group's members
   */
  public Page<MemberRepresentation> findGroupMembers(
      String orgId,
      String groupId,
      Pageable pageable) throws HururaaProblemException {
    try {
      final var response = organizationsApi
          .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersGet(
              apiProperties.getRealmName(),
              orgId,
              groupId,
              Optional.empty(), // briefRepresentation
              Optional.of((int) pageable.getOffset()), // first
              Optional.of(pageable.getPageSize())); // max

      final List<MemberRepresentation> members =
          response.getBody() == null ? List.of() : response.getBody();

      return new PageImpl<>(
          members,
          pageable,
          pageable.getOffset() + members.size()
              + (members.size() == pageable.getPageSize() ? 1 : 0));
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to retrieve members of group {} in organization {}: {}",
              groupId,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching members of group %s in organization %s: %s"
              .formatted(groupId, orgId, e.getMessage()),
          Map.of());
    }
  }

  /**
   * @param orgId
   * @param userId
   * @return the organization's groups the user is a member of
   * @throws HururaaProblemException with {@code 404 Not Found} when the user is not a member of the
   *         organization
   */
  public List<GroupRepresentation> findMemberGroups(String orgId, String userId)
      throws HururaaProblemException {
    try {
      final var response = organizationsApi
          .adminRealmsRealmOrganizationsOrgIdMembersMemberIdGroupsGet(
              apiProperties.getRealmName(),
              orgId,
              userId,
              Optional.empty(), // briefRepresentation
              Optional.of(0), // first
              Optional.of(Integer.MAX_VALUE), // max
              Optional.empty()); // search

      return response.getBody() == null ? List.of() : response.getBody();
    } catch (HttpClientErrorException.NotFound e) {
      throw new HururaaProblemException(
          ProblemType.NOT_A_MEMBER,
          "User %s is not a member of organization %s".formatted(userId, orgId),
          Map.of("userId", userId));
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to retrieve groups of user {} in organization {}: {}",
              userId,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching groups of user %s in organization %s: %s"
              .formatted(userId, orgId, e.getMessage()),
          Map.of());
    }
  }

  public void addGroupMember(String orgId, String groupId, String userId)
      throws HururaaProblemException {
    try {
      organizationsApi
          .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersUserIdPut(
              apiProperties.getRealmName(),
              orgId,
              groupId,
              userId);
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to add user {} to group {} in organization {}: {}",
              userId,
              groupId,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while adding user %s to group %s in organization %s: %s"
              .formatted(userId, groupId, orgId, e.getMessage()),
          Map.of());
    }
  }

  public void removeGroupMember(String orgId, String groupId, String userId)
      throws HururaaProblemException {
    try {
      organizationsApi
          .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersUserIdDelete(
              apiProperties.getRealmName(),
              orgId,
              groupId,
              userId);
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to remove user {} from group {} in organization {}: {}",
              userId,
              groupId,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while removing user %s from group %s in organization %s: %s"
              .formatted(userId, groupId, orgId, e.getMessage()),
          Map.of());
    }
  }


  @Cacheable(cacheNames = GROUP_CLIENT_ROLES_CACHE, key = "#clientId + '_' + #groupId")
  public List<RoleRepresentation> findClientRolesByGroupId(String clientId, String orgId, String groupId)
      throws HururaaProblemException {
    try {
      final var groupRoles = clientRoleMappingsApi
          .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdGet(
              apiProperties.getRealmName(),
              orgId,
              groupId,
              clientsService.findUuid(clientId))
          .getBody();
      return groupRoles == null ? List.of() : groupRoles;
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to retrieve client {} roles for group {} of organization {}: {}",
              clientId,
              groupId,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching group's %s roles for client %s: %s"
              .formatted(groupId, clientId, e.getMessage()),
          Map.of());
    }
  }

  /**
   * Idempotent: Keycloak ignores a mapping that already exists.
   *
   * @throws HururaaProblemException with {@code 404 Not Found} when no client role matches
   *         {@code roleName}
   */
  @CacheEvict(cacheNames = GROUP_CLIENT_ROLES_CACHE, key = "#clientId + '_' + #groupId")
  public void addClientRoleToGroup(String clientId, String orgId, String groupId, String roleName)
      throws HururaaProblemException {
    final var role = requireClientRole(clientId, roleName);
    try {
      clientRoleMappingsApi
          .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdPost(
              apiProperties.getRealmName(),
              orgId,
              groupId,
              clientsService.findUuid(clientId),
              Optional.of(List.of(role)));
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to add client {} role {} to group {} of organization {}: {}",
              clientId,
              roleName,
              groupId,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while adding role %s for client %s to group %s: %s"
              .formatted(roleName, clientId, groupId, e.getMessage()),
          Map.of());
    }
  }

  /**
   * Idempotent: does nothing when no client role matches {@code roleName}, or when the group does
   * not hold it.
   */
  @CacheEvict(cacheNames = GROUP_CLIENT_ROLES_CACHE, key = "#clientId + '_' + #groupId")
  public void removeClientRoleFromGroup(String clientId, String orgId, String groupId, String roleName)
      throws HururaaProblemException {
    final var role = findClientRole(clientId, roleName);
    if (role.isEmpty()) {
      return;
    }
    try {
      clientRoleMappingsApi
          .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdDelete(
              apiProperties.getRealmName(),
              orgId,
              groupId,
              clientsService.findUuid(clientId),
              Optional.of(List.of(role.get())));
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to remove client {} role {} from group {} of organization {}: {}",
              clientId,
              roleName,
              groupId,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while removing role %s for client %s from group %s: %s"
              .formatted(roleName, clientId, groupId, e.getMessage()),
          Map.of());
    }
  }

  private Optional<RoleRepresentation> findClientRole(String clientId, String roleName)
      throws HururaaProblemException {
    return rolesService
        .findAllClientRoles(clientId)
        .stream()
        .filter(role -> Objects.equals(roleName, role.getName()))
        .findAny();
  }

  private RoleRepresentation requireClientRole(String clientId, String roleName)
      throws HururaaProblemException {
    return findClientRole(clientId, roleName)
        .orElseThrow(() -> new HururaaProblemException(
            ProblemType.APPLICATION_ROLE_NOT_FOUND,
            "No role named %s for client %s".formatted(roleName, clientId),
            Map.of("clientId", clientId, "role", roleName)));
  }
}
