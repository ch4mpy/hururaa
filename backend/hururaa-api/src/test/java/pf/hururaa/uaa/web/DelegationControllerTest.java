package pf.hururaa.uaa.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.DPAM_ADMIN;
import static pf.hururaa.HururaaFixtures.DPAM_MANAGER;
import static pf.hururaa.HururaaFixtures.escales;
import java.util.List;
import org.junit.jupiter.api.Test;
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
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.application.web.ApplicationMapperImpl;
import pf.hururaa.direction.domain.DirectionAdmin;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.uaa.HururaaPermission;

@WebMvcTest(controllers = DelegationController.class)
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({HururaaFixtures.WebMvcTestConfiguration.class, ApplicationMapperImpl.class})
@TestPropertySource(properties = "server.ssl.enabled=false")
class DelegationControllerTest {

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

  @Test
  @WithAnonymousUser
  void givenAnonymous_whenGetDelegations_thenUnauthorized() throws Exception {
    api.get(DelegationController.BASE_PATH).andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("jwt/sipf-admin.json")
  void givenPlatformAdmin_whenGetDelegations_thenPlatformPermissions() throws Exception {
    api
        .get(DelegationController.BASE_PATH)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platformOrganization").value("dsi"))
        .andExpect(jsonPath("$.platformPermissions[0]")
            .value(HururaaPermission.Names.APPLICATIONS_MANAGE))
        .andExpect(jsonPath("$.platformPermissions[1]")
            .value(HururaaPermission.Names.DIRECTION_ADMINS_MANAGE))
        .andExpect(jsonPath("$.administeredDirections").isEmpty());
  }

  @Test
  @WithJwt("jwt/dpam-sipf-lookalike.json")
  void givenHururaaRolesOutsideThePlatformOrganization_whenGetDelegations_thenNone()
      throws Exception {
    api
        .get(DelegationController.BASE_PATH)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platformPermissions").isEmpty());
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenDirectionAdmin_whenGetDelegations_thenAdministeredDirections() throws Exception {
    when(directionAdminRepository.findByUserIdOrderByDirection(DPAM_ADMIN))
        .thenReturn(List.of(DirectionAdmin.builder().direction(DPAM).userId(DPAM_ADMIN).build()));

    api
        .get(DelegationController.BASE_PATH)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.administeredDirections[0]").value(DPAM));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenGetDelegations_thenManagedApplications() throws Exception {
    when(applicationRepository.findByManager(DPAM_MANAGER)).thenReturn(List.of(escales()));

    api
        .get(DelegationController.BASE_PATH)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.managedApplications[0].clientPrefix").value("escales"));
  }
}
