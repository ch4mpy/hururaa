package pf.hururaa.application.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

/**
 * Persists an application through the Liquibase-created schema, with Hibernate validating the
 * entity mappings against that schema (in which the delegation tables are dropped).
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ApplicationPersistenceTest {

  @Autowired
  TestEntityManager em;

  @Test
  void whenPersistingApplication_thenReadBack() {
    final var id = em.persistAndGetId(Application
        .builder()
        .clientPrefix("te-fenua")
        .name("Te Fenua")
        .direction("dsi")
        .build(), Long.class);
    em.flush();
    em.clear();

    final var actual = em.find(Application.class, id);
    assertThat(actual.getClientPrefix()).isEqualTo("te-fenua");
    assertThat(actual.getName()).isEqualTo("Te Fenua");
    assertThat(actual.getDirection()).isEqualTo("dsi");
    assertThat(actual.getVersion()).isZero();
  }
}
