package pf.hururaa.uaa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pf.hururaa.HururaaFixtures;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.keycloak.ClientRoleService;
import pf.hururaa.keycloak.GroupService;

class DelegationServiceTest {

  static final String ROLES_NAMESPACE = "hururaa-api";

  GroupService groupService;

  ClientRoleService clientRoleService;

  DelegationService service;

  @BeforeEach
  void setUp() {
    groupService = mock(GroupService.class);
    clientRoleService = mock(ClientRoleService.class);
    service = new DelegationService(groupService, clientRoleService, ROLES_NAMESPACE);
  }

  @Test
  void whenProvisionDirection_thenAdminsGroupGrantsDirectionAdmin() throws Exception {
    service.provisionDirection("dsp");

    verify(clientRoleService).save(ROLES_NAMESPACE, "hururaa.direction.admin",
        "Administer the direction (in the DSI: every direction)");
    verify(groupService).save("dsp", "hururaa.admins");
    verify(groupService).addClientRole("dsp", "hururaa.admins", ROLES_NAMESPACE,
        "hururaa.direction.admin");
  }

  @Test
  void whenProvisionApplication_thenProductOwnersGroupGrantsItsManagerRole() throws Exception {
    service.provisionApplication(HururaaFixtures.escales());

    verify(clientRoleService).save(ROLES_NAMESPACE, "hururaa.application.escales.manage",
        "Manage Escales");
    verify(groupService).save("dpam", "hururaa.escales.product-owners");
    verify(groupService).addClientRole("dpam", "hururaa.escales.product-owners", ROLES_NAMESPACE,
        "hururaa.application.escales.manage");
  }

  @Test
  void whenDeprovisionApplication_thenGroupAndRoleDeleted() throws Exception {
    service.deprovisionApplication(HururaaFixtures.escales());

    verify(groupService).delete("dpam", "hururaa.escales.product-owners");
    verify(clientRoleService).delete(ROLES_NAMESPACE, "hururaa.application.escales.manage");
  }

  @Test
  void givenNoAdminsGroupYet_whenFindAdmins_thenNone() throws Exception {
    when(groupService.findByName("dpam", "hururaa.admins")).thenReturn(Optional.empty());

    assertThat(service.findAdmins("dpam")).isEmpty();
    verify(groupService, never()).findAllMembers(anyString(), anyString());
  }

  @Test
  void whenAddManager_thenProvisionedFirstThenAdded() throws Exception {
    when(groupService.addMember("dpam", "hururaa.escales.product-owners", "user-1"))
        .thenReturn(true);

    assertThat(service.addManager(HururaaFixtures.escales(), "user-1")).isTrue();
    verify(groupService).save("dpam", "hururaa.escales.product-owners");
  }

  @Test
  void givenNoAdminsGroup_whenRemoveAdmin_thenNothingRemoved() throws Exception {
    when(groupService.findByName("dpam", "hururaa.admins")).thenReturn(Optional.empty());

    assertThat(service.removeAdmin("dpam", "user-1")).isFalse();
    verify(groupService, never()).removeMember(anyString(), anyString(), anyString());
  }

  @Test
  void givenAdminsGroup_whenFindAdmins_thenItsMembers() throws Exception {
    when(groupService.findByName("dpam", "hururaa.admins"))
        .thenReturn(Optional.of(new Group("id", "dpam", "hururaa.admins")));
    when(groupService.findAllMembers("dpam", "hururaa.admins"))
        .thenReturn(List.of(HururaaFixtures.user(HururaaFixtures.DPAM_ADMIN, "dpam.admin")));

    assertThat(service.findAdmins("dpam")).extracting(user -> user.username())
        .containsExactly("dpam.admin");
  }
}
