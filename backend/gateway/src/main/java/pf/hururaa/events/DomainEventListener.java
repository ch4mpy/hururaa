package pf.hururaa.events;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pf.hururaa.commons.events.ResourceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventListener {

  private final SseEmitterRegistry registry;

  @RabbitListener(queues = "#{gatewayEventsQueue.name}")
  public void onDomainEvent(ResourceEvent event) {
    registry.relay(event);
  }
}
