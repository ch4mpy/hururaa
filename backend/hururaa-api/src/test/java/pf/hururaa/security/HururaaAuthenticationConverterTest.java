package pf.hururaa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static pf.hururaa.security.HururaaJwtFixture.ROLES_NAMESPACE;
import static pf.hururaa.security.HururaaJwtFixture.fixtureClaims;
import static pf.hururaa.security.HururaaJwtFixture.jwt;
import static pf.hururaa.uaa.HururaaPermission.DIRECTION_ADMIN;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import pf.hururaa.commons.security.TenantPermissionsExtractor;
import pf.hururaa.commons.security.HururaaAuthentication;
import pf.hururaa.uaa.HururaaPermission;
import pf.hururaa.uaa.UaaProperties;

class HururaaAuthenticationConverterTest {

  private static HururaaAuthenticationConverter converter(String rolesNamespace) {
    return new HururaaAuthenticationConverter(new TenantPermissionsExtractor(rolesNamespace),
        new UaaProperties("dsi"));
  }

  @Test
  void convertsJwtIntoAHururaaJwtAuthenticationWithPermissionsFromTheExtractor() {
    final var authentication = converter(ROLES_NAMESPACE).convert(jwt(fixtureClaims()));

    assertThat(authentication).isInstanceOf(HururaaJwtAuthentication.class);
    assertThat(((HururaaAuthentication) authentication).getPermissionsByTenant().get("dsi"))
        .contains(DIRECTION_ADMIN.value());
  }

  @Test
  void usesTheConfiguredRolesNamespaceRatherThanAHardcodedOne() {
    final var authentication =
        (HururaaAuthentication) converter("custom-client").convert(jwt(fixtureClaims()));

    assertThat(authentication.getPermissionsByTenant().get("dsi")).isEmpty();
  }

  @Test
  void preservesTheOriginalJwtAsUnderlyingToken() {
    final var sourceJwt = jwt(fixtureClaims());

    final var authentication =
        (HururaaJwtAuthentication) converter(ROLES_NAMESPACE).convert(sourceJwt);

    assertThat(authentication.getTokenAttributes().getTokenValue())
        .isEqualTo(sourceJwt.getTokenValue());
  }

  @Test
  void grantsTheHururaaRolesHeldInTheDsiAsAuthorities() {
    final var authentication = converter(ROLES_NAMESPACE).convert(jwt(fixtureClaims()));

    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrderElementsOf(HururaaPermission.ALL);
  }

  @Test
  void grantsNoAuthorityForHururaaPermissionsHeldInAnotherOrganization() {
    final var converter = new HururaaAuthenticationConverter(
        new TenantPermissionsExtractor(ROLES_NAMESPACE), new UaaProperties("dpam"));

    assertThat(converter.convert(jwt(fixtureClaims())).getAuthorities()).isEmpty();
  }

  @Test
  void exposesTheSubjectClaimAsName() {
    final var authentication = converter(ROLES_NAMESPACE).convert(jwt(fixtureClaims()));

    assertThat(authentication.getName()).isEqualTo("b3029704-4137-4d49-93f5-7f1aef4279cd");
  }
}
