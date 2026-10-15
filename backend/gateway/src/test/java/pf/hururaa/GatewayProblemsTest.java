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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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

  /** An HTTP session holding the security context, as {@code oauth2Login} leaves it. */
  private static MockHttpSession loggedInSession() {
    final var user = HururaaOidcUserFixture.hururaaOidcUser(Map.of("dsi", Set.of()));
    final var session = new MockHttpSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(
            new OAuth2AuthenticationToken(user, user.getAuthorities(), "hururaa-bff")));
    return session;
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
  void givenNoSession_whenGetUnknownRoutes_thenUnauthenticatedProblem() throws Exception {
    // refused by default (EX-13), in both filter chains (spec v3, v4)
    expectProblem(
        mockMvc.perform(getGateway("/inconnue")).andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
    expectProblem(
        mockMvc.perform(getGateway("/bff/inconnue")).andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
  }

  /**
   * The security context is in the HTTP session, as after a real login, not set by the test support
   * (which every filter chain would see): the resource server chain, stateless, ignores the session
   * (spec v4, EX-4).
   */
  @Test
  void givenASession_whenGetUnknownRoutes_thenNotFoundUnderTheClientPrefixesOnly()
      throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway("/bff/inconnue").session(loggedInSession()))
            .andExpect(status().isNotFound()),
        ProblemType.NOT_FOUND);
    expectProblem(
        mockMvc
            .perform(getGateway("/inconnue").session(loggedInSession()))
            .andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
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
  /** The other side of the default refusal (EX-13): a member's session still opens the stream. */
  @Test
  void givenAMemberSession_whenSubscribeToEvents_thenTheStreamStarts() throws Exception {
    mockMvc
        .perform(
            getGateway("/bff/events/dsi")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .session(loggedInSession()))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());
  }

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
            .perform(getGateway(FailingController.PATH).session(loggedInSession()))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string(not(containsString(FailingController.SECRET)))),
        ProblemType.SERVER_ERROR);
  }

  /**
   * What {@code TokenRelay=} throws, inside the handler, when the refresh token is no longer valid:
   * {@code RestfulOAuth2Filter} answers it {@code 401}, the shared exception resolver leaves it alone
   * (review of PR #26).
   */
  @Test
  void givenTheClientNeedsAuthorizationAgain_whenRelayed_thenUnauthenticatedProblem()
      throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway(FailingController.CLIENT_AUTHORIZATION_REQUIRED_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION)),
        ProblemType.UNAUTHENTICATED);
  }

  /** Handlers failing like the gateway's own ones can only fail. */
  @RestController
  static class FailingController {
    // under /bff/**, reached with a session: not anonymous, refused by default (EX-13)
    static final String PATH = "/bff/test/failing";

    // under /bff/**, like the TokenRelay= route: the client filter chain (RestfulOAuth2Filter)
    static final String CLIENT_AUTHORIZATION_REQUIRED_PATH = "/bff/test/client-authorization-required";

    static final String SECRET = "jdbc:postgresql://internal-host/secret";

    @GetMapping(PATH)
    @PreAuthorize("permitAll()")
    String fail() {
      throw new IllegalStateException(SECRET);
    }

    @GetMapping(CLIENT_AUTHORIZATION_REQUIRED_PATH)
    @PreAuthorize("permitAll()")
    String requireClientAuthorization() {
      throw new ClientAuthorizationRequiredException("hururaa-bff");
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
  void givenNoToken_whenGetActuatorOnTheMainPort_thenUnauthenticatedProblem() throws Exception {
    // the actuator is on the management port (ActuatorExposureTest): here, an unlisted path
    expectProblem(
        mockMvc.perform(getGateway("/actuator/info")).andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
  }

  // ---------- what anonymous requests may reach (EX-13, CA-13) ----------

  @Test
  void givenNoSession_whenStartLogin_thenOkWithTheAuthorizationUri() throws Exception {
    mockMvc
        .perform(getGateway("/oauth2/authorization/hururaa-bff"))
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.LOCATION));
  }

  @Test
  void givenNoSession_whenLogout_thenAcceptedWithoutEffect() throws Exception {
    mockMvc
        .perform(post(CONTEXT_PATH + "/logout").contextPath(CONTEXT_PATH).with(csrf()))
        .andExpect(status().isAccepted());
  }

  @Test
  void givenNoSession_whenGetError_thenItsProblem() throws Exception {
    // called directly, without an error status: 404 (an error dispatch carries its status)
    expectProblem(
        mockMvc.perform(getGateway("/error")).andExpect(status().isNotFound()),
        ProblemType.NOT_FOUND);
  }
}
