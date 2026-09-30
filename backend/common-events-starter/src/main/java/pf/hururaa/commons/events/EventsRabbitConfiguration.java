package pf.hururaa.commons.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import tools.jackson.databind.json.JsonMapper;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
@EnableConfigurationProperties(ResourceEventsProperties.class)
public class EventsRabbitConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = ResourceEventsProperties.PREFIX, name = "exchange-name")
  TopicExchange eventsExchange(ResourceEventsProperties properties) {
    return new TopicExchange(properties.getExchangeName());
  }

  @Bean
  MessageConverter eventsMessageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(jsonMapper);
  }

  /** Only the services owning resources (those naming their exchange) publish events. */
  @Bean
  @ConditionalOnProperty(prefix = ResourceEventsProperties.PREFIX, name = "exchange-name")
  ResourceEventPublisher resourceEventPublisher(
      RabbitTemplate rabbitTemplate, ResourceEventsProperties properties,
      ObjectProvider<MeterRegistry> meterRegistry) {
    // the actuator's registry when there is one, Micrometer's global one otherwise
    return new ResourceEventPublisher(rabbitTemplate, properties.getExchangeName(),
        meterRegistry.getIfAvailable(() -> Metrics.globalRegistry));
  }
}
