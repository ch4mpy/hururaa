package pf.hururaa.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.jayway.jsonpath.JsonPath;
import pf.hururaa.problem.ProblemResponses;
import tools.jackson.databind.json.JsonMapper;

/**
 * The gateway's invalid-session strategy answers {@code 401} {@code unauthenticated} without
 * opening a session (unlike spring-addons' one): the requests the SPA sends in parallel with a stale
 * cookie don't each leave an orphaned session behind (docs/intent/0003). Checked on the strategy
 * itself: in a MockMvc test, the security test support opens a session of its own.
 */
class ProblemSecurityConfigurationTest {

  final ProblemResponses problems = new ProblemResponses(JsonMapper.builder().build());

  @Test
  void givenAnInvalidSession_whenDetected_thenUnauthenticatedProblemAndNoNewSession()
      throws Exception {
    final var request = new MockHttpServletRequest("GET", "/gateway/bff/api/v1/me/delegations");
    request.setRequestedSessionId("0123456789ABCDEF0123456789ABCDEF");
    request.setRequestedSessionIdValid(false);
    final var response = new MockHttpServletResponse();

    new ProblemSecurityConfiguration().invalidSessionStrategy(problems)
        .onInvalidSessionDetected(request, response);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(JsonPath.<String>read(response.getContentAsString(), "$.type"))
        .isEqualTo("urn:hururaa:problem:unauthenticated");
    assertThat(request.getSession(false)).isNull();
  }
}
