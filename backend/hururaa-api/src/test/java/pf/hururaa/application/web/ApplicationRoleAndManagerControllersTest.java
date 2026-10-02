package pf.hururaa.application.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.DSI;
import static pf.hururaa.HururaaFixtures.DPAM_AGENT;
import static pf.hururaa.HururaaFixtures.DPAM_MANAGER;
import static pf.hururaa.HururaaFixtures.ESCALES_ID;
import static pf.hururaa.HururaaFixtures.escales;
import static pf.hururaa.HururaaFixtures.stubDevDelegations;
import static pf.hururaa.HururaaFixtures.user;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.MockMvcSupport;
import pf.hururaa.HururaaFixtures;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.domain.ApplicationRole;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.direction.web.DirectoryMapperImpl;
import pf.hururaa.keycloak.ClientRoleService;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;

/**
 * Levels 2 and 3 of the delegation chain on an application: its direction's administrators
 * designate its managers, who define its roles.
 */
@WebMvcTest(controllers = {ApplicationRoleController.class, ApplicationManagerController.class})
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({HururaaFixtures.WebMvcTestConfiguration.class, ApplicationMapperImpl.class,
    DirectoryMapperImpl.class})
@TestPropertySource(properties = "server.ssl.enabled=false")
class ApplicationRoleAndManagerControllersTest {

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
  ClientRoleService clientRoleService;

  @MockitoBean
  ResourceEventPublisher resourceEvents;

  @BeforeEach
  void setUp() throws Exception {
    stubDevDelegations(directionService, directionAdminRepository, applicationRepository);
    when(applicationRepository.findById(ESCALES_ID)).thenReturn(Optional.of(escales()));
  }

