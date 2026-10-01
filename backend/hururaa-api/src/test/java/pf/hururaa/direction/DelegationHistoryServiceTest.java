package pf.hururaa.direction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static pf.hururaa.HururaaFixtures.SIPF_ADMIN;
import static pf.hururaa.HururaaFixtures.user;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.direction.domain.DelegationChange;
import pf.hururaa.direction.domain.DelegationChange.Change;
import pf.hururaa.direction.domain.DelegationChange.Delegation;
import pf.hururaa.direction.domain.DirectionAdmin;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.uaa.UaaProperties;

/**
 * Replays real Envers revisions: each change commits its own transaction (Envers writes the audit
 * trail on commit), with the author in the security context. Every test works in directions of its
 * own, the audit rows outliving it.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(DelegationHistoryService.class)
class DelegationHistoryServiceTest {
  static final String PLATFORM = "sipf";

  @Autowired
  DelegationHistoryService service;

  @Autowired
  DirectionAdminRepository directionAdminRepository;

  @Autowired
  ApplicationRepository applicationRepository;

  @Autowired
  PlatformTransactionManager transactionManager;

  @MockitoBean
  DirectionService directionService;

  @MockitoBean
  UaaProperties uaaProperties;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void givenAdminDesignatedThenRevoked_whenFindByDirection_thenBothChangesNewestFirst()
      throws Exception {
    when(uaaProperties.getPlatformOrganization()).thenReturn(PLATFORM);
    when(directionService.findMember(PLATFORM, SIPF_ADMIN))
        .thenReturn(Optional.of(user(SIPF_ADMIN, "sipf.admin")));
    when(directionService.findMember("hist-admins", "admin-1"))
        .thenReturn(Optional.of(user("admin-1", "admin.one")));
    final var admin = commitAs(SIPF_ADMIN, () -> directionAdminRepository
        .save(DirectionAdmin.builder().direction("hist-admins").userId("admin-1").build()));
    commitAs(SIPF_ADMIN, () -> {
      directionAdminRepository.deleteById(admin.getId());
      return null;
    });

    final var actual = history("hist-admins", PageRequest.of(0, 20));

    assertThat(actual.getContent())
        .extracting(DelegationChange::delegation, DelegationChange::change,
            change -> change.delegate().username(), change -> change.author().username())
        .containsExactly(
            tuple(Delegation.DIRECTION_ADMIN, Change.REVOKED, "admin.one", "sipf.admin"),
            tuple(Delegation.DIRECTION_ADMIN, Change.GRANTED, "admin.one", "sipf.admin"));
    assertThat(actual.getContent().get(0).revision())
        .isGreaterThan(actual.getContent().get(1).revision());
  }

  @Test
  void givenManagersThenApplicationMoved_whenFindByDirection_thenRevokedInFormerDirectionOnly() {
    final var application = commitAs("dir-admin", () -> applicationRepository.save(Application
        .builder()
        .clientPrefix("hist-app")
        .name("Hist App")
        .direction("hist-from")
        .managers(new HashSet<>(Set.of("manager-1")))
        .build()));
    final var withSecondManager = commitAs("dir-admin", () -> {
      final var current = applicationRepository.findById(application.getId()).orElseThrow();
      current.getManagers().add("manager-2");
      return applicationRepository.save(current);
    });
    commitAs("platform-admin", () -> {
      final var current = applicationRepository.findById(withSecondManager.getId()).orElseThrow();
      current.setDirection("hist-to");
      current.getManagers().clear();
      return applicationRepository.save(current);
    });

    assertThat(history("hist-from", PageRequest.of(0, 20)).getContent())
        .extracting(DelegationChange::change, change -> change.delegate().id(),
            change -> change.author().id(), DelegationChange::applicationName)
        .containsExactly(
            tuple(Change.REVOKED, "manager-1", "platform-admin", "Hist App"),
            tuple(Change.REVOKED, "manager-2", "platform-admin", "Hist App"),
            tuple(Change.GRANTED, "manager-2", "dir-admin", "Hist App"),
            tuple(Change.GRANTED, "manager-1", "dir-admin", "Hist App"));
    assertThat(history("hist-to", PageRequest.of(0, 20)).getContent()).isEmpty();
  }

  @Test
  void givenThreeChanges_whenFindSecondPageOfOne_thenMiddleChangeAndTotal() {
    for (final var userId : new String[] {"paged-1", "paged-2", "paged-3"}) {
      commitAs(SIPF_ADMIN, () -> directionAdminRepository
          .save(DirectionAdmin.builder().direction("hist-paged").userId(userId).build()));
    }

    final var actual = history("hist-paged", PageRequest.of(1, 1));

    assertThat(actual.getTotalElements()).isEqualTo(3);
    assertThat(actual.getContent())
        .extracting(change -> change.delegate().id())
        .containsExactly("paged-2");
  }

  private <T> T commitAs(String userId, Supplier<T> change) {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(userId,
        null));
    try {
      return new TransactionTemplate(transactionManager).execute(_ -> change.get());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private Page<DelegationChange> history(String direction, PageRequest pageable) {
    final var template = new TransactionTemplate(transactionManager);
    template.setReadOnly(true);
    return template.execute(_ -> {
      try {
        return service.findByDirection(direction, pageable);
      } catch (HururaaProblemException e) {
        throw new IllegalStateException(e);
      }
    });
  }
}
