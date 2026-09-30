package pf.hururaa.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Validated at startup: a missing value would otherwise only surface on the first Keycloak call (for
 * instance as a {@code Null key returned for cache operation} when a client id is used as a cache
 * key).
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@ConfigurationProperties(prefix = "keycloak-admin-api")
@Validated
@Data
public class KeycloakAdminApiProperties {

  @NotBlank
  private final String realmName;

  /**
   * Appended to an application's client prefix to get the ID of the Keycloak client carrying its
   * roles and the service account its REST API calls the admin API with ({@code escales} →
   * {@code escales-api}).
   */
  @NotBlank
  private final String apiClientSuffix;

  /**
   * Appended to an application's client prefix to get the ID of the Keycloak client its users log
   * in with ({@code escales} → {@code escales-bff}).
   */
  @NotBlank
  private final String bffClientSuffix;

  /**
   * The {@code realm-management} roles granted to the service account of the {@code <prefix>-api}
   * clients Hurura'a creates (see {@link ClientProvisioningService}): what an application's REST API
   * needs of the admin API. Hurura'a's own service account must hold each of them, Keycloak only
   * lets an administrator grant the admin roles it has.
   */
  @NotNull
  private final List<String> apiServiceAccountRoles;

  /**
   * @param clientPrefix an application's client prefix
   * @return the ID of the Keycloak client carrying the application's roles
   */
  public String apiClientId(String clientPrefix) {
    return clientPrefix + apiClientSuffix;
  }

  /**
   * @param clientPrefix an application's client prefix
   * @return the ID of the Keycloak client the application's users log in with
   */
  public String bffClientId(String clientPrefix) {
    return clientPrefix + bffClientSuffix;
  }
}
