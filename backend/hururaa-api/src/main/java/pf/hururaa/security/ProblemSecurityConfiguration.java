package pf.hururaa.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.ResourceServerSynchronizedHttpSecurityPostProcessor;
import pf.hururaa.problem.ProblemAccessDeniedHandler;
import pf.hururaa.problem.ProblemAuthenticationEntryPoint;
import pf.hururaa.problem.ProblemResponses;
import pf.hururaa.problem.ProblemType;

/**
 * The resource server's {@code 401} and {@code 403} as problems ({@link ProblemType#UNAUTHENTICATED},
 * {@link ProblemType#ACCESS_DENIED}) instead of spring-addons' bare {@code sendError}
 * (docs/intent/0003). The post-processor is applied last to the resource server's filter chain, so
 * its exception handling replaces spring-addons' entry point; the {@code Bearer} challenge is kept.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration(proxyBeanMethods = false)
public class ProblemSecurityConfiguration {

  @Bean
  ResourceServerSynchronizedHttpSecurityPostProcessor problemExceptionHandling(
      ProblemResponses problems) {
    final var entryPoint = new ProblemAuthenticationEntryPoint(problems, "Bearer");
    return http -> http
        // no token: the exception translation's entry point
        .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(new ProblemAccessDeniedHandler(problems)))
        // a token, rejected (expired, bad signature, other issuer...): BearerTokenAuthenticationFilter
        // has its own entry point, set by this configurer (review of PR #26)
        .oauth2ResourceServer(resourceServer -> resourceServer.authenticationEntryPoint(entryPoint));
  }
}
