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
import org.keycloak.admin.api.RolesApi;
import org.keycloak.admin.model.ClientRepresentation;
import org.keycloak.admin.model.RoleRepresentation;
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
@SpringJUnitConfig(classes = CachingKeycloakRoleRepositoryTest.TestConfig.class)
class CachingKeycloakRoleRepositoryTest {

  static final String REALM = "hururaa";
  static final String CLIENT_ID = "hururaa-api";
  static final String CLIENT_UUID = "client-uuid-1";

  @Configuration
  @ImportAutoConfiguration(CacheAutoConfiguration.class)
  @Import({CacheConfiguration.class, CachingKeycloakClientRepository.class, CachingKeycloakRoleRepository.class})
  static class TestConfig {
    @Bean
    KeycloakAdminApiProperties keycloakAdminApiProperties() {
      return new KeycloakAdminApiProperties(REALM, "-api", "-bff", List.of("view-users"));
    }
  }

  @MockitoBean
  ClientsApi clientsApi;

  /** Needed by {@link CachingKeycloakClientRepository}, unused here. */
  @MockitoBean
  ClientRoleMappingsApi clientRoleMappingsApi;

  @MockitoBean
  RolesApi rolesApi;

  @Autowired
  CachingKeycloakRoleRepository clientRolesService;

  @Autowired
  CacheManager cacheManager;

  @BeforeEach
  void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  private void stubClientUuidLookup() {
    when(
        clientsApi.adminRealmsRealmClientsGet(
            REALM,
            Optional.of(CLIENT_ID),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()))
        .thenReturn(ResponseEntity.ok(List.of(new ClientRepresentation().id(CLIENT_UUID).clientId(CLIENT_ID))));
  }

