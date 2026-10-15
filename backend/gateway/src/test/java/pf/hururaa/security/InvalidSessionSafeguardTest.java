package pf.hururaa.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import pf.hururaa.problem.ProblemType;

/**
 * The safeguard of the session probe: with spring-addons'
 * {@code invalid-session.pass-through-patterns} emptied, a session cookie the gateway no longer
 * knows sends {@code /me} to the gateway's invalid-session strategy, whose {@code 401}
 * {@code unauthenticated} problem the SPA treats as logged out, never spring-addons' default
 * {@code 302} that took the SPA out of the gateway
 * (docs/intent/0001, 0002, 0003). Same infrastructure as {@link InvalidSessionTest}.
 */
@SpringBootTest(
    properties = {
      "issuer=http://localhost:8089/auth/realms/public-facing",
      "rest-api-v1=http://localhost:8090",
      "server.ssl.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false",
      "com.c4-soft.springaddons.oidc.client.invalid-session.pass-through-patterns="
    })
@ActiveProfiles("no-observability")
@AutoConfigureMockMvc
@EnableWireMock({
  @ConfigureWireMock(name = "keycloak", port = 8089, filesUnderClasspath = "wiremock/keycloak"),
  @ConfigureWireMock(name = "hururaa-api", port = 8090, filesUnderClasspath = "wiremock/hururaa-api")
})
class InvalidSessionSafeguardTest {
  private static final String CONTEXT_PATH = "/gateway";

  @Autowired MockMvc mockMvc;

  @Test
  void givenPassThroughOffAndUnknownSessionCookie_whenGetMe_thenUnauthorizedFromTheStrategy()
      throws Exception {
    mockMvc
        .perform(
            get(CONTEXT_PATH + "/me")
                .contextPath(CONTEXT_PATH)
                .with(
                    request -> {
                      request.setRequestedSessionId("0123456789ABCDEF0123456789ABCDEF");
                      request.setRequestedSessionIdValid(false);
                      request.setRequestedSessionIdFromCookie(true);
                      return request;
                    }))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(ProblemType.UNAUTHENTICATED.uri().toString()));
  }
}
