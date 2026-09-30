package pf.hururaa.keycloak;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.keycloak.admin.api.RolesApi;
import org.keycloak.admin.model.RoleRepresentation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.problem.HururaaProblemException;

/**
 * Facade in front of Keycloak's {@link RolesApi} that provides:
 * <ul>
 * <li>caching</li>
 * <li>Mapping of the {@link org.springframework.web.client.RestClient RestClient's}
 * {@link HttpClientErrorException} to a checked {@link HururaaProblemException} ({@link
 * ProblemType#IDENTITY_PROVIDER_ERROR}, or a business type where a {@code 404} has one)</li>
 * </ul>
 *
 * <p>
 * Client roles only: application roles are the client roles of the applications' {@code <prefix>-api}
 * clients, realm roles play no part in the model.
 * </p>
 *
 * @see ClientRoleService ClientRoleService for a public, domain-oriented facade
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = {CachingKeycloakRoleRepository.CLIENT_ROLES_CACHE})
class CachingKeycloakRoleRepository {
  static final String CLIENT_ROLES_CACHE = "clientRoles";

  private final KeycloakAdminApiProperties apiProperties;

  private final RolesApi rolesApi;

  private final CachingKeycloakClientRepository clientService;

  // Used to make internal calls, using the cache
  private final ObjectProvider<CachingKeycloakRoleRepository> self;

  @Cacheable(cacheNames = CLIENT_ROLES_CACHE, key = "#clientId")
  public List<RoleRepresentation> findAllClientRoles(String clientId)
      throws HururaaProblemException {
    try {
      final var clientRoles = rolesApi
          .adminRealmsRealmClientsClientUuidRolesGet(
              apiProperties.getRealmName(),
              clientService.findUuid(clientId),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty())
          .getBody();

      return clientRoles == null ? List.of() : clientRoles;

    } catch (HttpClientErrorException e) {
      log.error("Failed to retrieve roles for client {} from Keycloak: {}", clientId, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching roles for client %s: %s".formatted(clientId, e.getMessage()),
          Map.of());
    }
  }

  @CachePut(cacheNames = CLIENT_ROLES_CACHE, key = "#clientId")
  public List<RoleRepresentation> saveClientRole(
      String clientId,
      String roleName,
      @Nullable String description)
      throws HururaaProblemException {
    try {
      // self.getObject() returns the proxy bean, instrumented with caching.
      final var currentRoles = self.getObject().findAllClientRoles(clientId);
      if (currentRoles.stream().anyMatch(role -> Objects.equals(roleName, role.getName()))) {
        log.warn("Role {} already exists for client {}", roleName, clientId);
        return currentRoles;
      }

      final var dto = new RoleRepresentation();
      dto.clientRole(true);
      dto.name(roleName);
      dto.description(description);
      rolesApi
          .adminRealmsRealmClientsClientUuidRolesPost(
              apiProperties.getRealmName(),
              clientService.findUuid(clientId),
              Optional.of(dto));

      final var createdRole = rolesApi
          .adminRealmsRealmClientsClientUuidRolesRoleNameGet(
              apiProperties.getRealmName(),
              clientService.findUuid(clientId),
              roleName)
          .getBody();
      if (createdRole == null) {
        log.error("Failed to retrieve role {} for client {} after creation", roleName, clientId);
        throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching role %s for client %s after creation"
                .formatted(roleName, clientId),
          Map.of());
      }

      return Stream.concat(currentRoles.stream(), Stream.of(createdRole)).toList();

    } catch (HttpClientErrorException e) {
      log.error("Failed to save role {} for client {}: {}", roleName, clientId, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while saving role %s for client %s: %s"
              .formatted(roleName, clientId, e.getMessage()),
          Map.of());
    }
  }

  // Keycloak drops the role's group mappings along with it: the cached group roles are stale too
  @Caching(
      put = @CachePut(cacheNames = CLIENT_ROLES_CACHE, key = "#clientId"),
      evict = @CacheEvict(
          cacheNames = CachingKeycloakOrganizationGroupRepository.GROUP_CLIENT_ROLES_CACHE,
          allEntries = true))
  public List<RoleRepresentation> deleteClientRole(String clientId, String roleName)
      throws HururaaProblemException {
    try {
      // self.getObject() returns the proxy bean, instrumented with caching.
      final var currentRoles = self.getObject().findAllClientRoles(clientId);
      if (currentRoles.stream().noneMatch(role -> Objects.equals(roleName, role.getName()))) {
        log.info("Role {} does not exist for client {}. Doing nothing", roleName, clientId);
        return currentRoles;
      }

      rolesApi
          .adminRealmsRealmClientsClientUuidRolesRoleNameDelete(
              apiProperties.getRealmName(),
              clientService.findUuid(clientId),
              roleName);

      return currentRoles
          .stream()
          .filter(role -> !Objects.equals(roleName, role.getName()))
          .toList();

    } catch (HttpClientErrorException e) {
      log.error("Failed to delete role {} for client {}: {}", roleName, clientId, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while deleting role %s for client %s: %s"
              .formatted(roleName, clientId, e.getMessage()),
          Map.of());
    }
  }
}
