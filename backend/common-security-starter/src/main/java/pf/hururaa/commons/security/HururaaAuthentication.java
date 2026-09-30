package pf.hururaa.commons.security;

import java.util.Map;
import java.util.Set;

/**
 * Exposes the permissions granted to the user for each tenant (organisation) it belongs to.
 * <p>
 * This is all the {@link HururaaPermissionEvaluator} needs. It is implemented either by the
 * {@link org.springframework.security.core.Authentication} itself (resource server: an
 * {@code OAuthentication} built from the JWT access token) or by its principal (BFF: the
 * {@code OidcUser} of an {@code oauth2Login} session). How the permissions are extracted is left
 * to each application, see {@link TenantPermissionsExtractor}.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public interface HururaaAuthentication {

  /**
   * @return an unmodifiable map of the permissions granted to the user, indexed by tenant
   *         (organisation) name
   */
  Map<String, Set<String>> getPermissionsByTenant();
}
