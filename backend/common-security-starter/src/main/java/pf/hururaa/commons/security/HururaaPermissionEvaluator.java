package pf.hururaa.commons.security;

import java.io.Serializable;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * {@link PermissionEvaluator} backing the {@code hasPermission(...)} SpEL function: the "target" is
 * a tenant (organisation) name and the "permission" is one of the roles granted to the user for
 * that tenant (see {@link HururaaAuthentication#getPermissionsByTenant()}).
 * <p>
 * Usage in {@code @PreAuthorize}:
 * </p>
 * <ul>
 * <li>{@code hasPermission(#tenant, 'hururaa.users.read')}</li>
 * <li>{@code hasPermission(#tenant, 'tenant', 'hururaa.users.read')} (4-args flavour, the only
 * supported target type is {@value #TENANT_TARGET_TYPE})</li>
 * <li>{@code @tpe.isActive(authentication, #tenant)}</li>
 * <li>{@code @tpe.isMember(authentication, #tenant)}</li>
 * </ul>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component(value = "tpe")
public class HururaaPermissionEvaluator implements PermissionEvaluator {

  public static final String TENANT_TARGET_TYPE = "tenant";

  /**
   * @param authentication the security-context authentication
   * @param targetDomainObject the tenant (organisation) name
   * @param permission the permission name
   * @return true if {@code authentication} is a {@link HururaaAuthentication} granted with
   *         {@code permission} for the {@code targetDomainObject} tenant
   */
  @Override
  public boolean hasPermission(
      Authentication authentication,
      Object targetDomainObject,
      Object permission) {
    if (!(targetDomainObject instanceof String tenant) || !(permission instanceof String perm)) {
      return false;
    }
    return permissionsFor(authentication, tenant).contains(perm);
  }

  /**
   * @param authentication the security-context authentication
   * @param targetId the tenant (organisation) name
   * @param targetType must be {@value #TENANT_TARGET_TYPE} (case insensitive)
   * @param permission the permission name
   * @return true if {@code targetType} is {@value #TENANT_TARGET_TYPE} and {@code authentication}
   *         is a {@link HururaaAuthentication} granted with {@code permission} for the
   *         {@code targetId} tenant
   */
  @Override
  public boolean hasPermission(
      Authentication authentication,
      Serializable targetId,
      String targetType,
      Object permission) {
    if (!TENANT_TARGET_TYPE.equalsIgnoreCase(targetType)) {
      return false;
    }
    return hasPermission(authentication, targetId, permission);
  }

  /**
   * @param authentication the security-context authentication
   * @param tenant the tenant (organisation) name
   * @return true if {@code authentication} is a {@link HururaaAuthentication} granted with at least
   *         one permission for {@code tenant}
   */
  public boolean isActive(Authentication authentication, String tenant) {
    return !permissionsFor(authentication, tenant).isEmpty();
  }

  /**
   * @param authentication the security-context authentication
   * @param tenant the tenant (organisation) name
   * @return true if {@code authentication} is a {@link HururaaAuthentication} member of
   *         {@code tenant}, whether or not it holds permissions there (the {@code organization}
   *         claim lists every membership, with an empty role set for those granting none)
   */
  public boolean isMember(@Nullable Authentication authentication, String tenant) {
    if (authentication instanceof HururaaAuthentication auth) {
      return auth.getPermissionsByTenant().containsKey(tenant);
    }
    if (authentication != null
        && authentication.getPrincipal() instanceof HururaaAuthentication principal) {
      return principal.getPermissionsByTenant().containsKey(tenant);
    }
    return false;
  }

  /**
   * The {@link HururaaAuthentication} is either the authentication itself (resource server) or its
   * principal (BFF, {@code oauth2Login}).
   */
  private static Set<String> permissionsFor(@Nullable Authentication authentication, String tenant) {
    if (authentication instanceof HururaaAuthentication tickAuth) {
      return tickAuth.getPermissionsByTenant().getOrDefault(tenant, Set.of());
    }
    if (authentication != null
        && authentication.getPrincipal() instanceof HururaaAuthentication tickPrincipal) {
      return tickPrincipal.getPermissionsByTenant().getOrDefault(tenant, Set.of());
    }
    return Set.of();
  }
}