  private void stubExistingRoles(RoleRepresentation... roles) {
    when(
        rolesApi.adminRealmsRealmClientsClientUuidRolesGet(
            REALM,
            CLIENT_UUID,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenReturn(ResponseEntity.ok(List.of(roles)));
  }


  @Test
  void findByClientIdReturnsRolesAndCachesResult() throws HururaaProblemException {
    stubClientUuidLookup();
    final var role = new RoleRepresentation().id("role-uuid-1").clientRole(true).name("ADMIN");
    stubExistingRoles(role);

    final var first = clientRolesService.findAllClientRoles(CLIENT_ID);
    final var second = clientRolesService.findAllClientRoles(CLIENT_ID);

    assertThat(first).containsExactly(role);
    assertThat(second).containsExactly(role);
    verify(rolesApi, times(1))
        .adminRealmsRealmClientsClientUuidRolesGet(
            REALM,
            CLIENT_UUID,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
  }

  @Test
  void findByClientIdReturnsEmptyListWhenBodyIsNull() throws HururaaProblemException {
    stubClientUuidLookup();
    when(
        rolesApi.adminRealmsRealmClientsClientUuidRolesGet(
            REALM,
            CLIENT_UUID,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenReturn(ResponseEntity.ok(null));

    assertThat(clientRolesService.findAllClientRoles(CLIENT_ID)).isEmpty();
  }

  @Test
  void findByClientIdWrapsHttpClientErrorExceptionAs500() {
    stubClientUuidLookup();
    when(
        rolesApi.adminRealmsRealmClientsClientUuidRolesGet(
            REALM,
            CLIENT_UUID,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> clientRolesService.findAllClientRoles(CLIENT_ID))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  @Test
  void saveDoesNothingWhenRoleAlreadyExists() throws HururaaProblemException {
    stubClientUuidLookup();
    final var existing = new RoleRepresentation().id("role-uuid-1").clientRole(true).name("ADMIN");
    stubExistingRoles(existing);

    final var result = clientRolesService.saveClientRole(CLIENT_ID, "ADMIN", null);

    assertThat(result).containsExactly(existing);
    verify(rolesApi, never())
        .adminRealmsRealmClientsClientUuidRolesPost(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void saveCreatesRoleWhenAbsentAndUpdatesCache() throws HururaaProblemException {
    stubClientUuidLookup();
    final var existing = new RoleRepresentation().id("role-uuid-1").clientRole(true).name("ADMIN");
    stubExistingRoles(existing);

    final var newRoleDto = new RoleRepresentation().clientRole(true).name("EDITOR");
    when(rolesApi.adminRealmsRealmClientsClientUuidRolesPost(REALM, CLIENT_UUID, Optional.of(newRoleDto)))
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    final var createdRole = new RoleRepresentation().id("role-uuid-2").clientRole(true).name("EDITOR");
    when(rolesApi.adminRealmsRealmClientsClientUuidRolesRoleNameGet(REALM, CLIENT_UUID, "EDITOR"))
        .thenReturn(ResponseEntity.ok(createdRole));

    final var result = clientRolesService.saveClientRole(CLIENT_ID, "EDITOR", null);

    assertThat(result).containsExactlyInAnyOrder(existing, createdRole);

    // the roles-by-client cache should now reflect the created role, without a further roles GET
    final var cached = clientRolesService.findAllClientRoles(CLIENT_ID);
    assertThat(cached).containsExactlyInAnyOrder(existing, createdRole);
    verify(rolesApi, times(1))
        .adminRealmsRealmClientsClientUuidRolesGet(
            REALM,
            CLIENT_UUID,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
  }

  @Test
  void saveThrows500WhenCreatedRoleCannotBeRetrieved() {
    stubClientUuidLookup();
    stubExistingRoles();

    final var newRoleDto = new RoleRepresentation().clientRole(true).name("EDITOR");
    when(rolesApi.adminRealmsRealmClientsClientUuidRolesPost(REALM, CLIENT_UUID, Optional.of(newRoleDto)))
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    when(rolesApi.adminRealmsRealmClientsClientUuidRolesRoleNameGet(REALM, CLIENT_UUID, "EDITOR"))
        .thenReturn(ResponseEntity.ok(null));

    assertThatThrownBy(() -> clientRolesService.saveClientRole(CLIENT_ID, "EDITOR", null))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  @Test
  void saveWrapsHttpClientErrorExceptionAs500() {
    stubClientUuidLookup();
    stubExistingRoles();
    final var newRoleDto = new RoleRepresentation().clientRole(true).name("EDITOR");
    when(rolesApi.adminRealmsRealmClientsClientUuidRolesPost(REALM, CLIENT_UUID, Optional.of(newRoleDto)))
        .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> clientRolesService.saveClientRole(CLIENT_ID, "EDITOR", null))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  @Test
  void deleteDoesNothingWhenRoleIsAbsent() throws HururaaProblemException {
    stubClientUuidLookup();
    final var existing = new RoleRepresentation().id("role-uuid-1").clientRole(true).name("ADMIN");
    stubExistingRoles(existing);

    final var result = clientRolesService.deleteClientRole(CLIENT_ID, "EDITOR");

    assertThat(result).containsExactly(existing);
    verify(rolesApi, never())
        .adminRealmsRealmClientsClientUuidRolesRoleNameDelete(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deleteRemovesRoleAndUpdatesCache() throws HururaaProblemException {
    stubClientUuidLookup();
    final var admin = new RoleRepresentation().id("role-uuid-1").clientRole(true).name("ADMIN");
    final var editor = new RoleRepresentation().id("role-uuid-2").clientRole(true).name("EDITOR");
    stubExistingRoles(admin, editor);

    final var result = clientRolesService.deleteClientRole(CLIENT_ID, "EDITOR");

    assertThat(result).containsExactly(admin);
    verify(rolesApi, times(1)).adminRealmsRealmClientsClientUuidRolesRoleNameDelete(REALM, CLIENT_UUID, "EDITOR");

    final var cached = clientRolesService.findAllClientRoles(CLIENT_ID);
    assertThat(cached).containsExactly(admin);
    verify(rolesApi, times(1))
        .adminRealmsRealmClientsClientUuidRolesGet(
            REALM,
            CLIENT_UUID,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
  }

  @Test
  void deleteWrapsHttpClientErrorExceptionAs500() {
    stubClientUuidLookup();
    final var editor = new RoleRepresentation().id("role-uuid-2").clientRole(true).name("EDITOR");
    stubExistingRoles(editor);
    when(rolesApi.adminRealmsRealmClientsClientUuidRolesRoleNameDelete(REALM, CLIENT_UUID, "EDITOR"))
        .thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> clientRolesService.deleteClientRole(CLIENT_ID, "EDITOR"))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }










}
