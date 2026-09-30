package pf.hururaa.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import com.nimbusds.jose.util.JSONObjectUtils;
import pf.hururaa.commons.security.TenantPermissionsExtractor;

/** OIDC fixtures shared by the {@code pf.hururaa.security} unit tests. */
public final class HururaaOidcUserFixture {

  static final String ROLES_NAMESPACE = "hururaa-api";

  /**
   * A synthetic ID token shaped like the README's access token: a user member of two
   * organizations, with {@code hururaa.templates.read} on tenant1 and the full role set on tenant3.
   */
  static final String ID_TOKEN_CLAIMS_JSON =
      """
      {
        "iss": "https://host.docker.internal/auth/realms/public-facing",
        "sub": "b3029704-4137-4d49-93f5-7f1aef4279cd",
        "aud": ["hururaa-bff"],
        "azp": "hururaa-bff",
        "email_verified": true,
        "organization": {
          "tenant1": {
            "resource_access": { "hururaa-api": { "roles": ["hururaa.templates.read"] } },
            "groups": ["/worker"]
          },
          "tenant3": {
            "resource_access": {
              "hururaa-api": {
                "roles": [
                  "hururaa.forms.edit-any", "hururaa.forms.read-any", "hururaa.permissions.edit",
                  "hururaa.permissions.grant", "hururaa.permissions.read", "hururaa.templates.edit",
                  "hururaa.templates.read", "hururaa.users.read"
                ]
              }
            },
            "groups": ["/human-resources", "/manager"]
          }
        },
        "preferred_username": "manager3",
        "given_name": "Thor",
        "family_name": "Tellini",
        "email": "thortellini@tenant3.pf"
      }
      """;

  private HururaaOidcUserFixture() {}

  /** Claims as Nimbus parses them (nested objects are shaded-Gson maps, not JDK ones). */
  static Map<String, Object> nimbusParsedClaims() {
    try {
      return JSONObjectUtils.parse(ID_TOKEN_CLAIMS_JSON);
    } catch (java.text.ParseException e) {
      throw new IllegalStateException(e);
    }
  }

  static OidcIdToken idToken() {
    final var now = Instant.now();
    return new OidcIdToken("id-token", now, now.plusSeconds(300), nimbusParsedClaims());
  }

  static OidcUserInfo userInfo() {
    return new OidcUserInfo(nimbusParsedClaims());
  }

  static DefaultOidcUser defaultOidcUser() {
    return new DefaultOidcUser(
        List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken(), userInfo(), "preferred_username");
  }

  public static HururaaOidcUser hururaaOidcUser() {
    return hururaaOidcUser(
        new TenantPermissionsExtractor(ROLES_NAMESPACE).extract(defaultOidcUser().getClaims()));
  }

  /** The same user, with the given per-tenant permissions instead of those of its claims. */
  public static HururaaOidcUser hururaaOidcUser(Map<String, Set<String>> permissionsByTenant) {
    final var oidcUser = defaultOidcUser();
    return new HururaaOidcUser(
        oidcUser.getAuthorities(),
        oidcUser.getIdToken(),
        oidcUser.getUserInfo(),
        "preferred_username",
        permissionsByTenant);
  }
}
