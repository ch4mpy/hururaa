package pf.hururaa.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pf.hururaa.commons.security.HururaaClaimsFixture.ROLES_NAMESPACE;
import static pf.hururaa.commons.security.HururaaClaimsFixture.fixtureClaims;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantPermissionsExtractorTest {

  private final TenantPermissionsExtractor extractor = new TenantPermissionsExtractor(ROLES_NAMESPACE);

  @Test
  void extractsPermissionsPerTenantFromFixture() {
    final var permissions = extractor.extract(fixtureClaims());

    assertThat(permissions).containsOnlyKeys("tenant1", "tenant3");
    assertThat(permissions.get("tenant1")).containsExactlyInAnyOrder("hururaa.templates.read");
    assertThat(permissions.get("tenant3"))
        .containsExactlyInAnyOrder(
            "hururaa.forms.edit-any",
            "hururaa.forms.read-any",
            "hururaa.permissions.edit",
            "hururaa.permissions.grant",
            "hururaa.permissions.read",
            "hururaa.templates.edit",
            "hururaa.templates.read",
            "hururaa.users.read");
  }

  @Test
  void tenantWithoutResourceAccessYieldsEmptyPermissions() {
    final var claims = fixtureClaims();
    claims.put("organization", Map.of("tenant2", Map.of("groups", List.of("/worker"))));

    assertThat(extractor.extract(claims).get("tenant2")).isEmpty();
  }

  @Test
  void tenantWithResourceAccessForAnotherClientYieldsEmptyPermissions() {
    final var claims = fixtureClaims();
    claims.put(
        "organization",
        Map.of(
            "tenant2",
            Map.of("resource_access", Map.of("other-client", Map.of("roles", List.of("some.role"))))));

    assertThat(extractor.extract(claims).get("tenant2")).isEmpty();
  }

  @Test
  void tenantWithNoRolesYieldsEmptyPermissions() {
    final var claims = fixtureClaims();
    claims.put(
        "organization", Map.of("tenant2", Map.of("resource_access", Map.of(ROLES_NAMESPACE, Map.of()))));

    assertThat(extractor.extract(claims).get("tenant2")).isEmpty();
  }

  @Test
  void tenantValueWhichIsNotAMapYieldsEmptyPermissions() {
    final var claims = fixtureClaims();
    claims.put("organization", Map.of("tenant2", "oops"));

    assertThat(extractor.extract(claims).get("tenant2")).isEmpty();
  }

  @Test
  void emptyOrganizationClaimYieldsEmptyMap() {
    final var claims = fixtureClaims();
    claims.put("organization", Map.of());

    assertThat(extractor.extract(claims)).isEmpty();
  }

  @Test
  void missingOrganizationClaimYieldsEmptyMap() {
    final var claims = fixtureClaims();
    claims.remove("organization");

    assertThat(extractor.extract(claims)).isEmpty();
  }

  @Test
  void permissionsByTenantMapIsUnmodifiable() {
    final var permissions = extractor.extract(fixtureClaims());

    assertThatThrownBy(() -> permissions.put("tenant4", Set.of()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void permissionsPerTenantSetIsUnmodifiable() {
    final var permissions = extractor.extract(fixtureClaims());

    assertThatThrownBy(() -> permissions.get("tenant1").add("extra"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void usesTheGivenRolesNamespaceRatherThanAnotherClientsRoles() {
    final var claims = fixtureClaims();
    claims.put(
        "organization",
        Map.of(
            "tenant2",
            Map.of(
                "resource_access",
                Map.of(
                    "hururaa-api",
                    Map.of("roles", List.of("hururaa.templates.read")),
                    "custom-client",
                    Map.of("roles", List.of("custom.permission"))))));

    assertThat(new TenantPermissionsExtractor("custom-client").extract(claims).get("tenant2"))
        .containsExactly("custom.permission");
  }

  @Test
  void organizationClaimWhichIsNotAMapYieldsEmptyMap() {
    assertThat(extractor.extract(Map.of("organization", "tenant3"))).isEmpty();
    assertThat(extractor.extract(Map.of("organization", List.of("tenant3")))).isEmpty();
  }

  @Test
  void malformedResourceAccessOrRolesYieldEmptyPermissionsRatherThanFailing() {
    assertThat(extractor.extract(Map.of("organization",
        Map.of("tenant3", Map.of("resource_access", "not-a-map"))))).containsEntry("tenant3", Set.of());
    assertThat(extractor.extract(Map.of("organization",
        Map.of("tenant3", Map.of("resource_access", Map.of("hururaa-api", "not-a-map"))))))
        .containsEntry("tenant3", Set.of());
    assertThat(extractor.extract(Map.of("organization",
        Map.of("tenant3", Map.of("resource_access", Map.of("hururaa-api", Map.of("roles", "not-a-list")))))))
        .containsEntry("tenant3", Set.of());
  }

  @Test
  void nonStringRolesAreIgnored() {
    assertThat(extractor.extract(Map.of("organization",
        Map.of("tenant3", Map.of("resource_access", Map.of("hururaa-api",
            Map.of("roles", List.of("hururaa.templates.read", 42))))))))
        .containsEntry("tenant3", Set.of("hururaa.templates.read"));
  }
}
