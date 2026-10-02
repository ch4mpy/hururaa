package pf.hururaa.direction.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.DPAM_AGENT;
import static pf.hururaa.HururaaFixtures.DPAM_MANAGER;
import static pf.hururaa.HururaaFixtures.DSI;
import static pf.hururaa.HururaaFixtures.ESCALES_ID;
import static pf.hururaa.HururaaFixtures.TE_FENUA_ID;
import static pf.hururaa.HururaaFixtures.escales;
import static pf.hururaa.HururaaFixtures.stubDevDelegations;
import static pf.hururaa.HururaaFixtures.teFenua;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;

/**
 * Level 3 of the delegation chain on a direction's groups: only managers of the direction's
 * applications act on them, and only for the applications they manage.
 */
@WebMvcTest(controllers = GroupController.class)
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({HururaaFixtures.WebMvcTestConfiguration.class, DirectoryMapperImpl.class})
@TestPropertySource(properties = "server.ssl.enabled=false")
class GroupControllerTest {

  private static final String GROUP = "escales-agents";

  /** A second application of dpam, which dpam.manager does not manage. */
  private static Application pgc() {
    return Application
        .builder()
        .id(7L)
        .clientPrefix("pgc")
        .name("PGC")
        .direction(DPAM)
        .managers(new HashSet<>(Set.of("someone-else")))
        .build();
  }

  @Autowired
  MockMvcSupport api;

  @MockitoBean
  ApplicationRepository applicationRepository;

  @MockitoBean
  DirectionAdminRepository directionAdminRepository;

  @MockitoBean
  GroupService groupService;

  @MockitoBean
  DirectionService directionService;

  @MockitoBean
  ResourceEventPublisher resourceEvents;

