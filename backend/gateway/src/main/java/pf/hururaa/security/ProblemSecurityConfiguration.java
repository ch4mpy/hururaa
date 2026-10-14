package pf.hururaa.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.session.InvalidSessionStrategy;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.client.ClientSynchronizedHttpSecurityPostProcessor;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.ResourceServerExpressionInterceptUrlRegistryPostProcessor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.problem.ProblemAccessDeniedHandler;
import pf.hururaa.problem.ProblemAuthenticationEntryPoint;
import pf.hururaa.problem.ProblemResponses;
import pf.hururaa.problem.ProblemType;

/**
 * The gateway's security errors as problems (docs/intent/0003), in place of spring-addons' plain
 * text bodies, which its properties can't change:
 *
 * <ul>
 * <li>no session on a route that needs one: {@code 401} {@link ProblemType#UNAUTHENTICATED}, with
 * no login URL nor {@code Location} (the SPA logs in through {@code /oauth2/authorization});</li>
 * <li>a session cookie the gateway no longer knows, on any route but {@code /me}
 * ({@code invalid-session.pass-through-patterns}): the same {@code 401}. Unlike spring-addons'
 * strategy, no session is opened: the requests the SPA sends in parallel with a stale cookie don't
 * each leave an orphaned one behind, and the SPA's next call to {@code /me}, which a {@code 401}
 * triggers, opens the new one;</li>
 * <li>an access rule or the CSRF protection refusing an authenticated request: {@code 403}
 * {@link ProblemType#ACCESS_DENIED};</li>
 * <li>the resource server filter chain, which handles every route outside spring-addons' client
 * {@code security-matchers}, requires a token for the actuator only: the gateway serves nothing
 * else to bearers, and an unknown route then reaches Spring MVC's {@code 404} (a
 * {@link ProblemType#NOT_FOUND}) instead of this chain's {@code 401}.</li>
 * </ul>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class ProblemSecurityConfiguration {

  /** Replaces spring-addons' default entry point of the client filter chain. */
  @Bean
  AuthenticationEntryPoint authenticationEntryPoint(ProblemResponses problems) {
    return new ProblemAuthenticationEntryPoint(problems, null);
  }

  /** Replaces spring-addons' invalid-session strategy. */
  @Bean
  InvalidSessionStrategy invalidSessionStrategy(ProblemResponses problems) {
    return (request, response) -> {
      log.debug("Invalid session on {}", request.getRequestURI());
      problems.write(request, response, ProblemType.UNAUTHENTICATED);
    };
  }

  @Bean
  ClientSynchronizedHttpSecurityPostProcessor problemAccessDenied(ProblemResponses problems) {
    return http -> http.exceptionHandling(
        exceptions -> exceptions.accessDeniedHandler(new ProblemAccessDeniedHandler(problems)));
  }

  @Bean
  ResourceServerExpressionInterceptUrlRegistryPostProcessor actuatorOnlyRequiresAToken() {
    return registry -> registry.requestMatchers("/actuator/**").authenticated().anyRequest()
        .permitAll();
  }
}
