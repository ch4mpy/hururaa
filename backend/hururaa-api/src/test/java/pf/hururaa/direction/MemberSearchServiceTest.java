package pf.hururaa.direction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.escales;
import static pf.hururaa.HururaaFixtures.hururaa;
import static pf.hururaa.HururaaFixtures.user;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import pf.hururaa.HururaaFixtures;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.direction.domain.User;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;

/**
 * Narrowed to groups, a member search reads the members of the matching groups (Hurura'a's
 * delegation groups included, as groups of Hurura'a), then filters, deduplicates, sorts and pages
 * them.
 */
class MemberSearchServiceTest {
  private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);

  private static final User ALICE = new User("1", "alice", "Alice", "Teriierooiterai", null);
  private static final User BOB = new User("2", "bob", "Bob", null, "bob@dpam.pf");
  private static final User CAROL = new User("3", "carol", null, null, null);
  private static final User DPAM_ADMIN = user(HururaaFixtures.DPAM_ADMIN, "dpam.admin");
  private static final User DPAM_MANAGER = user(HururaaFixtures.DPAM_MANAGER, "dpam.manager");

  private final DirectionService directionService = mock(DirectionService.class);
  private final GroupService groupService = mock(GroupService.class);
  private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
  private final KeycloakAdminApiProperties keycloakProperties =
      mock(KeycloakAdminApiProperties.class);

  private final MemberSearchService service = new MemberSearchService(directionService,
      groupService, applicationRepository, keycloakProperties);

  private final Application otherApplication =
      Application.builder().id(99L).clientPrefix("other").name("Other").direction(DPAM).build();

  @BeforeEach
  void setUp() throws Exception {
    when(applicationRepository.findAvailableIn(DPAM))
        .thenReturn(List.of(escales(), hururaa(), otherApplication));
    when(keycloakProperties.apiClientId("hururaa")).thenReturn("hururaa-api");
    when(groupService.findAllMembers(DPAM, "hururaa.admins")).thenReturn(List.of(DPAM_ADMIN));
    when(groupService.findAllMembers(DPAM, "hururaa.escales.product-owners"))
        .thenReturn(List.of(DPAM_MANAGER));
    when(groupService.findClientRoles(DPAM, "hururaa.admins", "hururaa-api"))
        .thenReturn(List.of("hururaa.direction.admin"));
    when(groupService.findByName(DPAM, "hururaa.admins"))
        .thenReturn(Optional.of(new Group("g0", DPAM, "hururaa.admins")));
    when(keycloakProperties.apiClientId("escales")).thenReturn("escales-api");
    when(keycloakProperties.apiClientId("other")).thenReturn("other-api");
    when(groupService.findAll(DPAM)).thenReturn(List.of(
        new Group("g0", DPAM, "hururaa.admins"),
        new Group("g4", DPAM, "hururaa.escales.product-owners"),
        new Group("g1", DPAM, "escales.agent"),
        new Group("g2", DPAM, "escales.chef"),
        new Group("g3", DPAM, "other.agent")));
    when(groupService.findByName(DPAM, "escales.agent"))
        .thenReturn(Optional.of(new Group("g1", DPAM, "escales.agent")));
    when(groupService.findAllMembers(DPAM, "escales.agent")).thenReturn(List.of(BOB, ALICE));
    when(groupService.findAllMembers(DPAM, "escales.chef")).thenReturn(List.of(ALICE));
    when(groupService.findAllMembers(DPAM, "other.agent")).thenReturn(List.of(CAROL));
    when(groupService.findClientRoles(DPAM, "escales.agent", "escales-api"))
        .thenReturn(List.of("escales.read"));
    when(groupService.findClientRoles(DPAM, "escales.chef", "escales-api"))
        .thenReturn(List.of("escales.read", "escales.write"));
    when(groupService.findClientRoles(DPAM, "other.agent", "other-api"))
        .thenReturn(List.of("other.read"));
  }

  @Test
  void givenNoNarrowing_whenSearch_thenKeycloakSearchesTheDirection() throws Exception {
    final var page = new PageImpl<>(List.of(ALICE), FIRST_PAGE, 1);
    when(directionService.searchMembers(DPAM, "ali", FIRST_PAGE)).thenReturn(page);

    assertThat(service.search(DPAM, new MemberFilter("ali", null, null, null), FIRST_PAGE))
        .isSameAs(page);
    verify(groupService, never()).findAllMembers(anyString(), anyString());
  }

  @Test
  void givenGroup_whenSearch_thenItsMembersSortedByUsername() throws Exception {
    final var page =
        service.search(DPAM, new MemberFilter("", "escales.agent", null, null), FIRST_PAGE);

    assertThat(page.getContent()).containsExactly(ALICE, BOB);
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void givenGroupAndSearch_whenSearch_thenMembersContainingIt() throws Exception {
    assertThat(service
        .search(DPAM, new MemberFilter("DPAM.PF", "escales.agent", null, null), FIRST_PAGE)
        .getContent()).containsExactly(BOB);
  }

  @Test
  void givenApplication_whenSearch_thenMembersOfItsGroupsOnce() throws Exception {
    assertThat(service.search(DPAM, new MemberFilter("", null, escales(), null), FIRST_PAGE)
        .getContent()).containsExactly(ALICE, BOB);
  }

  @Test
  void givenRole_whenSearch_thenMembersOfTheGroupsGrantingIt() throws Exception {
    assertThat(service.search(DPAM, new MemberFilter("", null, null, "escales.write"), FIRST_PAGE)
        .getContent()).containsExactly(ALICE);
    assertThat(service.search(DPAM, new MemberFilter("", null, null, "other.read"), FIRST_PAGE)
        .getContent()).containsExactly(CAROL);
  }

  @Test
  void givenRoleOfAnotherApplication_whenSearch_thenNobody() throws Exception {
    assertThat(service
        .search(DPAM, new MemberFilter("", null, escales(), "other.read"), FIRST_PAGE)
        .getContent()).isEmpty();
  }

  @Test
  void givenSecondPage_whenSearch_thenTheRemainingMembers() throws Exception {
    final var page = service.search(DPAM, new MemberFilter("", null, escales(), null),
        PageRequest.of(1, 1));

    assertThat(page.getContent()).containsExactly(BOB);
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void givenDirectionAdminRole_whenSearchDpam_thenDpamAdminFound() throws Exception {
    assertThat(service
        .search(DPAM, new MemberFilter("", null, null, "hururaa.direction.admin"), FIRST_PAGE)
        .getContent()).containsExactly(DPAM_ADMIN);
  }

  @Test
  void givenDelegationGroup_whenSearch_thenItsMembers() throws Exception {
    assertThat(service
        .search(DPAM, new MemberFilter("", "hururaa.admins", null, null), FIRST_PAGE)
        .getContent()).containsExactly(DPAM_ADMIN);
  }

  @Test
  void givenHururaa_whenSearchDpam_thenMembersOfItsGroupsInDpam() throws Exception {
    // CA-11: the direction's administrators and the managers of its applications
    assertThat(service.search(DPAM, new MemberFilter("", null, hururaa(), null), FIRST_PAGE)
        .getContent()).containsExactly(DPAM_ADMIN, DPAM_MANAGER);
  }

  @Test
  void givenUnknownGroup_whenSearch_thenNotFound() {
    assertThatThrownBy(() -> service
        .search(DPAM, new MemberFilter("", "escales.nobody", null, null), FIRST_PAGE))
        .isInstanceOfSatisfying(HururaaProblemException.class,
            e -> assertThat(e.getType()).isEqualTo(ProblemType.GROUP_NOT_FOUND));
  }
}
