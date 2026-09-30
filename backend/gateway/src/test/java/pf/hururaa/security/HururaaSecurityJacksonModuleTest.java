package pf.hururaa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static pf.hururaa.security.HururaaOidcUserFixture.hururaaOidcUser;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * Round-trips a security context holding a {@link HururaaOidcUser} through JSON, the way a
 * JSON-serialized session store would.
 */
class HururaaSecurityJacksonModuleTest {

  private final JsonMapper mapper =
      JsonMapper
          .builder()
          .addModules(HururaaSecurityJacksonModule.getModules(getClass().getClassLoader()))
          .build();

  private static SecurityContextImpl securityContext(HururaaOidcUser user) {
    return new SecurityContextImpl(
        new OAuth2AuthenticationToken(user, user.getAuthorities(), "hururaa-bff"));
  }

  @Test
  void roundTripsAHururaaOidcUserWithNimbusParsedClaimsAndImmutablePermissions() {
    final var user = hururaaOidcUser();
    assertThat(user.getIdToken().getClaims().get("organization").getClass().getName())
        .as("fixture claims are parsed by Nimbus (shaded Gson)")
        .isEqualTo(HururaaSecurityJacksonModule.NIMBUS_GSON_MAP);

    final var json = mapper.writeValueAsString(securityContext(user));
    final var restored = mapper.readValue(json, SecurityContextImpl.class);

    assertThat(restored.getAuthentication()).isInstanceOf(OAuth2AuthenticationToken.class);
    assertThat(restored.getAuthentication().getPrincipal()).isInstanceOf(HururaaOidcUser.class);
    final var restoredUser = (HururaaOidcUser) restored.getAuthentication().getPrincipal();
    assertThat(restoredUser.getName()).isEqualTo("manager3");
    assertThat(restoredUser.getPermissionsByTenant()).isEqualTo(user.getPermissionsByTenant());
    assertThat(restoredUser.getAuthorities()).isEqualTo(user.getAuthorities());
    assertThat(restoredUser.getIdToken().getTokenValue()).isEqualTo("id-token");
    assertThat(restoredUser.getClaimAsMap("organization")).containsOnlyKeys("tenant1", "tenant3");
    @SuppressWarnings("unchecked")
    final var tenant1 =
        (Map<String, Object>) restoredUser.getClaimAsMap("organization").get("tenant1");
    assertThat(tenant1).containsKeys("resource_access", "groups");
  }

  @Test
  void roundTripsAHururaaOidcUserWithoutUserInfo() {
    final var source = hururaaOidcUser();
    final var user =
        new HururaaOidcUser(
            source.getAuthorities(),
            source.getIdToken(),
            null,
            null,
            source.getPermissionsByTenant());

    final var json = mapper.writeValueAsString(securityContext(user));
    final var restored = mapper.readValue(json, SecurityContextImpl.class);

    final var restoredUser = (HururaaOidcUser) restored.getAuthentication().getPrincipal();
    assertThat(restoredUser.getName()).isEqualTo("b3029704-4137-4d49-93f5-7f1aef4279cd");
    assertThat(restoredUser.getUserInfo()).isNull();
    assertThat(restoredUser.getPermissionsByTenant()).isEqualTo(user.getPermissionsByTenant());
  }
}
