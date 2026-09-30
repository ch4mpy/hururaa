package pf.hururaa.application.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import pf.hururaa.HururaaFixtures;
import pf.hururaa.application.domain.Application;
import pf.hururaa.direction.domain.DirectionAdmin;
import pf.hururaa.direction.jpa.DirectionAdminRepository;

/**
 * The Liquibase {@code dev} context loads the applications and delegations matching the users of
 * the dev realm export: their ids must stay in sync with {@code keycloak/import}.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.liquibase.contexts=dev")
class DevDataTest {

  @Autowired
  ApplicationRepository applicationRepository;

  @Autowired
  DirectionAdminRepository directionAdminRepository;

  @Test
  void devDataHasOneApplicationPerDevClientPairAndOneAdministratorPerDirection() {
    assertThat(applicationRepository.findAllByOrderByNameAsc())
        .extracting(Application::getClientPrefix, Application::getDirection)
        .containsExactly(
            tuple("anahei", "daf"),
            tuple("escales", "dpam"),
            tuple("hururaa", "dsi"),
            tuple("te-fenua", "dsi"));
    assertThat(applicationRepository.findByManager(HururaaFixtures.DSI_MANAGER))
        .extracting(Application::getClientPrefix)
        .containsExactly("hururaa", "te-fenua");
    assertThat(applicationRepository.findByManager(HururaaFixtures.DPAM_MANAGER))
        .extracting(Application::getClientPrefix)
        .containsExactly("escales");
    assertThat(directionAdminRepository.findAll())
        .extracting(DirectionAdmin::getDirection, DirectionAdmin::getUserId)
        .contains(
            tuple(HururaaFixtures.DSI, HururaaFixtures.DSI_ADMIN),
            tuple(HururaaFixtures.DPAM, HururaaFixtures.DPAM_ADMIN))
        .hasSize(3);
  }
}
