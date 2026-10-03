package pf.hururaa.application.jpa;

import static org.assertj.core.api.Assertions.assertThat;
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
    repository.save(application("te-fenua", "Te Fenua", "dsi"));
    repository.save(application("hururaa", "Hurura'a", "dsi"));
    repository.save(application("escales", "Escales", "dpam"));
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

  private static Application application(String prefix, String name, String direction) {
    return Application.builder().clientPrefix(prefix).name(name).direction(direction).build();
  }
}
