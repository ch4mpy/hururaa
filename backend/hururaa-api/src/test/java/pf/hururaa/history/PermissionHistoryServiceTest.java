package pf.hururaa.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static pf.hururaa.HururaaFixtures.HURURAA_ADMIN;
import static pf.hururaa.HururaaFixtures.user;
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
import pf.hururaa.history.domain.PermissionChange;
import pf.hururaa.history.domain.PermissionChangeCategory;
import pf.hururaa.history.domain.PermissionChangeType;
import pf.hururaa.history.domain.PermissionHistoryFilter;
import pf.hururaa.journal.PermissionJournal;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.uaa.UaaProperties;

/**
 * Replays real Envers revisions and journal entries: each change commits its own transaction
 * (Envers writes the audit trail on commit), with the author in the security context. Every test
 * works in directions of its own, the rows outliving it.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PermissionHistoryService.class, PermissionJournal.class})
class PermissionHistoryServiceTest {
  static final String DSI_ALIAS = "dsi";

  @Autowired
  PermissionHistoryService service;

  @Autowired
  PermissionJournal journal;

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
  void givenDelegationsGrantedThenRevoked_whenFind_thenJournaledNewestFirst() throws Exception {
    when(uaaProperties.getPlatformOrganization()).thenReturn(DSI_ALIAS);
    when(directionService.findMember(DSI_ALIAS, HURURAA_ADMIN))
        .thenReturn(Optional.of(user(HURURAA_ADMIN, "hururaa.admin")));
    when(directionService.findMember("hist-admins", "admin-1"))
        .thenReturn(Optional.of(user("admin-1", "admin.one")));
    when(directionService.findMember("hist-admins", "manager-1"))
        .thenReturn(Optional.of(user("manager-1", "manager.one")));
    final var application = Application.builder().id(4343L).clientPrefix("hist-delegated")
        .name("Delegated").direction("hist-admins").build();
    commitAs(HURURAA_ADMIN, () -> {
      journal.directionAdminGranted("hist-admins", "admin-1");
      return null;
    });
    Thread.sleep(5);
    commitAs("admin-1", () -> {
      journal.applicationManagerGranted(application, "manager-1");
      return null;
    });
    Thread.sleep(5);
    commitAs(HURURAA_ADMIN, () -> {
      journal.directionAdminRevoked("hist-admins", "admin-1");
      return null;
    });

    assertThat(history("hist-admins").getContent())
        .extracting(PermissionChange::type, change -> change.subject().username(),
            change -> change.author().username(), PermissionChange::applicationName)
        .containsExactly(
            tuple(PermissionChangeType.DIRECTION_ADMIN_REVOKED, "admin.one", "hururaa.admin",
                null),
            tuple(PermissionChangeType.APPLICATION_MANAGER_GRANTED, "manager.one", "admin.one",
                "Delegated"),
            tuple(PermissionChangeType.DIRECTION_ADMIN_GRANTED, "admin.one", "hururaa.admin",
                null));
    // a manager's designation is a change of the application
    assertThat(find(new PermissionHistoryFilter("hist-admins", 4343L, null,
        Set.of(PermissionChangeCategory.DELEGATION)), PageRequest.of(0, 20)).getContent())
        .extracting(PermissionChange::type)
        .containsExactly(PermissionChangeType.APPLICATION_MANAGER_GRANTED);
  }

  @Test
  void givenApplicationRegisteredRenamedThenUnregistered_whenFind_thenItsWholeLife() {
    final var application = commitAs("dir-admin", () -> applicationRepository.save(Application
        .builder()
        .clientPrefix("hist-app")
        .name("Hist App")
        .direction("hist-from")
        .build()));
    commitAs("dir-admin", () -> {
      final var current = applicationRepository.findById(application.getId()).orElseThrow();
      current.setName("Hist App 2");
      return applicationRepository.save(current);
    });
    commitAs("hururaa-admin", () -> {
      applicationRepository.deleteById(application.getId());
      return null;
    });

    // a deleted row's audit holds only its id: its name is replayed
    assertThat(history("hist-from").getContent())
        .extracting(PermissionChange::type, change -> change.author().id(),
            PermissionChange::applicationName, PermissionChange::formerApplicationName)
        .containsExactly(
            tuple(PermissionChangeType.APPLICATION_UNREGISTERED, "hururaa-admin", "Hist App 2",
                null),
            tuple(PermissionChangeType.APPLICATION_RENAMED, "dir-admin", "Hist App 2",
                "Hist App"),
            tuple(PermissionChangeType.APPLICATION_REGISTERED, "dir-admin", "Hist App", null));
  }

  @Test
  void givenAuditedAndJournaledChanges_whenFind_thenMergedNewestFirstAndFiltered()
      throws Exception {
    // audited: the application's registration
    final var registered = commitAs("dir-admin", () -> applicationRepository.save(Application
        .builder().clientPrefix("hist-merged-app").name("Merged").direction("hist-merged")
        .build()));
    Thread.sleep(5);
    // journaled, under another application: the group's own changes
    final var application = Application.builder().id(4242L).clientPrefix("hist").name("Hist")
        .direction("hist-merged").build();
    commitAs("dir-admin", () -> {
      journal.groupCreated(application, "hist.agents");
      return null;
    });
    Thread.sleep(5);
    commitAs("dir-admin", () -> {
      journal.groupMemberAdded("hist-merged", application, "hist.agents", "agent-1");
      return null;
    });

    assertThat(history("hist-merged").getContent())
        .extracting(PermissionChange::type)
        .containsExactly(PermissionChangeType.GROUP_MEMBER_ADDED,
            PermissionChangeType.GROUP_CREATED, PermissionChangeType.APPLICATION_REGISTERED);
    assertThat(registered.getId()).isNotNull();
    assertThat(find(new PermissionHistoryFilter("hist-merged", null, null,
        Set.of(PermissionChangeCategory.GROUP_MEMBER)), PageRequest.of(0, 20)).getContent())
        .extracting(change -> change.subject().id())
        .containsExactly("agent-1");
    assertThat(find(new PermissionHistoryFilter("hist-merged", null, "hist.agents", Set.of()),
        PageRequest.of(0, 20)).getContent())
        .extracting(PermissionChange::type)
        .containsExactly(PermissionChangeType.GROUP_MEMBER_ADDED,
            PermissionChangeType.GROUP_CREATED);
    // a group's changes are also those of its application
    assertThat(find(new PermissionHistoryFilter("hist-merged", 4242L, null, Set.of()),
        PageRequest.of(0, 20)).getContent())
        .extracting(PermissionChange::type)
        .containsExactly(PermissionChangeType.GROUP_MEMBER_ADDED,
            PermissionChangeType.GROUP_CREATED);

    final var secondPage = find(PermissionHistoryFilter.ofDirection("hist-merged", Set.of()),
        PageRequest.of(1, 1));
    assertThat(secondPage.getTotalElements()).isEqualTo(3);
    assertThat(secondPage.getContent())
        .extracting(PermissionChange::type)
        .containsExactly(PermissionChangeType.GROUP_CREATED);
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

  private Page<PermissionChange> history(String direction) {
    return find(PermissionHistoryFilter.ofDirection(direction, Set.of()), PageRequest.of(0, 20));
  }

  private Page<PermissionChange> find(PermissionHistoryFilter filter, PageRequest pageable) {
    final var template = new TransactionTemplate(transactionManager);
    template.setReadOnly(true);
    return template.execute(_ -> {
      try {
        return service.find(filter, pageable);
      } catch (HururaaProblemException e) {
        throw new IllegalStateException(e);
      }
    });
  }
}
