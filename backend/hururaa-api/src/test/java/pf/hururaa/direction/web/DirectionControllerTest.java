package pf.hururaa.direction.web;

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
import static pf.hururaa.HururaaFixtures.user;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import pf.hururaa.direction.domain.Direction;
import pf.hururaa.direction.domain.DirectionAdmin;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;

/**
 * Level 1 of the delegation chain: platform administrators designate each direction's
 * administrators.
 */
@WebMvcTest(controllers = DirectionController.class)
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({HururaaFixtures.WebMvcTestConfiguration.class, DirectoryMapperImpl.class})
@TestPropertySource(properties = "server.ssl.enabled=false")
class DirectionControllerTest {

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
  ResourceEventPublisher resourceEvents;

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
    when(applicationRepository.existsByDirectionAndManager(DPAM, DPAM_MANAGER)).thenReturn(true);
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
  @WithJwt("jwt/sipf-admin.json")
  void givenPlatformAdmin_whenGetAdminsOfAnyDirection_thenOk() throws Exception {
    when(directionAdminRepository.findByDirectionOrderByUserId(DPAM))
        .thenReturn(List.of(DirectionAdmin.builder().direction(DPAM).userId(DPAM_ADMIN).build()));
    when(directionService.findMember(DPAM, DPAM_ADMIN))
        .thenReturn(Optional.of(user(DPAM_ADMIN, "dpam.admin")));

    api
        .get(DirectionController.ADMINS_PATH, DPAM)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("dpam.admin"));
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenDesignateAnotherAdmin_thenForbidden() throws Exception {
    when(directionAdminRepository.existsByDirectionAndUserId(DPAM, DPAM_ADMIN)).thenReturn(true);

    api
        .put(Map.of(), DirectionController.ADMIN_PATH, DPAM, DPAM_AGENT)
        .andExpect(status().isForbidden());
    verify(directionAdminRepository, never()).save(any());
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenPlatformAdmin_whenDesignateAdmin_thenSaved() throws Exception {
    when(directionService.requireMember(DPAM, DPAM_AGENT))
        .thenReturn(user(DPAM_AGENT, "dpam.agent"));

    api
        .put(Map.of(), DirectionController.ADMIN_PATH, DPAM, DPAM_AGENT)
        .andExpect(status().isNoContent());
    verify(directionAdminRepository).save(any(DirectionAdmin.class));
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenPlatformAdmin_whenRevokeAdmin_thenDeleted() throws Exception {
    final var admin = DirectionAdmin.builder().direction(DSI).userId("someone").build();
    when(directionAdminRepository.findByDirectionAndUserId(DSI, "someone"))
        .thenReturn(Optional.of(admin));

    api
        .delete(DirectionController.ADMIN_PATH, DSI, "someone")
        .andExpect(status().isNoContent());
    verify(directionAdminRepository).delete(admin);
  }
}