  // ---------- roles ----------

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenMemberWithoutDelegation_whenGetRoles_thenForbidden() throws Exception {
    api
        .get(ApplicationRoleController.BASE_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenGetRoles_thenOk() throws Exception {
    when(clientRoleService.findAll("escales-api"))
        .thenReturn(List.of(new ApplicationRole("escales.stopovers.read", "Consulter")));

    api
        .get(ApplicationRoleController.BASE_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("escales.stopovers.read"))
        .andExpect(jsonPath("$[0].description").value("Consulter"));
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenGetRoles_thenOk() throws Exception {
    api.get(ApplicationRoleController.BASE_PATH, DPAM, ESCALES_ID).andExpect(status().isOk());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenCreateRole_thenCreated() throws Exception {
    api
        .post(new ApplicationRoleRequest("escales.stopovers.delete", null),
            ApplicationRoleController.BASE_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isCreated());
    verify(clientRoleService).save("escales-api", "escales.stopovers.delete", null);
  }

  @Test
  @WithJwt("jwt/dsi-manager.json")
  void givenManagerOfAnotherApplication_whenCreateRole_thenForbidden() throws Exception {
    api
        .post(new ApplicationRoleRequest("escales.stopovers.delete", null),
            ApplicationRoleController.BASE_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenCreateRole_thenCreatedOnTheApiClient() throws Exception {
    api
        .post(new ApplicationRoleRequest("escales.stopovers.delete", "Supprimer une escale"),
            ApplicationRoleController.BASE_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isCreated())
        .andExpect(header().string("Location",
            endsWith("/directions/dpam/applications/3/roles/escales.stopovers.delete")));
    verify(clientRoleService)
        .save("escales-api", "escales.stopovers.delete", "Supprimer une escale");
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenInvalidRoleName_whenCreateRole_thenUnprocessable() throws Exception {
    api
        .post(new ApplicationRoleRequest("Not a role", null), ApplicationRoleController.BASE_PATH,
            DPAM, ESCALES_ID)
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenDeleteRole_thenNoContent() throws Exception {
    api
        .delete(ApplicationRoleController.ROLE_PATH, DPAM, ESCALES_ID, "escales.stopovers.read")
        .andExpect(status().isNoContent());
    verify(clientRoleService).delete("escales-api", "escales.stopovers.read");
  }

  // ---------- managers ----------

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenGetManagers_thenOkWithFormerMembersListedById()
      throws Exception {
    final var application = escales();
    application.getManagers().add("former-member");
    when(applicationRepository.findById(ESCALES_ID)).thenReturn(Optional.of(application));
    when(applicationRepository.findByDirectionOrderByNameAsc(DPAM))
        .thenReturn(List.of(application));
    when(directionService.findMember(DPAM, DPAM_MANAGER))
        .thenReturn(Optional.of(user(DPAM_MANAGER, "dpam.manager")));
    when(directionService.findMember(DPAM, "former-member")).thenReturn(Optional.empty());

    api
        .get(ApplicationManagerController.BASE_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("dpam.manager"))
        .andExpect(jsonPath("$[1].username").value("former-member"));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenAddCoManager_thenSaved() throws Exception {
    when(directionService.requireMember(DPAM, DPAM_AGENT))
        .thenReturn(user(DPAM_AGENT, "dpam.agent"));

    api
        .put(Map.of(), ApplicationManagerController.MANAGER_PATH, DPAM, ESCALES_ID, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verify(applicationRepository).save(any(Application.class));
  }

  @Test
  @WithJwt("jwt/dsi-manager.json")
  void givenManagerOfAnotherApplication_whenAddManager_thenForbidden() throws Exception {
    api
        .put(Map.of(), ApplicationManagerController.MANAGER_PATH, DPAM, ESCALES_ID, DPAM_AGENT)
        .andExpect(status().isForbidden());
    verify(applicationRepository, never()).save(any());
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenAddManager_thenSaved() throws Exception {
    when(directionService.requireMember(DPAM, DPAM_AGENT))
        .thenReturn(user(DPAM_AGENT, "dpam.agent"));

    api
        .put(Map.of(), ApplicationManagerController.MANAGER_PATH, DPAM, ESCALES_ID, DPAM_AGENT)
        .andExpect(status().isNoContent());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenAddMemberAsManager_thenSaved() throws Exception {
    when(directionService.requireMember(DPAM, DPAM_AGENT))
        .thenReturn(user(DPAM_AGENT, "dpam.agent"));

    api
        .put(Map.of(), ApplicationManagerController.MANAGER_PATH, DPAM, ESCALES_ID, DPAM_AGENT)
        .andExpect(status().isNoContent());

    final var saved = ArgumentCaptor.forClass(Application.class);
    verify(applicationRepository).save(saved.capture());
    assertThat(saved.getValue().getManagers()).containsExactlyInAnyOrder(DPAM_MANAGER, DPAM_AGENT);
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenAddNonMemberAsManager_thenNotFound() throws Exception {
    when(directionService.requireMember(DPAM, "outsider"))
        .thenThrow(new HururaaProblemException(ProblemType.NOT_A_MEMBER, "not a member",
            Map.of("userId", "outsider")));

    api
        .put(Map.of(), ApplicationManagerController.MANAGER_PATH, DPAM, ESCALES_ID, "outsider")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.NOT_A_MEMBER.uri().toString()));
    verify(applicationRepository, never()).save(any());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenRemoveManager_thenSaved() throws Exception {
    api
        .delete(ApplicationManagerController.MANAGER_PATH, DPAM, ESCALES_ID, DPAM_MANAGER)
        .andExpect(status().isNoContent());

    final var saved = ArgumentCaptor.forClass(Application.class);
    verify(applicationRepository).save(saved.capture());
    assertThat(saved.getValue().getManagers()).isEmpty();
  }

  @Test
  @WithJwt("jwt/dsi-admin.json")
  void givenAdminOfAnotherDirection_whenAddManagerUnderTheirDirection_thenForbidden()
      throws Exception {
    // Escales is managed by dpam: addressing it under dsi grants dsi's administrator nothing
    api
        .put(Map.of(), ApplicationManagerController.MANAGER_PATH, DSI, ESCALES_ID, DPAM_AGENT)
        .andExpect(status().isForbidden());
    verify(applicationRepository, never()).save(any());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenUnknownDirection_whenGetRoles_thenNotFound() throws Exception {
    api
        .get(ApplicationRoleController.BASE_PATH, "no-such-direction", ESCALES_ID)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.DIRECTION_NOT_FOUND.uri().toString()));
  }
}
