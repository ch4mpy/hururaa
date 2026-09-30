package pf.hururaa;

import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import pf.hururaa.application.domain.Application;
import pf.hururaa.commons.security.HururaaSecurityConfiguration;
import pf.hururaa.direction.domain.User;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.security.HururaaAuthenticationConverter;
import pf.hururaa.uaa.UaaAuthorization;
import pf.hururaa.uaa.UaaProperties;

/**
 * Users and applications of the dev realm (see {@code src/test/resources/jwt/*.json} and the dev
 * Liquibase data), shared by the controller tests.
 */
public final class HururaaFixtures {

  public static final String DSI = "dsi";
  public static final String DPAM = "dpam";
  public static final String DAF = "daf";

  public static final String SIPF_ADMIN = "d053cfc8-5a34-4fcb-9431-bb47617c18c6";
  public static final String DSI_ADMIN = "e6ff96c5-0d55-4a1e-8716-8e0210551d1a";
  public static final String DSI_MANAGER = "c6bca76e-e2cb-45ee-bbfe-2538652279b1";
  public static final String DPAM_ADMIN = "de10aa56-81e4-4e66-a1d7-824d4b6a33d1";
  public static final String DPAM_MANAGER = "b70c1bf6-e94f-439a-8f5d-50df5542e19f";
  public static final String DPAM_AGENT = "1f0644a3-d3e2-45fe-a153-a50491afe35c";

  public static final long ESCALES_ID = 3L;
  public static final long TE_FENUA_ID = 2L;

  private HururaaFixtures() {}

  /** Escales, managed by dpam.manager in dpam. */
  public static Application escales() {
    return Application
        .builder()
        .id(ESCALES_ID)
        .clientPrefix("escales")
        .name("Escales")
        .direction(DPAM)
        .managers(new HashSet<>(Set.of(DPAM_MANAGER)))
        .build();
  }

  /** Te Fenua, managed by dsi.manager in dsi. */
  public static Application teFenua() {
    return Application
        .builder()
        .id(TE_FENUA_ID)
        .clientPrefix("te-fenua")
        .name("Te Fenua")
        .direction(DSI)
        .managers(new HashSet<>(Set.of(DSI_MANAGER)))
        .build();
  }

  public static User user(String id, String username) {
    return new User(id, username, null, null, username + "@gov.pf");
  }

  /**
   * What a {@code @WebMvcTest} slice of this API needs besides its controller: the method-security
   * setup and JWT converter of {@code common-security-starter}, and the real {@code @uaa} access
   * rules with their properties bound from {@code application.yml} (their repositories and Keycloak
   * services being mocked by each test).
   */
  @TestConfiguration
  @EnableConfigurationProperties({UaaProperties.class, KeycloakAdminApiProperties.class})
  @Import({HururaaSecurityConfiguration.class, HururaaAuthenticationConverter.class,
      UaaAuthorization.class})
  public static class WebMvcTestConfiguration {
  }
}
