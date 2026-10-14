package pf.hururaa;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.security.HururaaOidcUserFixture;

/**
 * Every error the SPA can get from the gateway is a problem of a {@link ProblemType}
 * (docs/intent/0003): no plain text, no empty body, no login URL. The cases of a session cookie the
 * gateway no longer knows are in {@code InvalidSessionTest}, those of hururaa-api unreachable in
 * {@link ApiUnavailableTest}.
 *
 * <p>
 * Same infrastructure as {@code InvalidSessionTest}: Keycloak's discovery documents and hururaa-api
 * are served by WireMock (hururaa-api answers a request without a token with its
 * {@code unauthenticated} problem, and {@code /applications/99} with an
 * {@code application-not-found} one), the RabbitMQ listeners don't start.
 * </p>
 */
@SpringBootTest(
    properties = {
      "issuer=http://localhost:8089/auth/realms/public-facing",
      "rest-api-v1=http://localhost:8090",
      "server.ssl.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false"
    })
@ActiveProfiles("no-observability")
@AutoConfigureMockMvc
@EnableWireMock({
  @ConfigureWireMock(name = "keycloak", port = 8089, filesUnderClasspath = "wiremock/keycloak"),
  @ConfigureWireMock(name = "hururaa-api", port = 8090, filesUnderClasspath = "wiremock/hururaa-api")
})
@Import(GatewayProblemsTest.FailingController.class)
class GatewayProblemsTest {
  private static final String CONTEXT_PATH = "/gateway";

  @Autowired MockMvc mockMvc;

  private static MockHttpServletRequestBuilder getGateway(String path) {
    return get(CONTEXT_PATH + path).contextPath(CONTEXT_PATH);
  }

  /** A user logged in through the BFF, member of the DSI only (without roles there). */
  private static RequestPostProcessor dsiMember() {
    final var user = HururaaOidcUserFixture.hururaaOidcUser(Map.of("dsi", Set.of()));
    return authentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "hururaa-bff"));
  }

  private static ResultActions expectProblem(ResultActions result, ProblemType type)
      throws Exception {
    return result
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(type.uri().toString()));
  }

  @Test
  void givenNoSession_whenSubscribeToEvents_thenUnauthenticatedProblemWithoutLoginUrl()
      throws Exception {
    final var result =
        mockMvc
            .perform(getGateway("/bff/events/dsi").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
            .andExpect(content().string(not(containsString("login"))));
    expectProblem(result, ProblemType.UNAUTHENTICATED);
  }

  @Test
  void givenNoSession_whenGetUnknownRoute_thenNotFoundProblem() throws Exception {
    expectProblem(
        mockMvc.perform(getGateway("/inconnue")).andExpect(status().isNotFound()),
        ProblemType.NOT_FOUND);
  }

  @Test
  void givenALoggedInUser_whenGetUnknownRoutes_thenNotFoundProblem() throws Exception {
    expectProblem(
        mockMvc.perform(getGateway("/inconnue").with(dsiMember())).andExpect(status().isNotFound()),
        ProblemType.NOT_FOUND);
    expectProblem(
        mockMvc
            .perform(getGateway("/bff/inconnue").with(dsiMember()))
            .andExpect(status().isNotFound()),
        ProblemType.NOT_FOUND);
  }

  @Test
  void givenAMemberOfAnotherDirection_whenSubscribeToEvents_thenAccessDeniedProblem()
      throws Exception {
    expectProblem(
        mockMvc
            .perform(
                getGateway("/bff/events/daf")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .session(new MockHttpSession())
                    .with(dsiMember()))
            .andExpect(status().isForbidden()),
        ProblemType.ACCESS_DENIED);
  }

  @Test
  void givenAnInvalidCsrfToken_whenLogout_thenAccessDeniedProblem() throws Exception {
    expectProblem(
        mockMvc
            .perform(
                post(CONTEXT_PATH + "/logout").contextPath(CONTEXT_PATH).with(csrf().useInvalidToken()))
            .andExpect(status().isForbidden()),
        ProblemType.ACCESS_DENIED);
  }

  /**
   * Spring Security hands a missing CSRF token to the invalid-session strategy, as a session that
   * expired (spec v2, EX-5): {@code 401} {@code unauthenticated}, not {@code 403}.
   */
  @Test
  void givenNoCsrfToken_whenLogout_thenUnauthenticatedProblem() throws Exception {
    expectProblem(
        mockMvc
            .perform(post(CONTEXT_PATH + "/logout").contextPath(CONTEXT_PATH))
            .andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
  }

  /** An unexpected failure in a handler: its message stays in the logs. */
  @Test
  void givenAFailingHandler_whenCallIt_thenServerErrorProblemWithoutDetails() throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway(FailingController.PATH))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string(not(containsString(FailingController.SECRET)))),
        ProblemType.SERVER_ERROR);
  }

  /** A handler failing unexpectedly, the gateway's own ones failing only on server faults. */
  @RestController
  static class FailingController {
    static final String PATH = "/test/failing";

    static final String SECRET = "jdbc:postgresql://internal-host/secret";

    @GetMapping(PATH)
    @PreAuthorize("permitAll()")
    String fail() {
      throw new IllegalStateException(SECRET);
    }
  }

  @Test
  void givenNoSession_whenCallTheApi_thenItsUnauthenticatedProblemIsRelayed() throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway("/bff/api/v1/me/delegations"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer")),
        ProblemType.UNAUTHENTICATED);
  }

  @Test
  void givenAnApiProblem_whenRelayed_thenUnchanged() throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway("/bff/api/v1/applications/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.parameters.applicationId").value(99))
            .andExpect(jsonPath("$.detail").value("No application with id 99")),
        ProblemType.APPLICATION_NOT_FOUND);
  }

  /** The resource server filter chain still requires a token for the actuator. */
  @Test
  void givenNoToken_whenGetActuator_thenUnauthorized() throws Exception {
    mockMvc.perform(getGateway("/actuator/info")).andExpect(status().isUnauthorized());
  }
}
