package pf.hururaa.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.api.OrganizationsApi;
import org.keycloak.admin.model.MemberRepresentation;
import org.keycloak.admin.model.OrganizationRepresentation;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.HttpClientErrorException;

import pf.hururaa.CacheConfiguration;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.problem.HururaaProblemException;

@TestPropertySource(properties = "spring.cache.type=caffeine")
@SpringJUnitConfig(classes = CachingKeycloakOrganizationRepositoryTest.TestConfig.class)
class CachingKeycloakOrganizationRepositoryTest {

  static final String REALM = "hururaa";
  static final String ORG_ID = "org-1";
  static final String ORG_NAME = "Tahiti Numerique";
  static final String USER_ID = "user-1";

  @Configuration
  @ImportAutoConfiguration(CacheAutoConfiguration.class)
  @Import({CacheConfiguration.class, CachingKeycloakOrganizationRepository.class})
  static class TestConfig {
    @Bean
    KeycloakAdminApiProperties keycloakAdminApiProperties() {
      return new KeycloakAdminApiProperties(REALM, "-api", "-bff", List.of("view-users"));
    }
  }

  @MockitoBean
  OrganizationsApi organizationsApi;

  @Autowired
  CachingKeycloakOrganizationRepository organizationService;

  @Autowired
  CacheManager cacheManager;

  @BeforeEach
  void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  private void stubFindById(OrganizationRepresentation org) {
    when(organizationsApi.adminRealmsRealmOrganizationsOrgIdGet(REALM, ORG_ID)).thenReturn(ResponseEntity.ok(org));
  }

