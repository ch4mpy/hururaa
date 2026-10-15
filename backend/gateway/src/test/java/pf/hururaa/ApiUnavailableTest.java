package pf.hururaa;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import pf.hururaa.problem.ProblemType;

/**
 * hururaa-api unreachable: the gateway answers {@code 503} {@code service-unavailable}, without the
 * API's address (docs/intent/0003). Nothing listens on {@code rest-api-v1}'s port.
 */
@SpringBootTest(
    properties = {
      "issuer=http://localhost:8089/auth/realms/public-facing",
      "rest-api-v1=http://localhost:1",
      "server.ssl.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false"
    })
@ActiveProfiles("no-observability")
@AutoConfigureMockMvc
@EnableWireMock({
  @ConfigureWireMock(name = "keycloak", port = 8089, filesUnderClasspath = "wiremock/keycloak")
})
class ApiUnavailableTest {
  private static final String CONTEXT_PATH = "/gateway";

  @Autowired MockMvc mockMvc;

  @Test
  void givenTheApiUnreachable_whenCallIt_thenServiceUnavailableProblem() throws Exception {
    mockMvc
        .perform(get(CONTEXT_PATH + "/bff/api/v1/me/delegations").contextPath(CONTEXT_PATH))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(ProblemType.SERVICE_UNAVAILABLE.uri().toString()))
        .andExpect(content().string(not(containsString("localhost"))));
  }
}
