package pf.hururaa.commons.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class ResourceEventPublisherTest {

  private static final String EXCHANGE = "resource-events.hururaa-api";

  private static final ResourceEvent EVENT = new ResourceEvent(
      "dpam",
      "application",
      "42",
      null,
      null,
      List.of("hururaa.templates.read"),
      ResourceEvent.EventType.UPDATE,
      Instant.parse("2026-09-15T10:00:00Z"));

  private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final ResourceEventPublisher publisher =
      new ResourceEventPublisher(rabbitTemplate, EXCHANGE, meterRegistry);

  @AfterEach
  void clearTransactionSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void givenNoTransaction_whenPublish_thenSentRightAwayWithTenantTypeAndEventTypeRoutingKey() {
    publisher.publish(EVENT);

    verify(rabbitTemplate).convertAndSend(EXCHANGE, "dpam.application.UPDATE", EVENT);
  }

  @Test
  void givenTransaction_whenPublish_thenSentAfterCommitOnly() {
    TransactionSynchronizationManager.initSynchronization();

    publisher.publish(EVENT);
    verifyNoInteractions(rabbitTemplate);

    TransactionSynchronizationUtils.triggerAfterCommit();
    verify(rabbitTemplate).convertAndSend(EXCHANGE, "dpam.application.UPDATE", EVENT);
  }

  @Test
  void givenTransaction_whenRolledBack_thenNeverSent() {
    TransactionSynchronizationManager.initSynchronization();

    publisher.publish(EVENT);
    TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

    verifyNoInteractions(rabbitTemplate);
  }

  @Test
  void givenBrokerIsDown_whenPublish_thenFailureIsSwallowedAndCounted() {
    doThrow(new AmqpConnectException(new RuntimeException("down")))
        .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

    publisher.publish(EVENT);

    assertThat(meterRegistry.get(ResourceEventPublisher.FAILURES_METRIC).tag("exchange", EXCHANGE)
        .counter().count()).isEqualTo(1.0);
  }

  @Test
  void givenBrokerAcceptsTheEvent_whenPublish_thenNothingIsCounted() {
    publisher.publish(EVENT);

    assertThat(meterRegistry.get(ResourceEventPublisher.FAILURES_METRIC).counter().count()).isZero();
  }
}
