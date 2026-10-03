package pf.hururaa.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import pf.hururaa.uaa.HururaaPermission;

/** JWT fixtures shared by the {@code pf.hururaa.security} unit tests. */
final class HururaaJwtFixture {

  static final String ROLES_NAMESPACE = "hururaa-api";

  private HururaaJwtFixture() {}

  static Jwt jwt(Map<String, Object> claims) {
    return Jwt
        .withTokenValue("test-token")
        .header("alg", "none")
        .claims(c -> c.putAll(claims))
        .build();
  }

  /**
   * A synthetic access token: a user member of two directions, with no Hurura'a role in dpam (via
   * {@code /escales.agent}, granting roles of escales-api only) and {@link HururaaPermission#ALL}
   * of them in dsi (via {@code /hururaa.admins}).
   */
  static Map<String, Object> fixtureClaims() {
    final var claims = new LinkedHashMap<String, Object>();
    claims.put("iss", "https://host.docker.internal/auth/realms/public-facing");
    claims.put("sub", "b3029704-4137-4d49-93f5-7f1aef4279cd");
    claims.put("azp", "hururaa-bff");
    claims.put("scope", "openid profile organization email");
    claims.put("organization", fixtureOrganization());
    claims.put("preferred_username", "hururaa.admin");
    claims.put("given_name", "Hina");
    claims.put("family_name", "Teriierooiterai");
    claims.put("email", "hururaa.admin@gov.pf");
    return claims;
  }

  private static Map<String, Object> fixtureOrganization() {
    final var dpam = Map
        .of(
            "resource_access",
            Map.of("escales-api", Map.of("roles", List.of("escales.bookings.read"))),
            "groups",
            List.of("/escales.agent"));
    final var dsi = Map
        .of(
            "resource_access",
            Map.of(ROLES_NAMESPACE, Map.of("roles", List.copyOf(HururaaPermission.ALL))),
            "groups",
            List.of("/hururaa.admins"));
    return Map.of("dpam", dpam, "dsi", dsi);
  }
}
