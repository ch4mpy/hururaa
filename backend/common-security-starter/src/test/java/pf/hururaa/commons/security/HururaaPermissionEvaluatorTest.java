package pf.hururaa.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

class HururaaPermissionEvaluatorTest {

  private final HururaaPermissionEvaluator evaluator = new HururaaPermissionEvaluator();

  private static Authentication hururaaAuthentication() {
    return TestHururaaAuthentication.fromFixture();
  }

  @Nested
  class HasPermission {

    @Test
    void returnsTrueWhenAuthenticatedTenantHasThePermission() {
      assertThat(evaluator.hasPermission(hururaaAuthentication(), "tenant3", "hururaa.users.read"))
          .isTrue();
    }

    @Test
    void returnsFalseWhenAuthenticatedTenantLacksThePermission() {
      assertThat(evaluator.hasPermission(hururaaAuthentication(), "tenant1", "hururaa.users.read"))
          .isFalse();
    }

    @Test
    void returnsFalseWhenTenantIsUnknownToTheAuthentication() {
      assertThat(
          evaluator.hasPermission(hururaaAuthentication(), "tenant2", "hururaa.templates.read"))
              .isFalse();
    }

    @Test
    void returnsFalseWhenAuthenticationIsNull() {
      assertThat(evaluator.hasPermission(null, "tenant3", "hururaa.users.read")).isFalse();
    }

    @Test
    void returnsFalseWhenAuthenticationIsNotAHururaaAuthentication() {
      assertThat(
          evaluator
              .hasPermission(
                  new TestingAuthenticationToken("manager3", "n/a"),
                  "tenant1",
                  "hururaa.templates.read"))
                      .isFalse();
    }

    @Test
    void returnsFalseWhenTargetOrPermissionIsNotAString() {
      assertThat(evaluator.hasPermission(hururaaAuthentication(), 3, "hururaa.users.read"))
          .isFalse();
      assertThat(evaluator.hasPermission(hururaaAuthentication(), "tenant3", 42)).isFalse();
    }
  }

  @Nested
  class HasPermissionWithTargetType {

    @Test
    void returnsTrueWhenTargetTypeIsTenantAndTenantHasThePermission() {
      assertThat(
          evaluator
              .hasPermission(hururaaAuthentication(), "tenant3", "tenant", "hururaa.users.read"))
                  .isTrue();
      assertThat(
          evaluator
              .hasPermission(hururaaAuthentication(), "tenant3", "TENANT", "hururaa.users.read"))
                  .isTrue();
    }

    @Test
    void returnsFalseWhenTargetTypeIsNotTenant() {
      assertThat(
          evaluator
              .hasPermission(hururaaAuthentication(), "tenant3", "template", "hururaa.users.read"))
                  .isFalse();
      assertThat(
          evaluator.hasPermission(hururaaAuthentication(), "tenant3", null, "hururaa.users.read"))
              .isFalse();
    }

    @Test
    void returnsFalseWhenTenantLacksThePermission() {
      assertThat(
          evaluator
              .hasPermission(hururaaAuthentication(), "tenant1", "tenant", "hururaa.users.read"))
                  .isFalse();
    }
  }

  @Nested
  class WithHururaaAuthenticationAsPrincipal {

    private Authentication principalAuthentication() {
      return new TestingAuthenticationToken(TestHururaaAuthentication.fromFixture(), "n/a");
    }

    @Test
    void hasPermissionLooksAtThePrincipal() {
      assertThat(evaluator.hasPermission(principalAuthentication(), "tenant3", "hururaa.users.read"))
          .isTrue();
      assertThat(evaluator.hasPermission(principalAuthentication(), "tenant1", "hururaa.users.read"))
          .isFalse();
    }

    @Test
    void isActiveLooksAtThePrincipal() {
      assertThat(evaluator.isActive(principalAuthentication(), "tenant3")).isTrue();
      assertThat(evaluator.isActive(principalAuthentication(), "tenant2")).isFalse();
    }
  }

  @Nested
  class IsActive {

    @Test
    void returnsTrueWhenTenantHasAtLeastOnePermission() {
      assertThat(evaluator.isActive(hururaaAuthentication(), "tenant3")).isTrue();
    }

    @Test
    void returnsFalseWhenTenantIsUnknownToTheAuthentication() {
      assertThat(evaluator.isActive(hururaaAuthentication(), "tenant2")).isFalse();
    }

    @Test
    void returnsFalseWhenAuthenticationIsNull() {
      assertThat(evaluator.isActive(null, "tenant3")).isFalse();
    }

    @Test
    void returnsFalseWhenAuthenticationIsNotAHururaaAuthentication() {
      assertThat(evaluator.isActive(new TestingAuthenticationToken("manager3", "n/a"), "tenant3"))
          .isFalse();
    }
  }

  @Nested
  class IsMember {

    @Test
    void returnsTrueForAMembershipGrantingNoPermission() {
      final HururaaAuthentication member = () -> Map.of("dpam", Set.of());

      assertThat(evaluator.isMember(new TestingAuthenticationToken(member, "n/a"), "dpam"))
          .isTrue();
      assertThat(evaluator.isActive(new TestingAuthenticationToken(member, "n/a"), "dpam"))
          .isFalse();
    }

    @Test
    void returnsFalseForAnotherTenant() {
      assertThat(evaluator.isMember(hururaaAuthentication(), "tenant2")).isFalse();
      assertThat(evaluator.isMember(hururaaAuthentication(), "tenant1")).isTrue();
    }

    @Test
    void returnsFalseWhenAuthenticationIsNull() {
      assertThat(evaluator.isMember(null, "tenant1")).isFalse();
    }
  }
}
