package pf.hururaa.application.web;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.DSI;
import static pf.hururaa.HururaaFixtures.DPAM_MANAGER;
import static pf.hururaa.HururaaFixtures.ESCALES_ID;
import static pf.hururaa.HururaaFixtures.HURURAA_ID;
import static pf.hururaa.HururaaFixtures.hururaa;
import static pf.hururaa.HururaaFixtures.escales;
import static pf.hururaa.HururaaFixtures.stubDevDelegations;
import static pf.hururaa.HururaaFixtures.user;
import java.util.List;
import java.util.Optional;
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
import pf.hururaa.application.domain.ApplicationRole;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.journal.PermissionJournal;
import pf.hururaa.uaa.DelegationService;
import pf.hururaa.direction.web.DirectoryMapperImpl;
import pf.hururaa.keycloak.ClientRoleService;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.problem.ProblemType;

/**
 * Levels 2 and 3 of the delegation chain on an application: its direction's administrators and
 * its managers (members of its {@code hururaa.<prefix>.product-owners} group, designated through
 * the groups API) define its roles. Hurura'a's roles never change.
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
  DelegationService delegationService;

  @MockitoBean
  DirectionService directionService;

  @MockitoBean
  GroupService groupService;

  @MockitoBean
  ClientRoleService clientRoleService;

  @MockitoBean
  ResourceEventPublisher resourceEvents;

  @MockitoBean
  PermissionJournal permissionJournal;

  @BeforeEach
  void setUp() throws Exception {
    stubDevDelegations(directionService, applicationRepository);
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

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenCreateReservedRole_thenReservedName() throws Exception {
    api
        .post(new ApplicationRoleRequest("hururaa.application.escales.manage", null),
            ApplicationRoleController.BASE_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(ProblemType.RESERVED_NAME.uri().toString()));
    api
        .delete(ApplicationRoleController.ROLE_PATH, DPAM, ESCALES_ID, "hururaa.direction.admin")
        .andExpect(status().isConflict());
    verifyNoInteractions(clientRoleService);
  }

  // ---------- Hurura'a's roles ----------

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenDelegateOfDpam_whenGetHururaaRolesInDpam_thenOnlyThoseEffectiveInDpam()
      throws Exception {
    when(applicationRepository.findById(HURURAA_ID)).thenReturn(Optional.of(hururaa()));
    when(clientRoleService.findAll("hururaa-api")).thenReturn(List.of(
        new ApplicationRole("hururaa.direction.admin", null),
        new ApplicationRole("hururaa.application.anahei.manage", null),
        new ApplicationRole("hururaa.application.escales.manage", null),
        new ApplicationRole("hururaa.application.te-fenua.manage", null)));

    api
        .get(ApplicationRoleController.BASE_PATH, DPAM, HURURAA_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("hururaa.direction.admin"))
        .andExpect(jsonPath("$[1].name").value("hururaa.application.escales.manage"));
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenMemberWithoutDelegation_whenGetHururaaRoles_thenForbidden() throws Exception {
    when(applicationRepository.findById(HURURAA_ID)).thenReturn(Optional.of(hururaa()));
    api.get(ApplicationRoleController.BASE_PATH, DPAM, HURURAA_ID)
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("jwt/hururaa-admin.json")
  void givenHururaaAdmin_whenCreateOrDeleteHururaaRole_thenReservedName() throws Exception {
    when(applicationRepository.findById(HURURAA_ID)).thenReturn(Optional.of(hururaa()));
    api
        .post(new ApplicationRoleRequest("audit.read", null), ApplicationRoleController.BASE_PATH,
            DSI, HURURAA_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(ProblemType.RESERVED_NAME.uri().toString()));
    api
        .delete(ApplicationRoleController.ROLE_PATH, DSI, HURURAA_ID, "hururaa.direction.admin")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(ProblemType.RESERVED_NAME.uri().toString()));
    verifyNoInteractions(clientRoleService, permissionJournal);
  }

  // ---------- managers ----------

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenApplicationManager_whenGetManagers_thenMembersOfProductOwners() throws Exception {
    when(delegationService.findManagers(any(Application.class)))
        .thenReturn(List.of(user(DPAM_MANAGER, "dpam.manager")));

    api
        .get(ApplicationManagerController.BASE_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("dpam.manager"));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenUnknownDirection_whenGetRoles_thenNotFound() throws Exception {
    api
        .get(ApplicationRoleController.BASE_PATH, "no-such-direction", ESCALES_ID)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.DIRECTION_NOT_FOUND.uri().toString()));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenNewRole_whenCreateRole_thenJournaled() throws Exception {
    when(clientRoleService.save("escales-api", "escales.stopovers.delete", null)).thenReturn(true);

    api
        .post(new ApplicationRoleRequest("escales.stopovers.delete", null),
            ApplicationRoleController.BASE_PATH, DPAM, ESCALES_ID)
        .andExpect(status().isCreated());
    verify(permissionJournal).applicationRoleCreated(any(Application.class),
        eq("escales.stopovers.delete"));
  }

  @Test
  @WithJwt("jwt/dpam-manager.json")
  void givenNoSuchRole_whenDeleteRole_thenNotJournaled() throws Exception {
    when(clientRoleService.delete("escales-api", "escales.nothing")).thenReturn(false);

    api
        .delete(ApplicationRoleController.ROLE_PATH, DPAM, ESCALES_ID, "escales.nothing")
        .andExpect(status().isNoContent());
    verify(permissionJournal, never()).applicationRoleDeleted(any(), any());
  }
}
