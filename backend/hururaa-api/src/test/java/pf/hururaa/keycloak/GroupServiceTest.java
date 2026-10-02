package pf.hururaa.keycloak;

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
import org.keycloak.admin.model.GroupRepresentation;
import org.keycloak.admin.model.RoleRepresentation;
import pf.hururaa.direction.domain.User;

/**
 * The group operations tell whether they changed anything, which is what the permission journal
 * relies on, and leave Keycloak alone when they don't.
 */
class GroupServiceTest {
  private static final String DIRECTION = "dpam";
  private static final String ORG_ID = "org-1";
  private static final String GROUP = "escales.agent";
  private static final String GROUP_ID = "g1";
  private static final String USER_ID = "user-1";
  private static final String CLIENT_ID = "escales-api";

  private final DirectionService directionService = mock(DirectionService.class);

  private final CachingKeycloakOrganizationGroupRepository groupRepo =
      mock(CachingKeycloakOrganizationGroupRepository.class);

  private final GroupService service =
      new GroupService(directionService, groupRepo, new KeycloakRepresentationMapper() {});

  @BeforeEach
  void setUp() throws Exception {
    when(directionService.requireOrgId(DIRECTION)).thenReturn(ORG_ID);
    when(directionService.findMember(DIRECTION, USER_ID))
        .thenReturn(Optional.of(new User(USER_ID, "agent", null, null, null)));
    when(groupRepo.findGroupByName(ORG_ID, GROUP))
        .thenReturn(Optional.of(new GroupRepresentation().id(GROUP_ID).name(GROUP)));
  }

  @Test
  void givenNotYetMember_whenAddMember_thenAddedAndTrue() throws Exception {
    when(groupRepo.findMemberGroups(ORG_ID, USER_ID)).thenReturn(List.of());

    assertThat(service.addMember(DIRECTION, GROUP, USER_ID)).isTrue();
    verify(groupRepo).addGroupMember(ORG_ID, GROUP_ID, USER_ID);
  }

  @Test
  void givenAlreadyMember_whenAddMember_thenFalseAndKeycloakLeftAlone() throws Exception {
    when(groupRepo.findMemberGroups(ORG_ID, USER_ID))
        .thenReturn(List.of(new GroupRepresentation().id(GROUP_ID).name(GROUP)));

    assertThat(service.addMember(DIRECTION, GROUP, USER_ID)).isFalse();
    verify(groupRepo, never()).addGroupMember(anyString(), anyString(), anyString());
  }

  @Test
  void givenUserOutsideTheDirection_whenRemoveMember_thenFalse() throws Exception {
    when(directionService.findMember(DIRECTION, USER_ID)).thenReturn(Optional.empty());

    assertThat(service.removeMember(DIRECTION, GROUP, USER_ID)).isFalse();
    verify(groupRepo, never()).removeGroupMember(anyString(), anyString(), anyString());
  }

  @Test
  void givenRoleGranted_whenAddClientRole_thenFalse() throws Exception {
    when(groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID))
        .thenReturn(List.of(new RoleRepresentation().name("escales.stopovers.read")));

    assertThat(service.addClientRole(DIRECTION, GROUP, CLIENT_ID, "escales.stopovers.read"))
        .isFalse();
    verify(groupRepo, never()).addClientRoleToGroup(anyString(), anyString(), anyString(),
        anyString());
  }

  @Test
  void givenRoleGranted_whenRemoveClientRole_thenRemovedAndTrue() throws Exception {
    when(groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID))
        .thenReturn(List.of(new RoleRepresentation().name("escales.stopovers.read")));

    assertThat(service.removeClientRole(DIRECTION, GROUP, CLIENT_ID, "escales.stopovers.read"))
        .isTrue();
    verify(groupRepo)
        .removeClientRoleFromGroup(CLIENT_ID, ORG_ID, GROUP_ID, "escales.stopovers.read");
  }
}
