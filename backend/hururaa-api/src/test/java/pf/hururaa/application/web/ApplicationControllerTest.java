package pf.hururaa.application.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.DPAM_ADMIN;
import static pf.hururaa.HururaaFixtures.DSI;
import static pf.hururaa.HururaaFixtures.ESCALES_ID;
import static pf.hururaa.HururaaFixtures.escales;
import static pf.hururaa.HururaaFixtures.teFenua;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.MockMvcSupport;
import pf.hururaa.HururaaFixtures;
import pf.hururaa.application.ApplicationService;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEvent;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.keycloak.ClientProvisioningService;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.problem.ProblemType;

@WebMvcTest(controllers = ApplicationController.class)
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({HururaaFixtures.WebMvcTestConfiguration.class, ApplicationMapperImpl.class,
    ApplicationService.class})
@TestPropertySource(properties = "server.ssl.enabled=false")
class ApplicationControllerTest {

  @Autowired
  MockMvcSupport api;

  @MockitoBean
  ApplicationRepository applicationRepository;

  @MockitoBean
  DirectionAdminRepository directionAdminRepository;

  @MockitoBean
  DirectionService directionService;

  @MockitoBean
  GroupService groupService;

  @MockitoBean
  ClientProvisioningService clientProvisioningService;

  @MockitoBean
  ResourceEventPublisher resourceEvents;

