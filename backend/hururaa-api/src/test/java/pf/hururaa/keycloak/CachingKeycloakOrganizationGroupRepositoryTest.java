package pf.hururaa.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.api.ClientRoleMappingsApi;
import org.keycloak.admin.api.ClientsApi;
import org.keycloak.admin.api.OrganizationsApi;
import org.keycloak.admin.api.RolesApi;
import org.keycloak.admin.model.ClientRepresentation;
import org.keycloak.admin.model.GroupRepresentation;
import org.keycloak.admin.model.MemberRepresentation;
import org.keycloak.admin.model.RoleRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.HttpClientErrorException;

import pf.hururaa.CacheConfiguration;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.problem.HururaaProblemException;

/**
 * Hururaa roles are organization groups: Keycloak answers {@code 400 Cannot manage organization
 * related group via non Organization API} to the realm-level {@code /groups/{id}/...} endpoints,
 * so every call must go through {@code /organizations/{orgId}/groups/{groupId}/...}.
 */
@TestPropertySource(properties = "spring.cache.type=caffeine")
@SpringJUnitConfig(classes = CachingKeycloakOrganizationGroupRepositoryTest.TestConfig.class)
class CachingKeycloakOrganizationGroupRepositoryTest {

  static final String REALM = "hururaa";
  static final String CLIENT_ID = "hururaa-api";
  static final String CLIENT_UUID = "client-uuid-1";
  static final String ORG_ID = "org-1";
  static final String GROUP_ID = "group-1";
  static final String GROUP_NAME = "Engineering";
  static final String USER_ID = "user-1";
  static final RoleRepresentation READ =
      new RoleRepresentation().id("role-uuid-1").clientRole(true).name("tenant1.support.read");
  static final RoleRepresentation EDIT =
      new RoleRepresentation().id("role-uuid-2").clientRole(true).name("tenant1.support.edit");

  @Configuration
  @ImportAutoConfiguration(CacheAutoConfiguration.class)
  @Import({
      CacheConfiguration.class,
      CachingKeycloakClientRepository.class,
      CachingKeycloakRoleRepository.class,
      CachingKeycloakOrganizationGroupRepository.class})
  static class TestConfig {
    @Bean
    KeycloakAdminApiProperties keycloakAdminApiProperties() {
      return new KeycloakAdminApiProperties(REALM, "-api", "-bff", List.of("view-users"));
    }
  }

  @MockitoBean
  OrganizationsApi organizationsApi;

  @MockitoBean
  ClientsApi clientsApi;

  @MockitoBean
  RolesApi rolesApi;

  @MockitoBean
  ClientRoleMappingsApi clientRoleMappingsApi;

  @Autowired
  CachingKeycloakOrganizationGroupRepository groupRepo;

