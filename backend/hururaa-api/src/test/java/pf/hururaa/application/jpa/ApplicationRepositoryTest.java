package pf.hururaa.application.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import pf.hururaa.application.domain.Application;

@DataJpaTest
class ApplicationRepositoryTest {

  @Autowired
  ApplicationRepository repository;

  @BeforeEach
  void setUp() {
    repository.save(application("te-fenua", "Te Fenua", "dsi", "alice"));
    repository.save(application("hururaa", "Hurura'a", "dsi", "alice", "bob"));
    repository.save(application("escales", "Escales", "dpam", "carol"));
  }

  @Test
  void findByManagerReturnsTheApplicationsOfTheManagerWhateverTheirDirection() {
    assertThat(repository.findByManager("alice"))
        .extracting(Application::getClientPrefix)
        .containsExactly("hururaa", "te-fenua");
    assertThat(repository.findByManager("carol"))
        .extracting(Application::getClientPrefix)
        .containsExactly("escales");
    assertThat(repository.findByManager("nobody")).isEmpty();
  }

  @Test
  void existsByDirectionAndManagerIsScopedToTheDirection() {
    assertThat(repository.existsByDirectionAndManager("dsi", "bob")).isTrue();
    assertThat(repository.existsByDirectionAndManager("dpam", "bob")).isFalse();
    assertThat(repository.existsByDirectionAndManager("dpam", "carol")).isTrue();
  }

  @Test
  void findByDirectionOrderByNameAscSortsByName() {
    assertThat(repository.findByDirectionOrderByNameAsc("dsi"))
        .extracting(Application::getName)
        .containsExactly("Hurura'a", "Te Fenua");
  }

  @Test
  void existsByClientPrefix() {
    assertThat(repository.existsByClientPrefix("escales")).isTrue();
    assertThat(repository.existsByClientPrefix("anahei")).isFalse();
  }

  private static Application application(String prefix, String name, String direction,
      String... managers) {
    return Application
        .builder()
        .clientPrefix(prefix)
        .name(name)
        .direction(direction)
        .managers(new HashSet<>(Set.of(managers)))
        .build();
  }
}
