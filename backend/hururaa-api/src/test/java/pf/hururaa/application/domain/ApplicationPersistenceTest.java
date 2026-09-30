package pf.hururaa.application.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import pf.hururaa.direction.domain.DirectionAdmin;

/**
 * Persists an application with its managers and a direction administrator through the
 * Liquibase-created schema, with Hibernate validating the entity mappings against that schema.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ApplicationPersistenceTest {

  @Autowired
  TestEntityManager em;

  @Test
  void whenPersistingApplicationWithManagers_thenReadBack() {
    final var id = em.persistAndGetId(Application
        .builder()
        .clientPrefix("te-fenua")
        .name("Te Fenua")
        .direction("dsi")
        .managers(new HashSet<>(Set.of("manager-1", "manager-2")))
        .build(), Long.class);
    em.flush();
    em.clear();

    final var actual = em.find(Application.class, id);
    assertThat(actual.getClientPrefix()).isEqualTo("te-fenua");
    assertThat(actual.getName()).isEqualTo("Te Fenua");
    assertThat(actual.getDirection()).isEqualTo("dsi");
    assertThat(actual.getManagers()).containsExactlyInAnyOrder("manager-1", "manager-2");
    assertThat(actual.getVersion()).isZero();
  }

  @Test
  void whenBuildingWithoutManagers_thenManagersIsAMutableEmptySet() {
    final var application =
        Application.builder().clientPrefix("anahei").name("Anahei").direction("daf").build();

    assertThat(application.getManagers()).isEmpty();
    application.getManagers().add("someone");
    assertThat(application.getManagers()).containsExactly("someone");
  }

  @Test
  void whenPersistingDirectionAdmin_thenReadBack() {
    final var id = em.persistAndGetId(
        DirectionAdmin.builder().direction("dpam").userId("admin-1").build(), Long.class);
    em.flush();
    em.clear();

    final var actual = em.find(DirectionAdmin.class, id);
    assertThat(actual.getDirection()).isEqualTo("dpam");
    assertThat(actual.getUserId()).isEqualTo("admin-1");
  }
}