  @Autowired
  CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    when(
        clientsApi.adminRealmsRealmClientsGet(
            REALM,
            Optional.of(CLIENT_ID),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()))
        .thenReturn(ResponseEntity.ok(List.of(new ClientRepresentation().id(CLIENT_UUID).clientId(CLIENT_ID))));
    when(
        rolesApi.adminRealmsRealmClientsClientUuidRolesGet(
            REALM,
            CLIENT_UUID,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty())).thenReturn(ResponseEntity.ok(List.of(READ, EDIT)));
  }

  private void stubFindGroups(List<GroupRepresentation> body) {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGet(
                REALM,
                ORG_ID,
                Optional.empty(),
                Optional.of(false),
                Optional.of(0),
                Optional.of(Integer.MAX_VALUE),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty())).thenReturn(ResponseEntity.ok(body));
  }

  private void stubFindGroupByName(String groupName, List<GroupRepresentation> body) {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGet(
                REALM,
                ORG_ID,
                Optional.empty(),
                Optional.of(true),
                Optional.of(0),
                Optional.of(1),
                Optional.empty(),
                Optional.empty(),
                Optional.of(groupName),
                Optional.empty())).thenReturn(ResponseEntity.ok(body));
  }

  private void stubFindGroupMembers(int first, int max, List<MemberRepresentation> body) {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersGet(
                REALM,
                ORG_ID,
                GROUP_ID,
                Optional.empty(),
                Optional.of(first),
                Optional.of(max))).thenReturn(ResponseEntity.ok(body));
  }

  private void stubFindMemberGroups(List<GroupRepresentation> body) {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdMembersMemberIdGroupsGet(
                REALM,
                ORG_ID,
                USER_ID,
                Optional.empty(),
                Optional.of(0),
                Optional.of(Integer.MAX_VALUE),
                Optional.empty())).thenReturn(ResponseEntity.ok(body));
  }

  // ---- findGroups ----

  @Test
  void findGroupsReturnsRootGroupsAndCachesResult() throws HururaaProblemException {
    final var group = new GroupRepresentation().id(GROUP_ID).name("Engineering");
    stubFindGroups(List.of(group));

    final var first = groupRepo.findGroups(ORG_ID);
    final var second = groupRepo.findGroups(ORG_ID);

    assertThat(first).containsExactly(group);
    assertThat(second).containsExactly(group);
    verify(organizationsApi, times(1))
        .adminRealmsRealmOrganizationsOrgIdGroupsGet(
            REALM,
            ORG_ID,
            Optional.empty(),
            Optional.of(false),
            Optional.of(0),
            Optional.of(Integer.MAX_VALUE),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
  }

  @Test
  void findGroupsReturnsEmptyListWhenBodyIsNull() throws HururaaProblemException {
    stubFindGroups(null);

    assertThat(groupRepo.findGroups(ORG_ID)).isEmpty();
  }

  @Test
  void findGroupsReturnsEmptyListWhenBodyIsEmpty() throws HururaaProblemException {
    stubFindGroups(List.of());

    assertThat(groupRepo.findGroups(ORG_ID)).isEmpty();
  }

  @Test
  void findGroupsWrapsHttpClientErrorExceptionAs500() {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGet(
                REALM,
                ORG_ID,
                Optional.empty(),
                Optional.of(false),
                Optional.of(0),
                Optional.of(Integer.MAX_VALUE),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty())).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.findGroups(ORG_ID))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- findGroupByName ----

  @Test
  void findGroupByNameReturnsGroupWhenPresentAndCachesResult() throws HururaaProblemException {
    final var group = new GroupRepresentation().id(GROUP_ID).name(GROUP_NAME);
    stubFindGroupByName(GROUP_NAME, List.of(group));

    final var first = groupRepo.findGroupByName(ORG_ID, GROUP_NAME);
    final var second = groupRepo.findGroupByName(ORG_ID, GROUP_NAME);

    assertThat(first).contains(group);
    assertThat(second).contains(group);
    verify(organizationsApi, times(1))
        .adminRealmsRealmOrganizationsOrgIdGroupsGet(
            REALM,
            ORG_ID,
            Optional.empty(),
            Optional.of(true),
            Optional.of(0),
            Optional.of(1),
            Optional.empty(),
            Optional.empty(),
            Optional.of(GROUP_NAME),
            Optional.empty());
  }

  @Test
  void findGroupByNameReturnsEmptyWhenBodyIsNull() throws HururaaProblemException {
    stubFindGroupByName(GROUP_NAME, null);

    assertThat(groupRepo.findGroupByName(ORG_ID, GROUP_NAME)).isEmpty();
  }

  @Test
  void findGroupByNameReturnsEmptyWhenBodyIsEmptyList() throws HururaaProblemException {
    stubFindGroupByName(GROUP_NAME, List.of());

    assertThat(groupRepo.findGroupByName(ORG_ID, GROUP_NAME)).isEmpty();
  }

  @Test
  void findGroupByNameWrapsHttpClientErrorExceptionAs500() {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGet(
                REALM,
                ORG_ID,
                Optional.empty(),
                Optional.of(true),
                Optional.of(0),
                Optional.of(1),
                Optional.empty(),
                Optional.empty(),
                Optional.of(GROUP_NAME),
                Optional.empty())).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.findGroupByName(ORG_ID, GROUP_NAME))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- saveGroup (create) ----

  @Test
  void saveGroupCreatesGroupAndReturnsItsRepresentation() throws HururaaProblemException {
    final var groupToCreate = new GroupRepresentation().name(GROUP_NAME);
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsPost(REALM, ORG_ID, Optional.of(groupToCreate)))
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    final var created = new GroupRepresentation().id("generated-group-1").name(GROUP_NAME);
    stubFindGroupByName(GROUP_NAME, List.of(created));

    final var result = groupRepo.saveGroup(ORG_ID, groupToCreate);

    assertThat(result).isEqualTo(created);
  }

  /**
   * {@code @CachePut(key = "#orgId + '_' + #result.name")} primes the {@code organizationGroups}
   * cache under the created group's name. A subsequent {@code findGroupByName} for that name
   * should therefore be served from the cache instead of hitting the API again.
   */
  @Test
  void saveGroupByItselfPrimesTheCacheUnderTheCreatedGroupsName() throws HururaaProblemException {
    final var groupToCreate = new GroupRepresentation().name(GROUP_NAME);
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsPost(REALM, ORG_ID, Optional.of(groupToCreate)))
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    final var created = new GroupRepresentation().id("generated-group-1").name(GROUP_NAME);
    stubFindGroupByName(GROUP_NAME, List.of(created));

    groupRepo.saveGroup(ORG_ID, groupToCreate);
    final var found = groupRepo.findGroupByName(ORG_ID, GROUP_NAME);

    assertThat(found).contains(created);
    verify(organizationsApi, times(1))
        .adminRealmsRealmOrganizationsOrgIdGroupsGet(
            REALM,
            ORG_ID,
            Optional.empty(),
            Optional.of(true),
            Optional.of(0),
            Optional.of(1),
            Optional.empty(),
            Optional.empty(),
            Optional.of(GROUP_NAME),
            Optional.empty());
  }

  /**
   * {@code RoleService.save} looks the group up before creating it, which caches an empty
   * {@code Optional} under the group's name. The read {@code saveGroup} makes right after the
   * {@code POST} must not be served that stale entry, or the creation fails with
   * {@code IDENTITY_PROVIDER_ERROR} and every retry gets a {@code 409 Conflict} from Keycloak
   * until the cache entry expires.
   */
  @Test
  void saveGroupCreatesGroupEvenWhenAnEmptyLookupWasCachedForItsName() throws HururaaProblemException {
    final var groupToCreate = new GroupRepresentation().name(GROUP_NAME);
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsPost(REALM, ORG_ID, Optional.of(groupToCreate)))
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    final var created = new GroupRepresentation().id("generated-group-1").name(GROUP_NAME);
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGet(
                REALM,
                ORG_ID,
                Optional.empty(),
                Optional.of(true),
                Optional.of(0),
                Optional.of(1),
                Optional.empty(),
                Optional.empty(),
                Optional.of(GROUP_NAME),
                Optional.empty()))
        .thenReturn(ResponseEntity.ok(List.of()), ResponseEntity.ok(List.of(created)));

    assertThat(groupRepo.findGroupByName(ORG_ID, GROUP_NAME)).isEmpty();

    final var result = groupRepo.saveGroup(ORG_ID, groupToCreate);

    assertThat(result).isEqualTo(created);
    // and the stale empty entry has been replaced by the created group
    assertThat(groupRepo.findGroupByName(ORG_ID, GROUP_NAME)).contains(created);
  }

  @Test
  void saveGroupCreateThrows500WhenCreatedGroupCannotBeRetrieved() {
    final var groupToCreate = new GroupRepresentation().name(GROUP_NAME);
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsPost(REALM, ORG_ID, Optional.of(groupToCreate)))
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    stubFindGroupByName(GROUP_NAME, List.of());

    assertThatThrownBy(() -> groupRepo.saveGroup(ORG_ID, groupToCreate))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  @Test
  void saveGroupCreateWrapsHttpClientErrorExceptionAs500() {
    final var groupToCreate = new GroupRepresentation().name(GROUP_NAME);
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsPost(REALM, ORG_ID, Optional.of(groupToCreate)))
        .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> groupRepo.saveGroup(ORG_ID, groupToCreate))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- saveGroup (update) ----

  @Test
  void saveGroupUpdatesAndReturnsTheGivenGroupWhenIdIsPresent() throws HururaaProblemException {
    final var groupToUpdate = new GroupRepresentation().id(GROUP_ID).name("Engineering renamed");
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdPut(REALM, ORG_ID, GROUP_ID, Optional.of(groupToUpdate)))
        .thenReturn(ResponseEntity.noContent().build());

    final var result = groupRepo.saveGroup(ORG_ID, groupToUpdate);

    assertThat(result).isEqualTo(groupToUpdate);
    verify(organizationsApi, never()).adminRealmsRealmOrganizationsOrgIdGroupsPost(any(), any(), any());
  }

  @Test
  void saveGroupUpdatePrimesTheCacheUnderTheGroupsName() throws HururaaProblemException {
    final var groupToUpdate = new GroupRepresentation().id(GROUP_ID).name("Engineering renamed");
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdPut(REALM, ORG_ID, GROUP_ID, Optional.of(groupToUpdate)))
        .thenReturn(ResponseEntity.noContent().build());

    groupRepo.saveGroup(ORG_ID, groupToUpdate);
    final var found = groupRepo.findGroupByName(ORG_ID, "Engineering renamed");

    assertThat(found).contains(groupToUpdate);
    verify(organizationsApi, never())
        .adminRealmsRealmOrganizationsOrgIdGroupsGet(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void saveGroupUpdateWrapsHttpClientErrorExceptionAs500() {
    final var groupToUpdate = new GroupRepresentation().id(GROUP_ID).name("Engineering renamed");
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdPut(REALM, ORG_ID, GROUP_ID, Optional.of(groupToUpdate)))
        .thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.saveGroup(ORG_ID, groupToUpdate))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- deleteGroup ----

  @Test
  void deleteGroupCallsTheDeleteEndpointAndEvictsTheCache() throws HururaaProblemException {
    final var group = new GroupRepresentation().id(GROUP_ID).name(GROUP_NAME);
    stubFindGroupByName(GROUP_NAME, List.of(group));
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdDelete(REALM, ORG_ID, GROUP_ID))
        .thenReturn(ResponseEntity.noContent().build());

    groupRepo.findGroupByName(ORG_ID, GROUP_NAME);
    groupRepo.deleteGroup(ORG_ID, GROUP_ID);
    groupRepo.findGroupByName(ORG_ID, GROUP_NAME);

    verify(organizationsApi, times(1)).adminRealmsRealmOrganizationsOrgIdGroupsGroupIdDelete(REALM, ORG_ID, GROUP_ID);
    verify(organizationsApi, times(2))
        .adminRealmsRealmOrganizationsOrgIdGroupsGet(
            REALM,
            ORG_ID,
            Optional.empty(),
            Optional.of(true),
            Optional.of(0),
            Optional.of(1),
            Optional.empty(),
            Optional.empty(),
            Optional.of(GROUP_NAME),
            Optional.empty());
  }

  @Test
  void deleteGroupWrapsHttpClientErrorExceptionAs500() {
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdDelete(REALM, ORG_ID, GROUP_ID))
        .thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.deleteGroup(ORG_ID, GROUP_ID))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- findGroupMembers ----

  @Test
  void findGroupMembersOnPartialPageDoesNotAssumeMoreResults() throws HururaaProblemException {
    final var member = new MemberRepresentation().id(USER_ID).username("jdoe");
    stubFindGroupMembers(0, 2, List.of(member));

    final var page = groupRepo.findGroupMembers(ORG_ID, GROUP_ID, PageRequest.of(0, 2));

    assertThat(page.getContent()).containsExactly(member);
    assertThat(page.getTotalElements()).isEqualTo(1);
  }

  @Test
  void findGroupMembersOnFullPageAssumesThereAreMoreResults() throws HururaaProblemException {
    final var member1 = new MemberRepresentation().id("user-1").username("jdoe");
    final var member2 = new MemberRepresentation().id("user-2").username("jroe");
    stubFindGroupMembers(0, 2, List.of(member1, member2));

    final var page = groupRepo.findGroupMembers(ORG_ID, GROUP_ID, PageRequest.of(0, 2));

    assertThat(page.getContent()).containsExactly(member1, member2);
    // offset(0) + size(2) + 1 (there might be more): total is an optimistic estimate, not an exact count
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void findGroupMembersOnNonFirstFullPageAccountsForThePriorOffset() throws HururaaProblemException {
    final var member1 = new MemberRepresentation().id("user-3").username("jsmith");
    final var member2 = new MemberRepresentation().id("user-4").username("jblack");
    stubFindGroupMembers(2, 2, List.of(member1, member2));

    final var page = groupRepo.findGroupMembers(ORG_ID, GROUP_ID, PageRequest.of(1, 2));

    assertThat(page.getContent()).containsExactly(member1, member2);
    // offset(2) + size(2) + 1 (there might be more)
    assertThat(page.getTotalElements()).isEqualTo(5);
  }

  @Test
  void findGroupMembersTreatsNullBodyAsEmptyPage() throws HururaaProblemException {
    stubFindGroupMembers(0, 2, null);

    final var page = groupRepo.findGroupMembers(ORG_ID, GROUP_ID, PageRequest.of(0, 2));

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isZero();
  }

  @Test
  void findGroupMembersWrapsHttpClientErrorExceptionAs500() {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersGet(
                REALM,
                ORG_ID,
                GROUP_ID,
                Optional.empty(),
                Optional.of(0),
                Optional.of(2))).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.findGroupMembers(ORG_ID, GROUP_ID, PageRequest.of(0, 2)))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- findMemberGroups ----

  @Test
  void findMemberGroupsReturnsTheMembersGroups() throws HururaaProblemException {
    final var group = new GroupRepresentation().id(GROUP_ID).name(GROUP_NAME);
    stubFindMemberGroups(List.of(group));

    assertThat(groupRepo.findMemberGroups(ORG_ID, USER_ID)).containsExactly(group);
  }

  @Test
  void findMemberGroupsTreatsNullBodyAsEmptyList() throws HururaaProblemException {
    stubFindMemberGroups(null);

    assertThat(groupRepo.findMemberGroups(ORG_ID, USER_ID)).isEmpty();
  }

  @Test
  void findMemberGroupsMapsNotFoundAs404WhenUserIsNotAMember() {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdMembersMemberIdGroupsGet(
                REALM,
                ORG_ID,
                USER_ID,
                Optional.empty(),
                Optional.of(0),
                Optional.of(Integer.MAX_VALUE),
                Optional.empty()))
        .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

    assertThatThrownBy(() -> groupRepo.findMemberGroups(ORG_ID, USER_ID))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.NOT_A_MEMBER);
  }

  @Test
  void findMemberGroupsWrapsOtherHttpClientErrorExceptionAs500() {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdMembersMemberIdGroupsGet(
                REALM,
                ORG_ID,
                USER_ID,
                Optional.empty(),
                Optional.of(0),
                Optional.of(Integer.MAX_VALUE),
                Optional.empty()))
        .thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.findMemberGroups(ORG_ID, USER_ID))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- addGroupMember ----

  @Test
  void addGroupMemberCallsTheAddEndpoint() throws HururaaProblemException {
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersUserIdPut(REALM, ORG_ID, GROUP_ID, USER_ID))
        .thenReturn(ResponseEntity.noContent().build());

    groupRepo.addGroupMember(ORG_ID, GROUP_ID, USER_ID);

    verify(organizationsApi, times(1))
        .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersUserIdPut(REALM, ORG_ID, GROUP_ID, USER_ID);
  }

  @Test
  void addGroupMemberWrapsHttpClientErrorExceptionAs500() {
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersUserIdPut(REALM, ORG_ID, GROUP_ID, USER_ID))
        .thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.addGroupMember(ORG_ID, GROUP_ID, USER_ID))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- removeGroupMember ----

  @Test
  void removeGroupMemberCallsTheRemoveEndpoint() throws HururaaProblemException {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersUserIdDelete(REALM, ORG_ID, GROUP_ID, USER_ID))
        .thenReturn(ResponseEntity.noContent().build());

    groupRepo.removeGroupMember(ORG_ID, GROUP_ID, USER_ID);

    verify(organizationsApi, times(1))
        .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersUserIdDelete(REALM, ORG_ID, GROUP_ID, USER_ID);
  }

  @Test
  void removeGroupMemberWrapsHttpClientErrorExceptionAs500() {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdMembersUserIdDelete(REALM, ORG_ID, GROUP_ID, USER_ID))
        .thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.removeGroupMember(ORG_ID, GROUP_ID, USER_ID))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  /**
   * {@code findGroups} is cached: creating a group must evict that list, or the new group would be
   * missing from it until the cache expires.
   */
  @Test
  void saveGroupEvictsTheCachedGroupList() throws HururaaProblemException {
    final var existing = new GroupRepresentation().id(GROUP_ID).name(GROUP_NAME);
    final var toCreate = new GroupRepresentation().name("Sales");
    final var created = new GroupRepresentation().id("generated-group-1").name("Sales");
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdGroupsGet(
                REALM,
                ORG_ID,
                Optional.empty(),
                Optional.of(false),
                Optional.of(0),
                Optional.of(Integer.MAX_VALUE),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()))
        .thenReturn(ResponseEntity.ok(List.of(existing)))
        .thenReturn(ResponseEntity.ok(List.of(existing, created)));
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGroupsPost(REALM, ORG_ID, Optional.of(toCreate)))
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    stubFindGroupByName("Sales", List.of(created));

    assertThat(groupRepo.findGroups(ORG_ID)).containsExactly(existing);
    groupRepo.saveGroup(ORG_ID, toCreate);

    assertThat(groupRepo.findGroups(ORG_ID)).containsExactly(existing, created);
  }

  // ---- findClientRolesByGroupId ----

  @Test
  void findClientRolesByGroupIdUsesTheOrganizationEndpointAndCachesTheResult()
      throws HururaaProblemException {
    when(
        clientRoleMappingsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdGet(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID)).thenReturn(ResponseEntity.ok(List.of(READ)));

    final var first = groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID);
    final var second = groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID);

    assertThat(first).containsExactly(READ);
    assertThat(second).containsExactly(READ);
    verify(clientRoleMappingsApi, times(1))
        .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdGet(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID);
    verify(clientRoleMappingsApi, never())
        .adminRealmsRealmGroupsGroupIdRoleMappingsClientsClientIdGet(any(), any(), any());
  }

  @Test
  void findClientRolesByGroupIdReturnsEmptyListWhenBodyIsNull() throws HururaaProblemException {
    when(
        clientRoleMappingsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdGet(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID)).thenReturn(ResponseEntity.ok(null));

    assertThat(groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID)).isEmpty();
  }

  @Test
  void findClientRolesByGroupIdWrapsHttpClientErrorExceptionAs500() {
    when(
        clientRoleMappingsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdGet(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID)).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- addClientRoleToGroup ----

  @Test
  void addClientRoleToGroupUsesTheOrganizationEndpointAndEvictsTheCache()
      throws HururaaProblemException {
    when(
        clientRoleMappingsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdGet(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID)).thenReturn(ResponseEntity.ok(List.of())).thenReturn(ResponseEntity.ok(List.of(READ)));
    assertThat(groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID)).isEmpty();

    groupRepo.addClientRoleToGroup(CLIENT_ID, ORG_ID, GROUP_ID, READ.getName());

    verify(clientRoleMappingsApi)
        .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdPost(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID,
            Optional.of(List.of(READ)));
    verify(clientRoleMappingsApi, never())
        .adminRealmsRealmGroupsGroupIdRoleMappingsClientsClientIdPost(any(), any(), any(), any());
    assertThat(groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID)).containsExactly(READ);
  }

  @Test
  void addClientRoleToGroupThrows404WhenNoClientRoleMatches() {
    assertThatThrownBy(() -> groupRepo.addClientRoleToGroup(CLIENT_ID, ORG_ID, GROUP_ID, "tenant1.unknown"))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.APPLICATION_ROLE_NOT_FOUND);
    verifyNoInteractions(clientRoleMappingsApi);
  }

  @Test
  void addClientRoleToGroupWrapsHttpClientErrorExceptionAs500() {
    when(
        clientRoleMappingsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdPost(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID,
            Optional.of(List.of(READ)))).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.addClientRoleToGroup(CLIENT_ID, ORG_ID, GROUP_ID, READ.getName()))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- removeClientRoleFromGroup ----

  @Test
  void removeClientRoleFromGroupUsesTheOrganizationEndpointAndEvictsTheCache()
      throws HururaaProblemException {
    when(
        clientRoleMappingsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdGet(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID)).thenReturn(ResponseEntity.ok(List.of(READ))).thenReturn(ResponseEntity.ok(List.of()));
    assertThat(groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID)).containsExactly(READ);

    groupRepo.removeClientRoleFromGroup(CLIENT_ID, ORG_ID, GROUP_ID, READ.getName());

    verify(clientRoleMappingsApi)
        .adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdDelete(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID,
            Optional.of(List.of(READ)));
    verify(clientRoleMappingsApi, never())
        .adminRealmsRealmGroupsGroupIdRoleMappingsClientsClientIdDelete(any(), any(), any(), any());
    assertThat(groupRepo.findClientRolesByGroupId(CLIENT_ID, ORG_ID, GROUP_ID)).isEmpty();
  }

  @Test
  void removeClientRoleFromGroupDoesNothingWhenNoClientRoleMatches() throws HururaaProblemException {
    groupRepo.removeClientRoleFromGroup(CLIENT_ID, ORG_ID, GROUP_ID, "tenant1.unknown");

    verifyNoInteractions(clientRoleMappingsApi);
  }

  @Test
  void removeClientRoleFromGroupWrapsHttpClientErrorExceptionAs500() {
    when(
        clientRoleMappingsApi.adminRealmsRealmOrganizationsOrgIdGroupsGroupIdRoleMappingsClientsClientIdDelete(
            REALM,
            ORG_ID,
            GROUP_ID,
            CLIENT_UUID,
            Optional.of(List.of(READ)))).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> groupRepo.removeClientRoleFromGroup(CLIENT_ID, ORG_ID, GROUP_ID, READ.getName()))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }
}
