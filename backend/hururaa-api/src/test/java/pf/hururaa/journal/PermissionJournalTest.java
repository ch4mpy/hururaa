package pf.hururaa.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.DPAM_ADMIN;
import static pf.hururaa.HururaaFixtures.DPAM_AGENT;
import static pf.hururaa.HururaaFixtures.escales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import pf.hururaa.journal.domain.PermissionEvent;
import pf.hururaa.journal.domain.PermissionEventType;
import pf.hururaa.journal.jpa.PermissionEventRepository;

/**
 * Journals through the Liquibase-created schema, with Hibernate validating the entity mapping
 * against it.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(PermissionJournal.class)
class PermissionJournalTest {

  @Autowired
  PermissionJournal journal;

  @Autowired
  PermissionEventRepository repository;

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(DPAM_ADMIN, null));
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void whenJournaling_thenAuthoredByTheAuthenticatedUserAndListedNewestFirst() {
    journal.groupCreated(DPAM, "escales-agents");
    journal.groupRoleGranted(DPAM, "escales-agents", escales(), "escales.stopovers.read");
    journal.groupMemberAdded(DPAM, "escales-agents", DPAM_AGENT);

    final var events = repository.findByDirectionOrderByIdDesc(DPAM, PageRequest.of(0, 10));

    assertThat(events.getContent())
        .extracting(PermissionEvent::getType, PermissionEvent::getAuthorId,
            PermissionEvent::getGroupName, PermissionEvent::getApplicationName,
            PermissionEvent::getRole, PermissionEvent::getUserId)
        .containsExactly(
            tuple(PermissionEventType.GROUP_MEMBER_ADDED, DPAM_ADMIN, "escales-agents", null,
                null, DPAM_AGENT),
            tuple(PermissionEventType.GROUP_ROLE_GRANTED, DPAM_ADMIN, "escales-agents",
                "Escales", "escales.stopovers.read", null),
            tuple(PermissionEventType.GROUP_CREATED, DPAM_ADMIN, "escales-agents", null, null,
                null));
    assertThat(events.getContent()).allSatisfy(event -> {
      assertThat(event.getOccurredAt()).isNotNull();
      assertThat(event.getDirection()).isEqualTo(DPAM);
    });
  }

  @Test
  void whenJournalingAnotherDirection_thenNotListedForThisOne() {
    journal.directionCreated("dsp");

    assertThat(repository.findByDirectionOrderByIdDesc(DPAM, PageRequest.of(0, 10))).isEmpty();
    assertThat(repository.findByDirectionOrderByIdDesc("dsp", PageRequest.of(0, 10)))
        .extracting(PermissionEvent::getType)
        .containsExactly(PermissionEventType.DIRECTION_CREATED);
  }
}
