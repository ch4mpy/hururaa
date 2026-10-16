package pf.hururaa.application.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import pf.hururaa.application.domain.Application;

/**
 * Outside of the {@code dev} context (here {@code test}), Liquibase registers Hurura'a in the DSI
 * ({@code uaa.platform-organization}): it is an application like the others in every environment.
 */
@DataJpaTest
class RegisterHururaaTest {

  @Autowired
  ApplicationRepository applicationRepository;

  @Test
  void hururaaIsRegisteredInThePlatformOrganization() {
    assertThat(applicationRepository.findAllByOrderByNameAsc())
        .extracting(Application::getClientPrefix, Application::getDirection, Application::getName)
        .containsExactly(tuple("hururaa", "dsi", "Hurura'a"));
  }
}
