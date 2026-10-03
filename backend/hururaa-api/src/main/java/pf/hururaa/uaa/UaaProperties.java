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
   * Alias of the DSI, which runs Hurura'a: a direction like the others, whose administrators
   * ({@link HururaaPermission#DIRECTION_ADMIN} held there) are the Hurura'a administrators, acting
   * in every direction.
   */
  @NotBlank
  private final String platformOrganization;
}
