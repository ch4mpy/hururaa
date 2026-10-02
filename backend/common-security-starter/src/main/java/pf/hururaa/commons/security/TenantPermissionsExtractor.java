package pf.hururaa.commons.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Extracts the per-tenant permissions from the Keycloak {@value #ORGANIZATION_CLAIM} claim, which
 * is shaped as follows (the roles namespace being the ID of the Keycloak client whose roles are
 * read, see the {@code roles-namespace} property):
 *
 * <pre>
 * "organization": {
 *   "dsi": {
 *     "resource_access": { "hururaa-api": { "roles": [ "hururaa.admin" ] } },
 *     "groups": [ "/hururaa.admin" ]
 *   },
 *   "dpam": {
 *     "groups": [ ]
 *   },
 *   ...
 * }
 * </pre>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
public class TenantPermissionsExtractor {

  public static final String ORGANIZATION_CLAIM = "organization";

  private final String rolesNamespace;

  public TenantPermissionsExtractor(@Value("${roles-namespace}") String rolesNamespace) {
    this.rolesNamespace = rolesNamespace;
  }

  /**
   * @param claims the claims of a token (access, ID, ...) or of a user-info response
   * @return an unmodifiable map of the permissions found in the {@value #ORGANIZATION_CLAIM}
   *         claim, indexed by tenant name (empty if the claim is missing)
   */
  public Map<String, Set<String>> extract(Map<String, Object> claims) {
    // Every level is checked rather than cast: the claims are Keycloak's, but a mapper or a
    // brokered identity provider can shape them differently, and a ClassCastException while
    // converting a token is a 500 on every request of that user.
    if (!(claims.get(ORGANIZATION_CLAIM) instanceof Map<?, ?> organization)) {
      return Map.of();
    }
    return Collections
        .unmodifiableMap(
            organization
                .entrySet()
                .stream()
                .filter(e -> e.getKey() instanceof String)
                .collect(
                    Collectors
                        .toMap(e -> (String) e.getKey(), e -> extractPermissions(e.getValue()))));
  }

  private Set<String> extractPermissions(@Nullable Object tenantValue) {
    if (!(tenantValue instanceof Map<?, ?> tenant)
        || !(tenant.get("resource_access") instanceof Map<?, ?> resourceAccess)
        || !(resourceAccess.get(rolesNamespace) instanceof Map<?, ?> client)
        || !(client.get("roles") instanceof Collection<?> roles)) {
      return Set.of();
    }
    return roles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .collect(Collectors.toUnmodifiableSet());
  }
}
