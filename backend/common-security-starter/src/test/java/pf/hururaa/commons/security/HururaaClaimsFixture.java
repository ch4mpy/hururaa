package pf.hururaa.commons.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Claims fixtures shared by the {@code pf.hururaa.commons.security} unit tests. */
final class HururaaClaimsFixture {

  static final String ROLES_NAMESPACE = "hururaa-api";

  private HururaaClaimsFixture() {}

  /**
   * A synthetic access token shaped like the README's: a user member of two organizations, with
   * {@code hururaa.templates.read} on tenant1 (via {@code /worker}) and the full forms/permissions
   * /templates/users role set on tenant3 (via {@code /human-resources} and {@code /manager}).
   */
  static Map<String, Object> fixtureClaims() {
    final var claims = new LinkedHashMap<String, Object>();
    claims.put("iss", "https://host.docker.internal/auth/realms/public-facing");
    claims.put("sub", "b3029704-4137-4d49-93f5-7f1aef4279cd");
    claims.put("azp", "hururaa-bff");
    claims.put("scope", "openid profile organization email");
    claims.put("organization", fixtureOrganization());
    claims.put("preferred_username", "manager3");
    claims.put("given_name", "Thor");
    claims.put("family_name", "Tellini");
    claims.put("email", "thortellini@tenant3.pf");
    return claims;
  }

  private static Map<String, Object> fixtureOrganization() {
    final var tenant1 =
        Map.of(
            "resource_access",
            Map.of("hururaa-api", Map.of("roles", List.of("hururaa.templates.read"))),
            "groups",
            List.of("/worker"));
    final var tenant3 =
        Map.of(
            "resource_access",
            Map.of(
                "hururaa-api",
                Map.of(
                    "roles",
                    List.of(
                        "hururaa.forms.edit-any",
                        "hururaa.forms.read-any",
                        "hururaa.permissions.edit",
                        "hururaa.permissions.grant",
                        "hururaa.permissions.read",
                        "hururaa.templates.edit",
                        "hururaa.templates.read",
                        "hururaa.users.read"))),
            "groups",
            List.of("/human-resources", "/manager"));
    return Map.of("tenant1", tenant1, "tenant3", tenant3);
  }
}
