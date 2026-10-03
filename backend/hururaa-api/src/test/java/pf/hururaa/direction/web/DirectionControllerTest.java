package pf.hururaa.direction.web;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pf.hururaa.HururaaFixtures.DAF;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.DPAM_ADMIN;
import static pf.hururaa.HururaaFixtures.DPAM_AGENT;
import static pf.hururaa.HururaaFixtures.DPAM_MANAGER;
import static pf.hururaa.HururaaFixtures.DSI;
import static pf.hururaa.HururaaFixtures.HURURAA_ADMIN;
import static pf.hururaa.HururaaFixtures.stubDevDelegations;
import static pf.hururaa.HururaaFixtures.user;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.MockMvcSupport;
import pf.hururaa.HururaaFixtures;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.journal.PermissionJournal;
import pf.hururaa.history.PermissionHistoryMapperImpl;
import pf.hururaa.history.PermissionHistoryService;
import pf.hururaa.history.domain.PermissionChange;
import pf.hururaa.history.domain.PermissionChangeCategory;
import pf.hururaa.history.domain.PermissionChangeType;
import pf.hururaa.history.domain.PermissionHistoryFilter;
import pf.hururaa.direction.domain.Direction;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.uaa.DelegationService;

/**
 * Level 1 of the delegation chain: Hurura'a administrators (administrators of the DSI) create
 * the directions and designate their administrators (members of their {@code hururaa.admins}
 * group).
 */
@WebMvcTest(controllers = DirectionController.class)
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({HururaaFixtures.WebMvcTestConfiguration.class, DirectoryMapperImpl.class,
    PermissionHistoryMapperImpl.class})
@TestPropertySource(properties = "server.ssl.enabled=false")
class DirectionControllerTest {

  @Autowired
  MockMvcSupport api;

  @MockitoBean
  ApplicationRepository applicationRepository;

  @MockitoBean
  DelegationService delegationService;

  @MockitoBean
  DirectionService directionService;

  @MockitoBean
  GroupService groupService;

  @MockitoBean
  PermissionHistoryService permissionHistoryService;

  @BeforeEach
  void setUp() throws Exception {
    stubDevDelegations(directionService, applicationRepository);
  }

  @MockitoBean
  ResourceEventPublisher resourceEvents;

  @MockitoBean
  PermissionJournal permissionJournal;

