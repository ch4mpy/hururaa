package pf.hururaa.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

/**
 * How the whole security chain treats a session cookie the gateway no longer knows (browser
 * reopened, session expired, gateway restarted): a {@code 401}, never a redirection, on the session
 * probe ({@code /me}) as on the BFF routes (docs/intent/0001). Keycloak's discovery documents are
 * served by WireMock, as for the {@code openapi} Maven profile, and so is {@code hururaa-api}, which
 * answers {@code 401} to what the gateway relays without a token (no session); the RabbitMQ
 * listeners (resource events) don't start, so no broker is needed.
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

  @Autowired MockMvc mockMvc;

  private static MockHttpServletRequestBuilder getWithUnknownSession(String path) {
    return get(CONTEXT_PATH + path)
        .contextPath(CONTEXT_PATH)
        .with(
            request -> {
              request.setRequestedSessionId("0123456789ABCDEF0123456789ABCDEF");
              request.setRequestedSessionIdValid(false);
              request.setRequestedSessionIdFromCookie(true);
              return request;
            });
  }

  @Test
  void givenNoSessionCookie_whenGetMe_thenOkAnonymous() throws Exception {
    mockMvc.perform(get(CONTEXT_PATH + "/me").contextPath(CONTEXT_PATH)).andExpect(status().isOk());
  }

  @Test
  void givenUnknownSessionCookie_whenGetMe_thenUnauthorizedWithoutRedirection() throws Exception {
    mockMvc
        .perform(getWithUnknownSession("/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(INVALID_SESSION_BODY));
  }

  @Test
  void givenNoSessionCookie_whenGetBffRoute_thenUnauthorized() throws Exception {
    mockMvc
        .perform(get(CONTEXT_PATH + "/bff/api/v1/me/delegations").contextPath(CONTEXT_PATH))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void givenUnknownSessionCookie_whenGetBffRoute_thenUnauthorizedWithoutRedirection()
      throws Exception {
    mockMvc
        .perform(getWithUnknownSession("/bff/api/v1/me/delegations"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(INVALID_SESSION_BODY));
  }
}
