package pf.hururaa.commons.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@ConfigurationProperties(prefix = ResourceEventsProperties.PREFIX)
@Data
public class ResourceEventsProperties {
  static final String PREFIX = "resource-events";

  /**
   * Name of the topic exchange this service publishes its domain events to
   * (consumers, such as
   * the gateway, subscribe to it by name).
   */
  private final String exchangeName;
}
