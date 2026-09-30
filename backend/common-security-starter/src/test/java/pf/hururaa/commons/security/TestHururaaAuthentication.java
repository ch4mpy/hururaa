package pf.hururaa.commons.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** Minimal {@link HururaaAuthentication} implementation for the starter's unit tests. */
final class TestHururaaAuthentication extends AbstractAuthenticationToken
    implements HururaaAuthentication {
  private static final long serialVersionUID = 1L;

  private final String name;
  private final Map<String, Set<String>> permissionsByTenant;

  TestHururaaAuthentication(String name, Map<String, Set<String>> permissionsByTenant) {
    super(List.of());
    this.name = name;
    this.permissionsByTenant = permissionsByTenant;
    setAuthenticated(true);
  }

  static TestHururaaAuthentication fromFixture() {
    return new TestHururaaAuthentication(
        "manager3",
        new TenantPermissionsExtractor(HururaaClaimsFixture.ROLES_NAMESPACE)
            .extract(HururaaClaimsFixture.fixtureClaims()));
  }

  @Override
  public Map<String, Set<String>> getPermissionsByTenant() {
    return permissionsByTenant;
  }

  @Override
  public Object getCredentials() {
    return "";
  }

  @Override
  public Object getPrincipal() {
    return name;
  }

  @Override
  public String getName() {
    return name;
  }
}
