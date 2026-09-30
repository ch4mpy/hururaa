package pf.hururaa.keycloak;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.keycloak.admin.api.ClientRoleMappingsApi;
import org.keycloak.admin.api.ClientsApi;
import org.keycloak.admin.model.ClientRepresentation;
import org.keycloak.admin.model.RoleRepresentation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.problem.HururaaProblemException;

@Repository
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = {CachingKeycloakClientRepository.CLIENTS_CACHE})
class CachingKeycloakClientRepository {
  static final String CLIENTS_CACHE = "clients";

  private final KeycloakAdminApiProperties apiProperties;

  private final ClientsApi clientsApi;

  private final ClientRoleMappingsApi clientRoleMappingsApi;

  // Used to make internal calls, using the cache
  private final ObjectProvider<CachingKeycloakClientRepository> self;

  public String findUuid(String clientId) throws HururaaProblemException {
    // self.getObject() returns the proxy bean, instrumented with caching.
    final var client = self.getObject().findById(clientId).orElseThrow(() -> {
      log.error("No client with ID {}", clientId);
      return new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Client with id %s not found".formatted(clientId),
          Map.of());
    });
    return client.getId();
  }

  @Cacheable(cacheNames = CLIENTS_CACHE, key = "#clientId")
  public Optional<ClientRepresentation> findById(String clientId) throws HururaaProblemException {
    try {
      final var response = clientsApi
          .adminRealmsRealmClientsGet(
              apiProperties.getRealmName(),
              Optional.of(clientId),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty());

      return response.getBody() == null || response.getBody().isEmpty() ? Optional.empty()
          : Optional.of(response.getBody().get(0));
    } catch (HttpClientErrorException e) {
      log.error("Failed to retrieve client {}: {}", clientId, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching client for %s: %s".formatted(clientId, e.getMessage()),
          Map.of());
    }
  }

  /**
   * Creates a client. Evicts the cached lookup of its ID: it is typically an empty result, cached
   * by the "does it exist?" check made just before.
   *
   * @param client the client to create, with at least its {@code clientId}
   */
  @CacheEvict(cacheNames = CLIENTS_CACHE, key = "#client.clientId")
  public void create(ClientRepresentation client) throws HururaaProblemException {
    try {
      clientsApi.adminRealmsRealmClientsPost(apiProperties.getRealmName(), Optional.of(client));
    } catch (HttpClientErrorException e) {
      log.error("Failed to create client {}: {}", client.getClientId(), e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while creating client %s: %s".formatted(client.getClientId(), e.getMessage()),
          Map.of());
    }
  }

  /**
   * Grants client roles to the service account of a client.
   *
   * @param clientId the ID of the client whose service account is granted the roles
   * @param rolesClientId the ID of the client the roles belong to (e.g. {@code realm-management})
   * @param roles the roles to grant, as returned by the roles endpoints (with their ids)
   */
  public void grantServiceAccountRoles(
      String clientId,
      String rolesClientId,
      List<RoleRepresentation> roles) throws HururaaProblemException {
    try {
      final var serviceAccount = clientsApi
          .adminRealmsRealmClientsClientUuidServiceAccountUserGet(
              apiProperties.getRealmName(),
              findUuid(clientId))
          .getBody();
      if (serviceAccount == null || serviceAccount.getId() == null) {
        throw new HururaaProblemException(
            ProblemType.IDENTITY_PROVIDER_ERROR,
            "Client %s has no service account".formatted(clientId),
            Map.of());
      }
      clientRoleMappingsApi
          .adminRealmsRealmUsersUserIdRoleMappingsClientsClientIdPost(
              apiProperties.getRealmName(),
              serviceAccount.getId(),
              findUuid(rolesClientId),
              Optional.of(roles));
    } catch (HttpClientErrorException e) {
      log.error("Failed to grant {} roles to the service account of {}: {}", rolesClientId,
          clientId, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while granting %s roles to the service account of %s: %s"
              .formatted(rolesClientId, clientId, e.getMessage()),
          Map.of());
    }
  }
}