  private void stubFindByName(List<OrganizationRepresentation> body) {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsGet(
                REALM,
                Optional.empty(),
                Optional.of(true),
                Optional.empty(),
                Optional.of(1),
                Optional.empty(),
                Optional.of(ORG_NAME))).thenReturn(ResponseEntity.ok(body));
  }

  private void stubSearch(int first, int max, List<OrganizationRepresentation> body) {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsGet(
                REALM,
                Optional.empty(),
                Optional.of(false),
                Optional.of(first),
                Optional.of(max),
                Optional.empty(),
                Optional.of(ORG_NAME))).thenReturn(ResponseEntity.ok(body));
  }

  private void stubCount(Long count) {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsCountGet(REALM, Optional.empty(), Optional.empty(), Optional.of(ORG_NAME)))
        .thenReturn(ResponseEntity.ok(count));
  }

  private void stubFindMembers(int first, int max, Optional<String> search, List<MemberRepresentation> body) {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdMembersGet(
                REALM,
                ORG_ID,
                Optional.empty(),
                Optional.empty(),
                Optional.of(first),
                Optional.of(max),
                Optional.empty(),
                search)).thenReturn(ResponseEntity.ok(body));
  }

  // ---- findById ----




  // ---- findByName ----

  @Test
  void findByNameReturnsOrganizationWhenPresentAndCachesResult() throws HururaaProblemException {
    final var org = new OrganizationRepresentation().id(ORG_ID).name(ORG_NAME);
    stubFindByName(List.of(org));

    final var first = organizationService.findByName(ORG_NAME);
    final var second = organizationService.findByName(ORG_NAME);

    assertThat(first).contains(org);
    assertThat(second).contains(org);
    verify(organizationsApi, times(1))
        .adminRealmsRealmOrganizationsGet(
            REALM,
            Optional.empty(),
            Optional.of(true),
            Optional.empty(),
            Optional.of(1),
            Optional.empty(),
            Optional.of(ORG_NAME));
  }

  @Test
  void findByNameReturnsEmptyWhenBodyIsNull() throws HururaaProblemException {
    stubFindByName(null);

    assertThat(organizationService.findByName(ORG_NAME)).isEmpty();
  }

  @Test
  void findByNameReturnsEmptyWhenBodyIsEmptyList() throws HururaaProblemException {
    stubFindByName(List.of());

    assertThat(organizationService.findByName(ORG_NAME)).isEmpty();
  }

  @Test
  void findByNameWrapsHttpClientErrorExceptionAs500() {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsGet(
                REALM,
                Optional.empty(),
                Optional.of(true),
                Optional.empty(),
                Optional.of(1),
                Optional.empty(),
                Optional.of(ORG_NAME))).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> organizationService.findByName(ORG_NAME))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- findAllByNameContaining ----

  @Test
  void searchOnFirstPageWithFewerResultsThanPageSizeSkipsCountCall() throws HururaaProblemException {
    final var org = new OrganizationRepresentation().id(ORG_ID).name(ORG_NAME);
    stubSearch(0, 10, List.of(org));

    final var page = organizationService.findAllByNameContaining(ORG_NAME, PageRequest.of(0, 10));

    assertThat(page.getContent()).containsExactly(org);
    assertThat(page.getTotalElements()).isEqualTo(1);
    verify(organizationsApi, never())
        .adminRealmsRealmOrganizationsCountGet(any(), any(), any(), any());
  }

  @Test
  void searchOnFirstFullPageAsksForTotalCount() throws HururaaProblemException {
    final var org1 = new OrganizationRepresentation().id("org-1").name(ORG_NAME);
    final var org2 = new OrganizationRepresentation().id("org-2").name(ORG_NAME);
    stubSearch(0, 2, List.of(org1, org2));
    stubCount(5L);

    final var page = organizationService.findAllByNameContaining(ORG_NAME, PageRequest.of(0, 2));

    assertThat(page.getTotalElements()).isEqualTo(5);
  }

  @Test
  void searchOnNonFirstPageAlwaysAsksForTotalCountEvenIfPartial() throws HururaaProblemException {
    final var org = new OrganizationRepresentation().id(ORG_ID).name(ORG_NAME);
    stubSearch(10, 10, List.of(org));
    stubCount(11L);

    final var page = organizationService.findAllByNameContaining(ORG_NAME, PageRequest.of(1, 10));

    assertThat(page.getTotalElements()).isEqualTo(11);
  }

  @Test
  void searchTreatsNullBodyAsEmptyPage() throws HururaaProblemException {
    stubSearch(0, 10, null);

    final var page = organizationService.findAllByNameContaining(ORG_NAME, PageRequest.of(0, 10));

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isZero();
  }

  @Test
  void searchWrapsHttpClientErrorExceptionAs500() {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsGet(
                REALM,
                Optional.empty(),
                Optional.of(false),
                Optional.of(0),
                Optional.of(10),
                Optional.empty(),
                Optional.of(ORG_NAME))).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> organizationService.findAllByNameContaining(ORG_NAME, PageRequest.of(0, 10)))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
    verify(organizationsApi, never())
        .adminRealmsRealmOrganizationsCountGet(any(), any(), any(), any());
  }

  /**
   * The cache key must include the page, not just the org name: two different pages for the same
   * search must not collide in the cache (regression test for a bug where the key was
   * {@code "#orgName"} only).
   */
  @Test
  void searchCachesResultsPerPageRatherThanJustByName() throws HururaaProblemException {
    final var org1 = new OrganizationRepresentation().id("org-1").name(ORG_NAME);
    final var org2 = new OrganizationRepresentation().id("org-2").name(ORG_NAME);
    stubSearch(0, 2, List.of(org1));
    stubSearch(2, 2, List.of(org2));
    stubCount(3L);

    final var firstPageAgain = organizationService.findAllByNameContaining(ORG_NAME, PageRequest.of(0, 2));
    final var firstPageCached = organizationService.findAllByNameContaining(ORG_NAME, PageRequest.of(0, 2));
    final var secondPage = organizationService.findAllByNameContaining(ORG_NAME, PageRequest.of(1, 2));

    assertThat(firstPageAgain.getContent()).containsExactly(org1);
    assertThat(firstPageCached.getContent()).containsExactly(org1);
    assertThat(secondPage.getContent()).containsExactly(org2);
    verify(organizationsApi, times(1))
        .adminRealmsRealmOrganizationsGet(
            REALM,
            Optional.empty(),
            Optional.of(false),
            Optional.of(0),
            Optional.of(2),
            Optional.empty(),
            Optional.of(ORG_NAME));
    verify(organizationsApi, times(1))
        .adminRealmsRealmOrganizationsGet(
            REALM,
            Optional.empty(),
            Optional.of(false),
            Optional.of(2),
            Optional.of(2),
            Optional.empty(),
            Optional.of(ORG_NAME));
  }

  // ---- countOrgs ----

  @Test
  void countOrgsReturnsBody() throws HururaaProblemException {
    stubCount(7L);

    assertThat(organizationService.countOrgs(ORG_NAME)).isEqualTo(7L);
  }

  @Test
  void countOrgsReturnsZeroWhenBodyIsNull() throws HururaaProblemException {
    stubCount(null);

    assertThat(organizationService.countOrgs(ORG_NAME)).isZero();
  }

  @Test
  void countOrgsWrapsHttpClientErrorExceptionAs500() {
    when(organizationsApi.adminRealmsRealmOrganizationsCountGet(REALM, Optional.empty(), Optional.empty(), Optional.of(ORG_NAME)))
        .thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> organizationService.countOrgs(ORG_NAME))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }

  // ---- findMembers ----

  @Test
  void findMembersOnPartialPageDoesNotAssumeMoreResults() throws HururaaProblemException {
    final var member = new MemberRepresentation().id(USER_ID).username("jdoe");
    stubFindMembers(0, 2, Optional.empty(), List.of(member));

    final var page = organizationService.findMembers(ORG_ID, null, PageRequest.of(0, 2));

    assertThat(page.getContent()).containsExactly(member);
    assertThat(page.getTotalElements()).isEqualTo(1);
  }

  @Test
  void findMembersOnFullPageAssumesThereAreMoreResults() throws HururaaProblemException {
    final var member1 = new MemberRepresentation().id("user-1").username("jdoe");
    final var member2 = new MemberRepresentation().id("user-2").username("jroe");
    stubFindMembers(0, 2, Optional.empty(), List.of(member1, member2));

    final var page = organizationService.findMembers(ORG_ID, null, PageRequest.of(0, 2));

    assertThat(page.getContent()).containsExactly(member1, member2);
    // offset(0) + size(2) + 1 (there might be more): total is an optimistic estimate, not an exact count
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void findMembersOnNonFirstFullPageAccountsForThePriorOffset() throws HururaaProblemException {
    final var member1 = new MemberRepresentation().id("user-3").username("jsmith");
    final var member2 = new MemberRepresentation().id("user-4").username("jblack");
    stubFindMembers(2, 2, Optional.empty(), List.of(member1, member2));

    final var page = organizationService.findMembers(ORG_ID, null, PageRequest.of(1, 2));

    assertThat(page.getContent()).containsExactly(member1, member2);
    // offset(2) + size(2) + 1 (there might be more)
    assertThat(page.getTotalElements()).isEqualTo(5);
  }

  @Test
  void findMembersPassesABlankOrNullSearchAsEmpty() throws HururaaProblemException {
    final var member = new MemberRepresentation().id(USER_ID).username("jdoe");
    stubFindMembers(0, 10, Optional.empty(), List.of(member));

    final var withNull = organizationService.findMembers(ORG_ID, null, PageRequest.of(0, 10));
    final var withBlank = organizationService.findMembers(ORG_ID, "  ", PageRequest.of(0, 10));

    assertThat(withNull.getContent()).containsExactly(member);
    assertThat(withBlank.getContent()).containsExactly(member);
  }

  @Test
  void findMembersPassesANonBlankSearchThrough() throws HururaaProblemException {
    final var member = new MemberRepresentation().id(USER_ID).username("jdoe");
    stubFindMembers(0, 10, Optional.of("doe"), List.of(member));

    final var page = organizationService.findMembers(ORG_ID, "doe", PageRequest.of(0, 10));

    assertThat(page.getContent()).containsExactly(member);
  }

  @Test
  void findMembersTreatsNullBodyAsEmptyPage() throws HururaaProblemException {
    stubFindMembers(0, 2, Optional.empty(), null);

    final var page = organizationService.findMembers(ORG_ID, null, PageRequest.of(0, 2));

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isZero();
  }

  @Test
  void findMembersWrapsHttpClientErrorExceptionAs500() {
    when(
        organizationsApi
            .adminRealmsRealmOrganizationsOrgIdMembersGet(
                REALM,
                ORG_ID,
                Optional.empty(),
                Optional.empty(),
                Optional.of(0),
                Optional.of(2),
                Optional.empty(),
                Optional.empty())).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> organizationService.findMembers(ORG_ID, null, PageRequest.of(0, 2)))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.IDENTITY_PROVIDER_ERROR);
  }


  @Test
  void givenDuplicate_whenCreate_thenDirectionAlreadyExists() {
    when(organizationsApi.adminRealmsRealmOrganizationsPost(any(), any()))
        .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null,
            null));

    assertThatThrownBy(() -> organizationService
        .create(new OrganizationRepresentation().alias("dpam").name("DPAM")))
        .isInstanceOf(HururaaProblemException.class)
        .extracting(e -> ((HururaaProblemException) e).getType())
        .isEqualTo(ProblemType.DIRECTION_ALREADY_EXISTS);
  }

  @Test
  void whenCreate_thenEnabledAndTheCachedOrganizationsEvicted() throws Exception {
    final var cache =
        cacheManager.getCache(CachingKeycloakOrganizationRepository.ORGANIZATIONS_CACHE);
    cache.put("*", List.of());

    organizationService.create(new OrganizationRepresentation().alias("dsp").name("DSP"));

    verify(organizationsApi).adminRealmsRealmOrganizationsPost(REALM,
        Optional.of(new OrganizationRepresentation().alias("dsp").name("DSP").enabled(true)));
    assertThat(cache.get("*")).isNull();
  }
}
