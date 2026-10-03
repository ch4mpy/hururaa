package pf.hururaa.application.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import pf.hururaa.application.domain.Application;

/**
 * The Liquibase {@code dev} context loads the applications of the dev realm export, whose
 * {@code hururaa.<prefix>.product-owners} groups must stay in sync with {@code keycloak/import}.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.liquibase.contexts=dev")
class DevDataTest {

  @Autowired
  ApplicationRepository applicationRepository;

  @Test
  void devDataHasOneApplicationPerDevClientPair() {
    assertThat(applicationRepository.findAllByOrderByNameAsc())
        .extracting(Application::getClientPrefix, Application::getDirection)
        .containsExactly(
            tuple("anahei", "daf"),
            tuple("escales", "dpam"),
            tuple("hururaa", "dsi"),
            tuple("te-fenua", "dsi"));
  }
}
