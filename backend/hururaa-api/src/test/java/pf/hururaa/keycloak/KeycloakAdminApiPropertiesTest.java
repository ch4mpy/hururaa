package pf.hururaa.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link KeycloakAdminApiProperties} from the real {@code application.yml}: client ids are
 * used as cache keys, so leaving a suffix unset fails on the first roles request rather than at
 * startup.
 */
class KeycloakAdminApiPropertiesTest {

  @Configuration
  @EnableConfigurationProperties(KeycloakAdminApiProperties.class)
  static class TestConfig {
  }

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withUserConfiguration(TestConfig.class);

  @Test
  void applicationYmlNamesTheApplicationClientsAfterTheirPrefix() {
    runner.run(context -> {
      final var properties = context.getBean(KeycloakAdminApiProperties.class);
      assertThat(properties.getRealmName()).isEqualTo("public-facing");
      assertThat(properties.apiClientId("escales")).isEqualTo("escales-api");
      assertThat(properties.bffClientId("escales")).isEqualTo("escales-bff");
    });
  }

  @Test
  void theRolesNamespaceIsHururaasOwnApiClient() {
    runner.run(context -> {
      final var properties = context.getBean(KeycloakAdminApiProperties.class);
      assertThat(properties.apiClientId("hururaa"))
          .isEqualTo(context.getEnvironment().getProperty("roles-namespace"));
    });
  }

  @Test
  void aMissingApiClientSuffixFailsAtStartup() {
    runner.withPropertyValues("keycloak-admin-api.api-client-suffix=").run(context -> {
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("apiClientSuffix");
    });
  }
}
