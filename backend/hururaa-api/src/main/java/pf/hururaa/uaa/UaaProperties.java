package pf.hururaa.uaa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@ConfigurationProperties(prefix = "uaa")
@Validated
@Data
public class UaaProperties {

  /**
   * Alias of the organization running the platform (the SIPF's direction): the only one in which
   * {@link HururaaPermission Hurura'a's own roles} take effect.
   */
  @NotBlank
  private final String platformOrganization;
}