  @BeforeEach
  void setUp() throws Exception {
    when(applicationRepository.findById(ESCALES_ID)).thenReturn(Optional.of(escales()));
    when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> {
      final Application application = invocation.getArgument(0);
      if (application.getId() == null) {
        application.setId(42L);
      }
      return application;
    });
    when(directionService.exists(anyString())).thenReturn(true);
  }

  private static ApplicationCreationRequest anaheiRequest() {
    return new ApplicationCreationRequest("anahei", "Anahei", "daf");
  }

  // ---------- read ----------

  @Test
  @WithAnonymousUser
  void givenAnonymous_whenGetApplications_thenUnauthorized() throws Exception {
    api.get(ApplicationController.BASE_PATH).andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenAnyAuthenticatedUser_whenGetApplications_thenOkWithClientIds() throws Exception {
    when(applicationRepository.findAllByOrderByNameAsc()).thenReturn(List.of(escales()));

    api
        .get(ApplicationController.BASE_PATH)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].clientPrefix").value("escales"))
        .andExpect(jsonPath("$[0].direction").value(DPAM))
        .andExpect(jsonPath("$[0].bffClientId").value("escales-bff"))
        .andExpect(jsonPath("$[0].apiClientId").value("escales-api"));
  }

  private static final String MANAGEABLE = ApplicationController.BASE_PATH + "?manageable=true";

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenPlatformAdmin_whenGetManageableApplications_thenAll() throws Exception {
    when(applicationRepository.findAllByOrderByNameAsc()).thenReturn(List.of(escales(), teFenua()));

    api.get(MANAGEABLE).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenGetManageableApplications_thenThoseOfTheDirection()
      throws Exception {
    when(applicationRepository.findAllByOrderByNameAsc()).thenReturn(List.of(escales(), teFenua()));
    when(directionAdminRepository.existsByDirectionAndUserId(DPAM, DPAM_ADMIN)).thenReturn(true);

    api
        .get(MANAGEABLE)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].clientPrefix").value("escales"));
  }

  @Test
  @WithJwt("jwt/dsi-manager.json")
  void givenApplicationManager_whenGetManageableApplications_thenThoseManaged() throws Exception {
    when(applicationRepository.findAllByOrderByNameAsc()).thenReturn(List.of(escales(), teFenua()));

    api
        .get(MANAGEABLE)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].clientPrefix").value("te-fenua"));
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenMemberWithoutDelegation_whenGetManageableApplications_thenNone() throws Exception {
    when(applicationRepository.findAllByOrderByNameAsc()).thenReturn(List.of(escales(), teFenua()));

    api.get(MANAGEABLE).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenDirectionParam_whenGetApplications_thenFilteredByDirection() throws Exception {
    when(applicationRepository.findByDirectionOrderByNameAsc(DPAM)).thenReturn(List.of(escales()));

    api.get(ApplicationController.BASE_PATH + "?direction=" + DPAM).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    verify(applicationRepository, never()).findAllByOrderByNameAsc();
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenUnknownApplication_whenGetApplication_thenNotFoundProblem() throws Exception {
    api
        .get(ApplicationController.APPLICATION_PATH, 99)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.APPLICATION_NOT_FOUND.uri().toString()));
  }

  // ---------- create ----------

  @Test
  @WithJwt("jwt/dsi-admin.json")
  void givenDirectionAdmin_whenCreateApplication_thenForbidden() throws Exception {
    api.post(anaheiRequest(), ApplicationController.BASE_PATH).andExpect(status().isForbidden());
    verify(applicationRepository, never()).save(any());
  }

  @Test
  @WithJwt("jwt/dpam-sipf-lookalike.json")
  void givenPlatformRolesOutsideThePlatformOrganization_whenCreateApplication_thenForbidden()
      throws Exception {
    api.post(anaheiRequest(), ApplicationController.BASE_PATH).andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenPlatformAdmin_whenCreateApplication_thenCreatedAndEventPublished() throws Exception {
    api
        .post(anaheiRequest(), ApplicationController.BASE_PATH)
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", endsWith("/applications/42")));

    final var event = ArgumentCaptor.forClass(ResourceEvent.class);
    verify(resourceEvents).publish(event.capture());
    assertThat(event.getValue().tenant()).isEqualTo("daf");
    assertThat(event.getValue().eventType()).isEqualTo(EventType.CREATE);
    assertThat(event.getValue().audience()).containsExactly(ResourceEvent.ALL_MEMBERS);
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenTakenPrefix_whenCreateApplication_thenConflict() throws Exception {
    when(applicationRepository.existsByClientPrefix("anahei")).thenReturn(true);

    api
        .post(anaheiRequest(), ApplicationController.BASE_PATH)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(ProblemType.APPLICATION_ALREADY_EXISTS.uri().toString()))
        .andExpect(jsonPath("$.parameters.clientPrefix").value("anahei"));
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenPlatformAdmin_whenCreateApplication_thenItsKeycloakClientsAreProvisioned()
      throws Exception {
    api.post(anaheiRequest(), ApplicationController.BASE_PATH).andExpect(status().isCreated());

    verify(clientProvisioningService).ensureApplicationClients("anahei", "Anahei");
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenUnknownDirection_whenCreateApplication_thenNotFound() throws Exception {
    when(directionService.exists("daf")).thenReturn(false);

    api
        .post(anaheiRequest(), ApplicationController.BASE_PATH)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.DIRECTION_NOT_FOUND.uri().toString()));
    // nothing is created in Keycloak for an application that can't be registered
    verifyNoInteractions(clientProvisioningService);
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenInvalidPrefix_whenCreateApplication_thenUnprocessable() throws Exception {
    api
        .post(new ApplicationCreationRequest("Not A Prefix", "X", "daf"),
            ApplicationController.BASE_PATH)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.clientPrefix").exists());
  }

  // ---------- update ----------

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenPlatformAdmin_whenMovingApplication_thenManagersDroppedAndBothDirectionsNotified()
      throws Exception {
    when(groupService.findAll(DPAM)).thenReturn(List.of(new Group("g1", DPAM, "escales-agents")));
    when(groupService.findClientRoles(DPAM, "escales-agents", "escales-api"))
        .thenReturn(List.of());

    api
        .put(new ApplicationUpdateRequest("Escales", DSI), ApplicationController.APPLICATION_PATH,
            ESCALES_ID)
        .andExpect(status().isNoContent());

    final var saved = ArgumentCaptor.forClass(Application.class);
    verify(applicationRepository).save(saved.capture());
    assertThat(saved.getValue().getDirection()).isEqualTo(DSI);
    assertThat(saved.getValue().getManagers()).isEmpty();
    final var events = ArgumentCaptor.forClass(ResourceEvent.class);
    verify(resourceEvents, org.mockito.Mockito.times(2)).publish(events.capture());
    assertThat(events.getAllValues()).extracting(ResourceEvent::tenant).containsExactly(DPAM, DSI);
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenRolesStillGranted_whenMovingApplication_thenConflict() throws Exception {
    when(groupService.findAll(DPAM)).thenReturn(List.of(new Group("g1", DPAM, "escales-agents")));
    when(groupService.findClientRoles(DPAM, "escales-agents", "escales-api"))
        .thenReturn(List.of("escales.stopovers.read"));

    api
        .put(new ApplicationUpdateRequest("Escales", DSI), ApplicationController.APPLICATION_PATH,
            ESCALES_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type")
            .value(ProblemType.APPLICATION_ROLES_STILL_GRANTED.uri().toString()))
        .andExpect(jsonPath("$.parameters.groups").value("escales-agents"));
    verify(applicationRepository, never()).save(any());
    verifyNoInteractions(resourceEvents);
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenSameDirection_whenRenamingApplication_thenGrantsAreNotChecked() throws Exception {
    api
        .put(new ApplicationUpdateRequest("Escales 2", DPAM),
            ApplicationController.APPLICATION_PATH, ESCALES_ID)
        .andExpect(status().isNoContent());
    verify(groupService, never()).findAll(eq(DPAM));
  }

  // ---------- delete ----------

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenDeleteApplication_thenForbidden() throws Exception {
    api.delete(ApplicationController.APPLICATION_PATH, ESCALES_ID)
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenPlatformAdmin_whenDeleteUngrantedApplication_thenNoContent() throws Exception {
    when(groupService.findAll(DPAM)).thenReturn(List.of());

    api.delete(ApplicationController.APPLICATION_PATH, ESCALES_ID)
        .andExpect(status().isNoContent());
    verify(applicationRepository).delete(any(Application.class));
  }
}