  @BeforeEach
  void setUp() throws Exception {
    stubDevDelegations(directionService, directionAdminRepository, applicationRepository);
    when(applicationRepository.findById(ESCALES_ID)).thenReturn(Optional.of(escales()));
    when(applicationRepository.findById(TE_FENUA_ID)).thenReturn(Optional.of(teFenua()));
    when(applicationRepository.findById(7L)).thenReturn(Optional.of(pgc()));
    when(applicationRepository.findByDirectionOrderByNameAsc(DPAM))
        .thenReturn(List.of(escales(), pgc()));
    when(groupService.findByName(DPAM, GROUP))
        .thenReturn(Optional.of(new Group("g1", DPAM, GROUP)));
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenMemberWithoutDelegation_whenGetGroups_thenForbidden() throws Exception {
    api.get(GroupController.BASE_PATH, DPAM).andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenGetGroupRoles_thenRolesOfTheDirectionsApplications()
      throws Exception {
    when(groupService.findClientRoles(DPAM, GROUP, "escales-api"))
        .thenReturn(List.of("escales.stopovers.read"));
    when(groupService.findClientRoles(DPAM, GROUP, "pgc-api")).thenReturn(List.of());

    api
        .get(GroupController.ROLES_PATH, DPAM, GROUP)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].applicationId").value(ESCALES_ID))
        .andExpect(jsonPath("$[0].clientId").value("escales-api"))
        .andExpect(jsonPath("$[0].role").value("escales.stopovers.read"));
  }

  @Test
  @WithJwt("jwt/dsi-manager.json")
  void givenManagerInAnotherDirection_whenCreateGroup_thenForbidden() throws Exception {
    api.post(new GroupRequest("new-group"), GroupController.BASE_PATH, DPAM)
        .andExpect(status().isForbidden());
    verify(groupService, never()).save(anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenCreateGroup_thenCreated() throws Exception {
    when(groupService.save(DPAM, "new-group")).thenReturn(new Group("g2", DPAM, "new-group"));

    api.post(new GroupRequest("new-group"), GroupController.BASE_PATH, DPAM)
        .andExpect(status().isCreated());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfTheApplication_whenGrantItsRole_thenNoContent() throws Exception {
    api.put(Map.of(), GroupController.ROLE_PATH, DPAM, GROUP, ESCALES_ID, "escales.stopovers.read")
        .andExpect(status().isNoContent());
    verify(groupService).addClientRole(DPAM, GROUP, "escales-api", "escales.stopovers.read");
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfAnotherApplication_whenGrantItsRole_thenForbidden() throws Exception {
    api.put(Map.of(), GroupController.ROLE_PATH, DPAM, GROUP, 7L, "pgc.read")
        .andExpect(status().isForbidden());
    verify(groupService, never()).addClientRole(anyString(), anyString(), anyString(),
        anyString());
  }

  @Test
  @WithJwt("jwt/dsi-manager.json")
  void givenApplicationOfAnotherDirection_whenGrantItsRole_thenConflict() throws Exception {
    // dsi.manager manages Te Fenua, which dsi manages: its roles can't be granted by dpam's groups
    api.put(Map.of(), GroupController.ROLE_PATH, DPAM, GROUP, TE_FENUA_ID, "te-fenua.read")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(ProblemType.APPLICATION_NOT_IN_DIRECTION.uri().toString()))
        .andExpect(jsonPath("$.parameters.direction").value(DPAM));
    verify(groupService, never()).addClientRole(anyString(), anyString(), anyString(),
        anyString());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenGroupGrantingOnlyManagedApplicationsRoles_whenAddMember_thenNoContent()
      throws Exception {
    when(groupService.findClientRoles(DPAM, GROUP, "pgc-api")).thenReturn(List.of());

    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, GROUP, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verify(groupService).addMember(DPAM, GROUP, DPAM_AGENT);
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenGroupGrantingRolesOfAnUnmanagedApplication_whenAddMember_thenForbidden()
      throws Exception {
    when(groupService.findClientRoles(DPAM, GROUP, "pgc-api")).thenReturn(List.of("pgc.read"));

    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, GROUP, DPAM_AGENT)
        .andExpect(status().isForbidden());
    verify(groupService, never()).addMember(anyString(), anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenGroupGrantingRolesOfAnUnmanagedApplication_whenDeleteGroup_thenForbidden()
      throws Exception {
    when(groupService.findClientRoles(DPAM, GROUP, "pgc-api")).thenReturn(List.of("pgc.read"));

    api.delete(GroupController.GROUP_PATH, DPAM, GROUP).andExpect(status().isForbidden());
    verify(groupService, never()).delete(anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenAddMemberToAnyGroup_thenNoContent() throws Exception {
    when(groupService.findClientRoles(DPAM, GROUP, "pgc-api")).thenReturn(List.of("pgc.read"));

    // a group granting roles of an application nobody here manages: Hurura'a administrators act
    // at every level all the same
    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, GROUP, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verify(groupService).addMember(DPAM, GROUP, DPAM_AGENT);
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenUnknownGroup_whenDeleteGroup_thenNotFound() throws Exception {
    api
        .delete(GroupController.GROUP_PATH, DPAM, "no-such-group")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.GROUP_NOT_FOUND.uri().toString()));
    verify(groupService, never()).delete(anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenUnknownDirection_whenGetGroups_thenNotFound() throws Exception {
    api
        .get(GroupController.BASE_PATH, "no-such-direction")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.DIRECTION_NOT_FOUND.uri().toString()));
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenCreateGroup_thenCreated() throws Exception {
    when(groupService.save(DPAM, "new-group")).thenReturn(new Group("g2", DPAM, "new-group"));

    api.post(new GroupRequest("new-group"), GroupController.BASE_PATH, DPAM)
        .andExpect(status().isCreated());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenGrantRoleOfAnyApplicationOfTheDirection_thenNoContent()
      throws Exception {
    api.put(Map.of(), GroupController.ROLE_PATH, DPAM, GROUP, 7L, "pgc.read")
        .andExpect(status().isNoContent());
    verify(groupService).addClientRole(DPAM, GROUP, "pgc-api", "pgc.read");
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenAddMemberToGroupGrantingUnmanagedRoles_thenNoContent()
      throws Exception {
    when(groupService.findClientRoles(DPAM, GROUP, "pgc-api")).thenReturn(List.of("pgc.read"));

    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, GROUP, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verify(groupService).addMember(DPAM, GROUP, DPAM_AGENT);
  }

  @Test
  @WithJwt("jwt/dsi-admin.json")
  void givenAdminOfAnotherDirection_whenAddMember_thenForbidden() throws Exception {
    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, GROUP, DPAM_AGENT)
        .andExpect(status().isForbidden());
    verify(groupService, never()).addMember(anyString(), anyString(), anyString());
  }
}
