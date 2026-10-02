package pf.hururaa.direction.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.DPAM_AGENT;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.MockMvcSupport;
import pf.hururaa.HururaaFixtures;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.history.PermissionHistoryMapperImpl;
import pf.hururaa.history.PermissionHistoryService;
import pf.hururaa.history.domain.PermissionHistoryFilter;
import pf.hururaa.journal.PermissionJournal;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;

/**
 * Groups belong to an application of their direction: the direction's administrators manage all of
 * them, an application's managers manage those of their application only.
 */
@WebMvcTest(controllers = GroupController.class)
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({HururaaFixtures.WebMvcTestConfiguration.class, DirectoryMapperImpl.class,
    PermissionHistoryMapperImpl.class})
@TestPropertySource(properties = "server.ssl.enabled=false")
class GroupControllerTest {

  /** A group of Escales, which dpam.manager manages. */
  private static final String GROUP = "escales.agent";

  /** A group of PGC, which dpam.manager does not manage. */
  private static final String PGC_GROUP = "pgc.agent";

  /** A group created outside of Hurura'a, whose name matches no application of dpam. */
  private static final String LEGACY_GROUP = "legacy-agents";

  private static final long PGC_ID = 7L;

  /** A second application of dpam, which dpam.manager does not manage. */
  private static Application pgc() {
    return Application
        .builder()
        .id(PGC_ID)
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

  @MockitoBean
  PermissionJournal permissionJournal;

  @MockitoBean
  PermissionHistoryService permissionHistoryService;

  @BeforeEach
  void setUp() throws Exception {
    stubDevDelegations(directionService, directionAdminRepository, applicationRepository);
    when(applicationRepository.findById(ESCALES_ID)).thenReturn(Optional.of(escales()));
    when(applicationRepository.findById(TE_FENUA_ID)).thenReturn(Optional.of(teFenua()));
    when(applicationRepository.findById(PGC_ID)).thenReturn(Optional.of(pgc()));
    when(applicationRepository.findByDirectionOrderByNameAsc(DPAM))
        .thenReturn(List.of(escales(), pgc()));
    for (final var name : List.of(GROUP, PGC_GROUP, LEGACY_GROUP)) {
      when(groupService.findByName(DPAM, name))
          .thenReturn(Optional.of(new Group("id-" + name, DPAM, name)));
    }
    when(groupService.findAll(DPAM)).thenReturn(List.of(new Group("id-" + GROUP, DPAM, GROUP),
        new Group("id-" + LEGACY_GROUP, DPAM, LEGACY_GROUP),
        new Group("id-" + PGC_GROUP, DPAM, PGC_GROUP)));
  }

  // ---------- reading ----------

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenMemberWithoutDelegation_whenGetGroups_thenForbidden() throws Exception {
    api.get(GroupController.BASE_PATH, DPAM).andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerInTheDirection_whenGetGroups_thenAllGroupsWithTheirApplication()
      throws Exception {
    api
        .get(GroupController.BASE_PATH, DPAM)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value(GROUP))
        .andExpect(jsonPath("$[0].applicationId").value(ESCALES_ID))
        .andExpect(jsonPath("$[0].applicationName").value("Escales"))
        .andExpect(jsonPath("$[1].name").value(LEGACY_GROUP))
        .andExpect(jsonPath("$[1].applicationId").doesNotExist())
        .andExpect(jsonPath("$[2].applicationId").value(PGC_ID));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerInTheDirection_whenGetApplicationGroups_thenOnlyThoseOfTheApplication()
      throws Exception {
    api
        .get(GroupController.APPLICATION_GROUPS_PATH, DPAM, PGC_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value(PGC_GROUP));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerInTheDirection_whenGetGroup_thenWithItsApplication() throws Exception {
    api
        .get(GroupController.GROUP_PATH, DPAM, GROUP)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(GROUP))
        .andExpect(jsonPath("$.applicationId").value(ESCALES_ID))
        .andExpect(jsonPath("$.applicationName").value("Escales"));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenGetGroupRoles_thenRolesOfTheGroupsApplication()
      throws Exception {
    when(groupService.findClientRoles(DPAM, GROUP, "escales-api"))
        .thenReturn(List.of("escales.stopovers.read"));

    api
        .get(GroupController.ROLES_PATH, DPAM, GROUP)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].applicationId").value(ESCALES_ID))
        .andExpect(jsonPath("$[0].clientId").value("escales-api"))
        .andExpect(jsonPath("$[0].role").value("escales.stopovers.read"));
    verify(groupService, never()).findClientRoles(DPAM, GROUP, "pgc-api");
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenGroupWithoutApplication_whenGetGroupRoles_thenNone() throws Exception {
    api
        .get(GroupController.ROLES_PATH, DPAM, LEGACY_GROUP)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenUnknownDirection_whenGetGroups_thenNotFound() throws Exception {
    api
        .get(GroupController.BASE_PATH, "no-such-direction")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.DIRECTION_NOT_FOUND.uri().toString()));
  }

  // ---------- creation ----------

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenCreateGroup_thenCreatedWithTheApplicationPrefix()
      throws Exception {
    when(groupService.save(DPAM, "escales.supervisor"))
        .thenReturn(new Group("g2", DPAM, "escales.supervisor"));

    api.post(new GroupRequest("supervisor"), GroupController.APPLICATION_GROUPS_PATH, DPAM,
        ESCALES_ID)
        .andExpect(status().isCreated())
        .andExpect(header().string("Location",
            Matchers.endsWith("/directions/dpam/groups/escales.supervisor")));
    verify(permissionJournal).groupCreated(
        argThat(application -> ESCALES_ID == application.getId()), eq("escales.supervisor"));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfAnotherApplication_whenCreateGroup_thenForbidden() throws Exception {
    api.post(new GroupRequest("supervisor"), GroupController.APPLICATION_GROUPS_PATH, DPAM,
        PGC_ID)
        .andExpect(status().isForbidden());
    verify(groupService, never()).save(anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/dsi-manager.json")
  void givenApplicationOfAnotherDirection_whenCreateGroup_thenForbidden() throws Exception {
    // dsi.manager manages Te Fenua, but dsi manages it, not dpam
    api.post(new GroupRequest("agent"), GroupController.APPLICATION_GROUPS_PATH, DPAM,
        TE_FENUA_ID)
        .andExpect(status().isForbidden());
    verify(groupService, never()).save(anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenCreateGroupOfAnyApplication_thenCreated() throws Exception {
    when(groupService.save(DPAM, "pgc.supervisor"))
        .thenReturn(new Group("g2", DPAM, "pgc.supervisor"));

    api.post(new GroupRequest("supervisor"), GroupController.APPLICATION_GROUPS_PATH, DPAM,
        PGC_ID)
        .andExpect(status().isCreated());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenExistingGroup_whenCreateGroup_thenNotJournaled() throws Exception {
    when(groupService.save(DPAM, GROUP)).thenReturn(new Group("id-" + GROUP, DPAM, GROUP));

    api.post(new GroupRequest("agent"), GroupController.APPLICATION_GROUPS_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isCreated());
    verifyNoInteractions(permissionJournal);
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenInvalidName_whenCreateGroup_thenUnprocessable() throws Exception {
    api.post(new GroupRequest("Agents/All"), GroupController.APPLICATION_GROUPS_PATH, DPAM,
        ESCALES_ID)
        .andExpect(status().isUnprocessableContent());
    verify(groupService, never()).save(anyString(), anyString());
  }

  // ---------- roles ----------

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfTheGroupsApplication_whenGrantRole_thenGrantedAndJournaled()
      throws Exception {
    when(groupService.addClientRole(DPAM, GROUP, "escales-api", "escales.stopovers.read"))
        .thenReturn(true);

    api.put(Map.of(), GroupController.ROLE_PATH, DPAM, GROUP, "escales.stopovers.read")
        .andExpect(status().isNoContent());
    verify(permissionJournal).groupRoleGranted(
        argThat(application -> ESCALES_ID == application.getId()), eq(GROUP),
        eq("escales.stopovers.read"));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfAnotherApplication_whenGrantRole_thenForbidden() throws Exception {
    api.put(Map.of(), GroupController.ROLE_PATH, DPAM, PGC_GROUP, "pgc.read")
        .andExpect(status().isForbidden());
    verify(groupService, never()).addClientRole(anyString(), anyString(), anyString(),
        anyString());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenGrantRoleInAnyGroupOfTheDirection_thenGrantsItsApplicationsRole()
      throws Exception {
    api.put(Map.of(), GroupController.ROLE_PATH, DPAM, PGC_GROUP, "pgc.read")
        .andExpect(status().isNoContent());
    verify(groupService).addClientRole(DPAM, PGC_GROUP, "pgc-api", "pgc.read");
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenGroupWithoutApplication_whenGrantRole_thenConflict() throws Exception {
    api.put(Map.of(), GroupController.ROLE_PATH, DPAM, LEGACY_GROUP, "escales.stopovers.read")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(ProblemType.GROUP_WITHOUT_APPLICATION.uri().toString()))
        .andExpect(jsonPath("$.parameters.group").value(LEGACY_GROUP));
    verify(groupService, never()).addClientRole(anyString(), anyString(), anyString(),
        anyString());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfTheGroupsApplication_whenRevokeRole_thenRevoked() throws Exception {
    api.delete(GroupController.ROLE_PATH, DPAM, GROUP, "escales.stopovers.read")
        .andExpect(status().isNoContent());
    verify(groupService).removeClientRole(DPAM, GROUP, "escales-api", "escales.stopovers.read");
  }

  // ---------- members and deletion ----------

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfTheGroupsApplication_whenAddMember_thenAddedAndJournaled()
      throws Exception {
    when(groupService.addMember(DPAM, GROUP, DPAM_AGENT)).thenReturn(true);

    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, GROUP, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verify(permissionJournal).groupMemberAdded(eq(DPAM),
        argThat(application -> ESCALES_ID == application.getId()), eq(GROUP), eq(DPAM_AGENT));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfAnotherApplication_whenAddMember_thenForbidden() throws Exception {
    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, PGC_GROUP, DPAM_AGENT)
        .andExpect(status().isForbidden());
    verify(groupService, never()).addMember(anyString(), anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenGroupWithoutApplication_whenManagerAddsMember_thenForbidden() throws Exception {
    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, LEGACY_GROUP, DPAM_AGENT)
        .andExpect(status().isForbidden());
    verify(groupService, never()).addMember(anyString(), anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfAnotherApplication_whenDeleteGroup_thenForbidden() throws Exception {
    api.delete(GroupController.GROUP_PATH, DPAM, PGC_GROUP).andExpect(status().isForbidden());
    verify(groupService, never()).delete(anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerOfTheGroupsApplication_whenDeleteGroup_thenDeletedAndJournaled()
      throws Exception {
    api.delete(GroupController.GROUP_PATH, DPAM, GROUP).andExpect(status().isNoContent());
    verify(groupService).delete(DPAM, GROUP);
    verify(permissionJournal).groupDeleted(eq(DPAM),
        argThat(application -> ESCALES_ID == application.getId()), eq(GROUP));
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenAddMemberToAnyGroup_thenNoContent() throws Exception {
    // a group of an application nobody here manages: Hurura'a administrators act at every level
    // all the same
    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, PGC_GROUP, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verify(groupService).addMember(DPAM, PGC_GROUP, DPAM_AGENT);
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenAddMemberToAnyGroupOfTheDirection_thenNoContent()
      throws Exception {
    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, LEGACY_GROUP, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verify(groupService).addMember(DPAM, LEGACY_GROUP, DPAM_AGENT);
  }

  @Test
  @WithJwt("jwt/dsi-admin.json")
  void givenAdminOfAnotherDirection_whenAddMember_thenForbidden() throws Exception {
    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, GROUP, DPAM_AGENT)
        .andExpect(status().isForbidden());
    verify(groupService, never()).addMember(anyString(), anyString(), anyString());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenAlreadyMember_whenAddMember_thenNotJournaled() throws Exception {
    when(groupService.addMember(DPAM, GROUP, DPAM_AGENT)).thenReturn(false);

    api.put(Map.of(), GroupController.MEMBER_PATH, DPAM, GROUP, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verifyNoInteractions(permissionJournal);
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenUnknownGroup_whenDeleteGroup_thenNotFound() throws Exception {
    api
        .delete(GroupController.GROUP_PATH, DPAM, "escales.no-such-group")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.GROUP_NOT_FOUND.uri().toString()));
    verify(groupService, never()).delete(anyString(), anyString());
  }

  // ---------- history ----------

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenMemberWithoutDelegation_whenGetGroupHistory_thenForbidden() throws Exception {
    api.get(GroupController.HISTORY_PATH, DPAM, GROUP).andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenManagerInTheDirection_whenGetGroupHistory_thenFilteredOnTheGroup() throws Exception {
    when(permissionHistoryService.find(new PermissionHistoryFilter(DPAM, null, GROUP, Set.of()),
        PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    api.get(GroupController.HISTORY_PATH, DPAM, GROUP).andExpect(status().isOk());
    verify(permissionHistoryService)
        .find(new PermissionHistoryFilter(DPAM, null, GROUP, Set.of()), PageRequest.of(0, 20));
  }
}
