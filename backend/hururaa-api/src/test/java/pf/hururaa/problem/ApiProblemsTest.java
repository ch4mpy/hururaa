package pf.hururaa.problem;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pf.hururaa.HururaaFixtures.DAF;
import static pf.hururaa.HururaaFixtures.DPAM;
import static pf.hururaa.HururaaFixtures.stubDevDelegations;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.MockMvcSupport;
import pf.hururaa.HururaaFixtures;
import pf.hururaa.application.ApplicationService;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.application.web.ApplicationController;
import pf.hururaa.application.web.ApplicationCreationRequest;
import pf.hururaa.application.web.ApplicationMapperImpl;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.history.PermissionHistoryMapperImpl;
import pf.hururaa.history.PermissionHistoryService;
import pf.hururaa.keycloak.ClientProvisioningService;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.uaa.DelegationService;

/**
 * The errors of this API that no business rule raises, written as problems of the generic types
 * (docs/intent/0003): the security configuration's {@code 401} and {@code 403}, the framework's
 * rejections and unknown routes ({@link ProblemHandlerExceptionResolver}), with a business problem
 * still typed by {@link HururaaExceptionHandler}. Exercised on the real access rules of
 * {@link ApplicationController}.
 */
@WebMvcTest(controllers = ApplicationController.class)
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({HururaaFixtures.WebMvcTestConfiguration.class, ApplicationMapperImpl.class,
    ApplicationService.class, PermissionHistoryMapperImpl.class})
@TestPropertySource(properties = "server.ssl.enabled=false")
class ApiProblemsTest {

  @Autowired
  MockMvcSupport api;

  @MockitoBean
  ApplicationRepository applicationRepository;

  @MockitoBean
  DelegationService delegationService;

  @MockitoBean
  DirectionService directionService;

  @MockitoBean
  GroupService groupService;

  @MockitoBean
  ClientProvisioningService clientProvisioningService;

  @MockitoBean
  ResourceEventPublisher resourceEvents;

  @MockitoBean
  PermissionHistoryService permissionHistoryService;

  @BeforeEach
  void setUp() throws Exception {
    stubDevDelegations(directionService, applicationRepository);
    when(applicationRepository.findById(99L)).thenReturn(Optional.empty());
    when(directionService.exists(anyString())).thenReturn(true);
  }

  private static void expectProblem(ResultActions result, ProblemType type) throws Exception {
    result
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(type.uri().toString()));
  }

  @Test
  @WithAnonymousUser
  void givenAnonymous_whenGetApplications_thenUnauthenticatedProblemWithABearerChallenge()
      throws Exception {
    final var result = api.get(ApplicationController.BASE_PATH)
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    expectProblem(result, ProblemType.UNAUTHENTICATED);
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenAdminOfAnotherDirection_whenCreateApplication_thenAccessDeniedProblem()
      throws Exception {
    final var result = api
        .post(new ApplicationCreationRequest("anahei", "Anahei"),
            ApplicationController.DIRECTION_APPLICATIONS_PATH, DAF)
        .andExpect(status().isForbidden());
    expectProblem(result, ProblemType.ACCESS_DENIED);
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenAMemberWithoutHururaaRole_whenCreateApplication_thenAccessDeniedProblem()
      throws Exception {
    final var result = api
        .post(new ApplicationCreationRequest("anahei", "Anahei"),
            ApplicationController.DIRECTION_APPLICATIONS_PATH, DPAM)
        .andExpect(status().isForbidden());
    expectProblem(result, ProblemType.ACCESS_DENIED);
  }

  @Test
  @WithJwt("jwt/dpam-admin.json")
  void givenAnUnreadableBody_whenCreateApplication_thenInvalidRequestProblem() throws Exception {
    final var result = api
        .perform(post(ApplicationController.DIRECTION_APPLICATIONS_PATH, DPAM)
            .contentType(MediaType.APPLICATION_JSON).content("{\"clientPrefix\":"))
        .andExpect(status().isBadRequest());
    expectProblem(result, ProblemType.INVALID_REQUEST);
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenAnUnsupportedMethod_whenDeleteApplications_thenInvalidRequestProblemWithItsStatus()
      throws Exception {
    final var result = api.delete(ApplicationController.BASE_PATH)
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists(HttpHeaders.ALLOW))
        .andExpect(jsonPath("$.status").value(405));
    expectProblem(result, ProblemType.INVALID_REQUEST);
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenAnUnknownRoute_whenGet_thenNotFoundProblem() throws Exception {
    final var result = api.get("/unknown").andExpect(status().isNotFound());
    expectProblem(result, ProblemType.NOT_FOUND);
  }

  @Test
  @WithJwt("jwt/dpam-agent.json")
  void givenUnknownApplication_whenGetApplication_thenTheBusinessProblemIsKept() throws Exception {
    final var result = api.get(ApplicationController.APPLICATION_PATH, 99)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.parameters.applicationId").value(99));
    expectProblem(result, ProblemType.APPLICATION_NOT_FOUND);
  }
}
