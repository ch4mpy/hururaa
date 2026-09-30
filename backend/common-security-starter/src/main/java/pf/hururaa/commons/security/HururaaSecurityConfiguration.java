package pf.hururaa.commons.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables method security and registers the {@link TenantPermissionsExtractor} (used by the
 * applications to build their {@link HururaaAuthentication} implementation) and the
 * {@link HururaaPermissionEvaluator} (behind the {@code hasPermission(...)} SpEL function, and also
 * exposed as the {@code @tpe} bean for {@code @tpe.isActive(authentication, tenant)}).
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
@EnableMethodSecurity
@Import({TenantPermissionsExtractor.class, HururaaPermissionEvaluator.class})
public class HururaaSecurityConfiguration {

  /**
   * Spring Security does not pick {@link org.springframework.security.access.PermissionEvaluator}
   * beans on its own: the expression handler has to be wired explicitly. It is {@code static}
   * because method security infrastructure is initialized early in the context lifecycle.
   *
   * @param permissionEvaluator the evaluator behind {@code hasPermission(...)}
   * @return the method-security expression handler to use in {@code @PreAuthorize} & co.
   */
  @Bean
  static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      HururaaPermissionEvaluator permissionEvaluator) {
    final var handler = new DefaultMethodSecurityExpressionHandler();
    handler.setPermissionEvaluator(permissionEvaluator);
    return handler;
  }
}
