package pf.hururaa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

/**
 * How the whole security chain treats a session cookie the gateway no longer knows (browser
 * reopened, session expired, gateway restarted), never with a redirection:
 *
 * <ul>
 * <li>on the permit-all routes, spring-addons' {@code invalid-session.permit-all-pass-through}
 * opens a new session and the request proceeds as without a cookie: the session probe ({@code /me})
 * answers anonymous (docs/intent/0002), a BFF route answers what {@code hururaa-api} answers to a
 * request without a token, a {@code 401};</li>
 * <li>{@code /logout} proceeds, its filter running before the session management.</li>
 * </ul>
 *
 * <p>
 * No route the SPA calls reaches spring-addons' invalid-session strategy any more; its
 * {@code status: UNAUTHORIZED} stays as a safeguard should the pass-through be turned off
 * (docs/intent/0001, 0002).
 * </p>
 *
 * <p>
 * Keycloak's discovery documents are served by WireMock, as for the {@code openapi} Maven profile,
 * and so is {@code hururaa-api}, which answers {@code 401} to what the gateway relays without a
 * token; the RabbitMQ listeners (resource events) don't start, so no broker is needed.
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
class InvalidSessionTest {
  private static final String CONTEXT_PATH = "/gateway";

  /** Written by spring-addons' invalid-session strategy: the 401 comes from it, not elsewhere. */
  private static final String INVALID_SESSION_BODY = "Invalid session. Please authenticate.";

  private static final String UNKNOWN_SESSION_ID = "0123456789ABCDEF0123456789ABCDEF";

  @Autowired MockMvc mockMvc;

  private static RequestPostProcessor unknownSession() {
    return request -> {
      request.setRequestedSessionId(UNKNOWN_SESSION_ID);
      request.setRequestedSessionIdValid(false);
      request.setRequestedSessionIdFromCookie(true);
      return request;
    };
  }

  private static MockHttpServletRequestBuilder getWithUnknownSession(String path) {
    return get(CONTEXT_PATH + path).contextPath(CONTEXT_PATH).with(unknownSession());
  }

  @Test
  void givenNoSessionCookie_whenGetMe_thenOkAnonymous() throws Exception {
    mockMvc.perform(get(CONTEXT_PATH + "/me").contextPath(CONTEXT_PATH)).andExpect(status().isOk());
  }

  @Test
  void givenUnknownSessionCookie_whenGetMe_thenOkAnonymousWithANewSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(getWithUnknownSession("/me"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.directions").isEmpty())
            .andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain("\"sub\":\"");
    assertThat(result.getRequest().getSession(false)).isNotNull();
  }

  @Test
  void givenNoSessionCookie_whenGetBffRoute_thenUnauthorized() throws Exception {
    mockMvc
        .perform(get(CONTEXT_PATH + "/bff/api/v1/me/delegations").contextPath(CONTEXT_PATH))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void givenUnknownSessionCookie_whenGetBffRoute_thenUnauthorizedFromTheApiWithoutRedirection()
      throws Exception {
    mockMvc
        .perform(getWithUnknownSession("/bff/api/v1/me/delegations"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().string(org.hamcrest.Matchers.not(INVALID_SESSION_BODY)));
  }

  /**
   * The logout filter runs before the session management: a logout with a stale session cookie
   * proceeds (RP-initiated logout, {@code 202} with the URI to follow), never a redirection.
   */
  @Test
  void givenUnknownSessionCookie_whenLogout_thenAcceptedWithoutRedirection() throws Exception {
    mockMvc
        .perform(
            post(CONTEXT_PATH + "/logout")
                .contextPath(CONTEXT_PATH)
                .with(csrf())
                .with(unknownSession()))
        .andExpect(status().isAccepted());
  }
}
