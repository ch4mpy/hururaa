package pf.hururaa.commons.events;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes {@link ResourceEvent}s to the service's topic exchange (see
 * {@link ResourceEventsProperties#getExchangeName()}), with a
 * {@code {tenant}.{resourceType}.{eventType}} routing key.
 *
 * <p>
 * When called inside a transaction, the event is sent only once that transaction commits: a change
 * that ends up rolled back (a constraint violation, an optimistic-locking failure at flush time)
 * must not be announced. A broker failure is logged, not propagated: by then the change is
 * persisted and the caller's request has succeeded.
 * </p>
 *
 * <p>
 * Delivery is best effort, by decision (docs/decisions/0003): no outbox, no publisher confirms, a
 * crash between the commit and the publication loses the event. Nothing may depend on an event
 * being received; consumers (the frontend) refetch the resource over REST. What is measured is
 * the {@value #FAILURES_METRIC} counter, incremented for every event the broker did not take.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Slf4j
public class ResourceEventPublisher {

  /** Counter of the events the broker did not accept (tag {@code exchange}). */
  public static final String FAILURES_METRIC = "hururaa.events.publish.failures";

  private final RabbitTemplate rabbitTemplate;

  private final String exchangeName;

  private final Counter failures;

  public ResourceEventPublisher(RabbitTemplate rabbitTemplate, String exchangeName, MeterRegistry meterRegistry) {
    this.rabbitTemplate = rabbitTemplate;
    this.exchangeName = exchangeName;
    this.failures = Counter.builder(FAILURES_METRIC)
        .description("Resource events the broker did not accept (lost: delivery is best effort)")
        .tag("exchange", exchangeName)
        .register(meterRegistry);
  }

  /**
   * @param event the event to publish, right away or after the current transaction commits
   */
  public void publish(ResourceEvent event) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          send(event);
        }
      });
    } else {
      send(event);
    }
  }

  static String routingKeyOf(ResourceEvent event) {
    return "%s.%s.%s".formatted(event.tenant(), event.resourceType(), event.eventType());
  }

  private void send(ResourceEvent event) {
    final var routingKey = routingKeyOf(event);
    try {
      rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
      log.debug("Published {} to {} with routing key {}", event, exchangeName, routingKey);
    } catch (AmqpException e) {
      failures.increment();
      log.warn("Failed to publish {} to {} with routing key {}: the change is persisted but not announced",
          event, exchangeName, routingKey, e);
    }
  }
}
