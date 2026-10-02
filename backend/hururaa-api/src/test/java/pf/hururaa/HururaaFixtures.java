package pf.hururaa;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.security.HururaaSecurityConfiguration;
import pf.hururaa.direction.DelegationResolver;
import pf.hururaa.direction.domain.Direction;
import pf.hururaa.direction.domain.DirectionAdmin;
import pf.hururaa.direction.domain.User;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.security.HururaaAuthenticationConverter;
import pf.hururaa.uaa.UaaProperties;

/**
 * Users and applications of the dev realm (see {@code src/test/resources/jwt/*.json} and the dev
 * Liquibase data), shared by the controller tests.
 */
public final class HururaaFixtures {

  public static final String DSI = "dsi";
  public static final String DPAM = "dpam";
  public static final String DAF = "daf";

  public static final String HURURAA_ADMIN = "d053cfc8-5a34-4fcb-9431-bb47617c18c6";
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

  /**
   * Stubs what the path variable converters read, as in the dev data: {@value #DSI},
   * {@value #DPAM} and {@value #DAF} exist in Keycloak (no other direction does), administered by
   * {@link #DSI_ADMIN} and {@link #DPAM_ADMIN}, with {@link #teFenua()} in dsi and
   * {@link #escales()} in dpam. A test stubbing one of these calls again overrides it.
   */
  public static void stubDevDelegations(DirectionService directionService,
      DirectionAdminRepository directionAdminRepository,
      ApplicationRepository applicationRepository) throws Exception {
    when(directionService.findByAlias(anyString())).thenAnswer(invocation -> {
      final String alias = invocation.getArgument(0);
      return Set.of(DSI, DPAM, DAF).contains(alias) ? Optional.of(new Direction(alias, alias, null))
          : Optional.empty();
    });
    when(directionAdminRepository.findByDirectionOrderByUserId(DSI))
        .thenReturn(List.of(DirectionAdmin.builder().direction(DSI).userId(DSI_ADMIN).build()));
    when(directionAdminRepository.findByDirectionOrderByUserId(DPAM))
        .thenReturn(List.of(DirectionAdmin.builder().direction(DPAM).userId(DPAM_ADMIN).build()));
    when(applicationRepository.findByDirectionOrderByNameAsc(DSI)).thenReturn(List.of(teFenua()));
    when(applicationRepository.findByDirectionOrderByNameAsc(DPAM)).thenReturn(List.of(escales()));
  }

  public static User user(String id, String username) {
    return new User(id, username, null, null, username + "@gov.pf");
  }

  /**
   * What a {@code @WebMvcTest} slice of this API needs besides its controller: the method-security
   * setup of {@code common-security-starter}, this API's JWT converter (the Hurura'a roles held in
   * the DSI as authorities), and the real {@link DelegationResolver} the path variable converters
   * resolve directions and groups with, with their properties bound from {@code application.yml}
   * (its repositories and Keycloak services being mocked by each test).
   */
  @TestConfiguration
  @EnableConfigurationProperties({UaaProperties.class, KeycloakAdminApiProperties.class})
  @Import({HururaaSecurityConfiguration.class, HururaaAuthenticationConverter.class,
      DelegationResolver.class})
  public static class WebMvcTestConfiguration {
  }
}