  @Test
  @WithAnonymousUser
  void givenAnonymous_whenGetDirections_thenUnauthorized() throws Exception {
    api.get(DirectionController.BASE_PATH).andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenAuthenticatedUser_whenGetDirections_thenOk() throws Exception {
    when(directionService.findAll()).thenReturn(List.of(
        new Direction(DAF, DAF, "Direction des affaires foncières"),
        new Direction(DPAM, DPAM, null)));

    api
        .get(DirectionController.BASE_PATH)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].alias").value(DAF))
        .andExpect(jsonPath("$[0].description").value("Direction des affaires foncières"));
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenMemberWithoutDelegation_whenSearchUsers_thenForbidden() throws Exception {
    api.get(DirectionController.USERS_PATH, DPAM).andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenSearchUsers_thenOk() throws Exception {
    when(directionService.searchMembers(DPAM, "agent", PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(user(DPAM_AGENT, "dpam.agent")),
            PageRequest.of(0, 20), 1));

    api
        .get(DirectionController.USERS_PATH + "?search=agent", DPAM)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].username").value("dpam.agent"))
        .andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenGetAdminsOfAnyDirection_thenOk() throws Exception {
    when(delegationService.findAdmins(DPAM)).thenReturn(List.of(user(DPAM_ADMIN, "dpam.admin")));

    api
        .get(DirectionController.ADMINS_PATH, DPAM)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("dpam.admin"));
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenGetAdminsOfAnotherDirection_thenForbidden() throws Exception {
    api.get(DirectionController.ADMINS_PATH, DAF).andExpect(status().isForbidden());
    verify(delegationService, never()).findAdmins(any());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenDesignateAnotherAdmin_thenForbidden() throws Exception {

    api
        .put(Map.of(), DirectionController.ADMIN_PATH, DPAM, DPAM_AGENT)
        .andExpect(status().isForbidden());
    verify(delegationService, never()).addAdmin(any(), any());
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenDesignateAdmin_thenAddedToAdminsGroupAndJournaled()
      throws Exception {
    when(delegationService.addAdmin(DPAM, DPAM_AGENT)).thenReturn(true);

    api
        .put(Map.of(), DirectionController.ADMIN_PATH, DPAM, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verify(permissionJournal).directionAdminGranted(DPAM, DPAM_AGENT);
    verify(resourceEvents).publish(any());
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenAdminAlready_whenDesignateAdmin_thenNothingJournaled() throws Exception {
    when(delegationService.addAdmin(DPAM, DPAM_ADMIN)).thenReturn(false);

    api
        .put(Map.of(), DirectionController.ADMIN_PATH, DPAM, DPAM_ADMIN)
        .andExpect(status().isNoContent());
    verifyNoInteractions(permissionJournal, resourceEvents);
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenRevokeAdmin_thenRemovedFromAdminsGroupAndJournaled()
      throws Exception {
    when(delegationService.removeAdmin(DSI, "someone")).thenReturn(true);

    api
        .delete(DirectionController.ADMIN_PATH, DSI, "someone")
        .andExpect(status().isNoContent());
    verify(permissionJournal).directionAdminRevoked(DSI, "someone");
  }

  @Test
  @WithAnonymousUser
  void givenAnonymous_whenGetHistory_thenUnauthorized() throws Exception {
    api.get(DirectionController.HISTORY_PATH, DPAM).andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenMemberWithoutDelegation_whenGetHistory_thenForbidden() throws Exception {
    api.get(DirectionController.HISTORY_PATH, DPAM).andExpect(status().isForbidden());
    verify(permissionHistoryService, never()).find(any(), any());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenGetHistory_thenOk() throws Exception {
    when(permissionHistoryService.find(
        PermissionHistoryFilter.ofDirection(DPAM, Set.of(PermissionChangeCategory.DELEGATION,
            PermissionChangeCategory.GROUP_MEMBER)),
        PageRequest.of(1, 5)))
        .thenReturn(new PageImpl<>(List.of(new PermissionChange(
            Instant.parse("2026-10-01T08:00:00Z"), user(HURURAA_ADMIN, "hururaa.admin"),
            PermissionChangeType.DIRECTION_ADMIN_GRANTED, user(DPAM_ADMIN, "dpam.admin"), null,
            null, null, null, null)), PageRequest.of(1, 5), 6));

    api
        .get(DirectionController.HISTORY_PATH
            + "?page=1&size=5&categories=DELEGATION&categories=GROUP_MEMBER", DPAM)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].authorUsername").value("hururaa.admin"))
        .andExpect(jsonPath("$.content[0].subjectUsername").value("dpam.admin"))
        .andExpect(jsonPath("$.content[0].type").value("DIRECTION_ADMIN_GRANTED"))
        .andExpect(jsonPath("$.content[0].category").value("DELEGATION"))
        .andExpect(jsonPath("$.page.totalElements").value(6));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenGetHistory_thenOk() throws Exception {
    when(permissionHistoryService.find(PermissionHistoryFilter.ofDirection(DPAM, Set.of()),
        PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    api.get(DirectionController.HISTORY_PATH, DPAM).andExpect(status().isOk());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenCreateDirection_thenForbidden() throws Exception {
    api.post(new DirectionCreationRequest("dsp", "DSP", null), DirectionController.BASE_PATH)
        .andExpect(status().isForbidden());
    verify(directionService, never()).create(any(), any(), any());
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenCreateDirection_thenCreatedAndJournaled() throws Exception {
    when(directionService.create("dsp", "DSP", "Direction de la santé publique"))
        .thenReturn(new Direction("dsp", "DSP", "Direction de la santé publique"));

    api
        .post(new DirectionCreationRequest("dsp", "DSP", "Direction de la santé publique"),
            DirectionController.BASE_PATH)
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", endsWith("/directions/dsp")));
    verify(delegationService).provisionDirection("dsp");
    verify(permissionJournal).directionCreated("dsp");
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenTakenAlias_whenCreateDirection_thenConflictAndNotJournaled() throws Exception {
    when(directionService.create("dpam", "DPAM", null))
        .thenThrow(new HururaaProblemException(ProblemType.DIRECTION_ALREADY_EXISTS, "taken",
            Map.of("direction", "dpam")));

    api
        .post(new DirectionCreationRequest("dpam", "DPAM", null), DirectionController.BASE_PATH)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(ProblemType.DIRECTION_ALREADY_EXISTS.uri().toString()));
    verifyNoInteractions(permissionJournal);
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenInvalidAlias_whenCreateDirection_thenUnprocessable() throws Exception {
    api
        .post(new DirectionCreationRequest("Not An Alias", "X", null),
            DirectionController.BASE_PATH)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.alias").exists());
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenAuthenticatedUser_whenGetDirection_thenOk() throws Exception {
    api
        .get(DirectionController.DIRECTION_PATH, DPAM)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alias").value(DPAM));
  }
}
