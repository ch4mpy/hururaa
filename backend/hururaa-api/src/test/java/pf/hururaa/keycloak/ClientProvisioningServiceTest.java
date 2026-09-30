package pf.hururaa.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.model.ClientRepresentation;
import org.keycloak.admin.model.RoleRepresentation;
import org.mockito.ArgumentCaptor;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;

class ClientProvisioningServiceTest {

  private final CachingKeycloakClientRepository clientRepo =
      mock(CachingKeycloakClientRepository.class);

  private final CachingKeycloakRoleRepository roleRepo = mock(CachingKeycloakRoleRepository.class);

  private final ClientProvisioningService service = new ClientProvisioningService(clientRepo,
      roleRepo, new KeycloakAdminApiProperties("public-facing", "-api", "-bff",
          List.of("view-users", "query-users")));

  @BeforeEach
  void setUp() throws HururaaProblemException {
    when(clientRepo.findById(anyString())).thenReturn(Optional.empty());
    when(roleRepo.findAllClientRoles("realm-management")).thenReturn(List.of(
        new RoleRepresentation().name("view-users"),
        new RoleRepresentation().name("query-users"),
        new RoleRepresentation().name("manage-realm")));
  }

  @Test
  void givenNoClient_whenEnsure_thenBothAreCreatedAndTheServiceAccountGetsTheConfiguredRoles()
      throws HururaaProblemException {
    service.ensureApplicationClients("tautai", "Tautai");

    final var created = ArgumentCaptor.forClass(ClientRepresentation.class);
    verify(clientRepo, org.mockito.Mockito.times(2)).create(created.capture());
    final var bff = created.getAllValues().get(0);
    assertThat(bff.getClientId()).isEqualTo("tautai-bff");
    assertThat(bff.getPublicClient()).isFalse();
    assertThat(bff.getStandardFlowEnabled()).isTrue();
    assertThat(bff.getAttributes()).containsEntry("pkce.code.challenge.method", "S256");
    assertThat(bff.getDefaultClientScopes()).contains("organization");
    final var api = created.getAllValues().get(1);
    assertThat(api.getClientId()).isEqualTo("tautai-api");
    assertThat(api.getServiceAccountsEnabled()).isTrue();
    assertThat(api.getStandardFlowEnabled()).isFalse();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<RoleRepresentation>> roles = ArgumentCaptor.forClass(List.class);
    verify(clientRepo).grantServiceAccountRoles(org.mockito.ArgumentMatchers.eq("tautai-api"),
        org.mockito.ArgumentMatchers.eq("realm-management"), roles.capture());
    assertThat(roles.getValue()).extracting(RoleRepresentation::getName)
        .containsExactlyInAnyOrder("view-users", "query-users");
  }

  @Test
  void givenExistingClients_whenEnsure_thenNothingIsCreated() throws HururaaProblemException {
    when(clientRepo.findById(anyString()))
        .thenReturn(Optional.of(new ClientRepresentation().id("uuid")));

    service.ensureApplicationClients("escales", "Escales");

    verify(clientRepo, never()).create(any());
    verify(clientRepo, never()).grantServiceAccountRoles(anyString(), anyString(), any());
  }

  @Test
  void givenOnlyTheBffClient_whenEnsure_thenOnlyTheApiClientIsCreated()
      throws HururaaProblemException {
    when(clientRepo.findById("tautai-bff"))
        .thenReturn(Optional.of(new ClientRepresentation().id("uuid")));

    service.ensureApplicationClients("tautai", "Tautai");

    final var created = ArgumentCaptor.forClass(ClientRepresentation.class);
    verify(clientRepo).create(created.capture());
    assertThat(created.getValue().getClientId()).isEqualTo("tautai-api");
  }

  @Test
  void givenAConfiguredRoleKeycloakDoesNotHave_whenEnsure_thenIdentityProviderError()
      throws HururaaProblemException {
    when(roleRepo.findAllClientRoles("realm-management"))
        .thenReturn(List.of(new RoleRepresentation().name("view-users")));

    assertThatThrownBy(() -> service.ensureApplicationClients("tautai", "Tautai"))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }
}
