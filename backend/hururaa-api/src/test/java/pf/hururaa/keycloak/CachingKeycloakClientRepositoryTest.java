package pf.hururaa.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.api.ClientRoleMappingsApi;
import org.keycloak.admin.api.ClientsApi;
import org.keycloak.admin.model.ClientRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.HttpClientErrorException;

import pf.hururaa.CacheConfiguration;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.problem.HururaaProblemException;

@TestPropertySource(properties = "spring.cache.type=caffeine")
@SpringJUnitConfig(classes = CachingKeycloakClientRepositoryTest.TestConfig.class)
class CachingKeycloakClientRepositoryTest {

  static final String REALM = "hururaa";

  @Configuration
  @ImportAutoConfiguration(CacheAutoConfiguration.class)
  @Import({CacheConfiguration.class, CachingKeycloakClientRepository.class})
  static class TestConfig {
    @Bean
    KeycloakAdminApiProperties keycloakAdminApiProperties() {
      return new KeycloakAdminApiProperties(REALM, "-api", "-bff", List.of("view-users"));
    }
  }

  @MockitoBean
  ClientsApi clientsApi;

  @MockitoBean
  ClientRoleMappingsApi clientRoleMappingsApi;

  @Autowired
  CachingKeycloakClientRepository clientService;

  @Autowired
  CacheManager cacheManager;

  @BeforeEach
  void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  @Test
  void findByIdReturnsClientWhenPresentAndCachesResult() throws HururaaProblemException {
    final var client = new ClientRepresentation().id("client-uuid-1").clientId("hururaa-api");
    when(
        clientsApi.adminRealmsRealmClientsGet(
            REALM,
            Optional.of("hururaa-api"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenReturn(ResponseEntity.ok(List.of(client)));

    final var first = clientService.findById("hururaa-api");
    final var second = clientService.findById("hururaa-api");

    assertThat(first).contains(client);
    assertThat(second).contains(client);
    verify(clientsApi, times(1))
        .adminRealmsRealmClientsGet(
            REALM,
            Optional.of("hururaa-api"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
  }

  @Test
  void findByIdReturnsEmptyWhenBodyIsEmptyList() throws HururaaProblemException {
    when(
        clientsApi.adminRealmsRealmClientsGet(
            REALM,
            Optional.of("unknown"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenReturn(ResponseEntity.ok(List.of()));

    assertThat(clientService.findById("unknown")).isEmpty();
  }

  @Test
  void findByIdReturnsEmptyWhenBodyIsNull() throws HururaaProblemException {
    when(
        clientsApi.adminRealmsRealmClientsGet(
            REALM,
            Optional.of("unknown"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenReturn(ResponseEntity.ok(null));

    assertThat(clientService.findById("unknown")).isEmpty();
  }

  @Test
  void findByIdWrapsHttpClientErrorExceptionAs500() {
    when(
        clientsApi.adminRealmsRealmClientsGet(
            REALM,
            Optional.of("hururaa-api"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> clientService.findById("hururaa-api"))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  @Test
  void findUuidReturnsClientIdWhenClientExists() throws HururaaProblemException {
    final var client = new ClientRepresentation().id("client-uuid-1").clientId("hururaa-api");
    when(
        clientsApi.adminRealmsRealmClientsGet(
            REALM,
            Optional.of("hururaa-api"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenReturn(ResponseEntity.ok(List.of(client)));

    assertThat(clientService.findUuid("hururaa-api")).isEqualTo("client-uuid-1");
  }

  @Test
  void findUuidThrowsIdentityProviderErrorWhenClientIsMissing() {
    when(
        clientsApi.adminRealmsRealmClientsGet(
            REALM,
            Optional.of("unknown"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenReturn(ResponseEntity.ok(List.of()));

    assertThatThrownBy(() -> clientService.findUuid("unknown"))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
    verify(clientsApi, never())
        .adminRealmsRealmClientsGet(
            REALM,
            Optional.of("hururaa-api"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
  }

  @Test
  void createEvictsTheCachedAbsenceOfTheClient() throws HururaaProblemException {
    when(clientsApi.adminRealmsRealmClientsGet(REALM, Optional.of("tautai-api"), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
        .thenReturn(ResponseEntity.ok(List.of()))
        .thenReturn(ResponseEntity.ok(
            List.of(new ClientRepresentation().id("uuid-tautai").clientId("tautai-api"))));

    assertThat(clientService.findById("tautai-api")).isEmpty();
    clientService.create(new ClientRepresentation().clientId("tautai-api"));

    assertThat(clientService.findById("tautai-api")).map(ClientRepresentation::getId)
        .contains("uuid-tautai");
    verify(clientsApi).adminRealmsRealmClientsPost(org.mockito.ArgumentMatchers.eq(REALM),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void grantServiceAccountRolesMapsTheRolesOnTheServiceAccountUser()
      throws HururaaProblemException {
    for (final var client : List.of(
        new ClientRepresentation().id("uuid-tautai").clientId("tautai-api"),
        new ClientRepresentation().id("uuid-rm").clientId("realm-management"))) {
      when(clientsApi.adminRealmsRealmClientsGet(REALM, Optional.of(client.getClientId()),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty())).thenReturn(ResponseEntity.ok(List.of(client)));
    }
    when(clientsApi.adminRealmsRealmClientsClientUuidServiceAccountUserGet(REALM, "uuid-tautai"))
        .thenReturn(ResponseEntity.ok(new org.keycloak.admin.model.UserRepresentation().id("sa")));
    final var roles = List.of(new org.keycloak.admin.model.RoleRepresentation().name("view-users"));

    clientService.grantServiceAccountRoles("tautai-api", "realm-management", roles);

    verify(clientRoleMappingsApi)
        .adminRealmsRealmUsersUserIdRoleMappingsClientsClientIdPost(REALM, "sa", "uuid-rm",
            Optional.of(roles));
  }
}
