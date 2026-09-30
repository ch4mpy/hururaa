package pf.hururaa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pf.hururaa.security.HururaaOidcUserFixture.ROLES_NAMESPACE;
import static pf.hururaa.security.HururaaOidcUserFixture.defaultOidcUser;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import pf.hururaa.commons.security.TenantPermissionsExtractor;

class HururaaOidcUserServiceTest {

  @SuppressWarnings("unchecked")
  private final OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock(OAuth2UserService.class);

  private final HururaaOidcUserService service =
      new HururaaOidcUserService(delegate, new TenantPermissionsExtractor(ROLES_NAMESPACE));

  private static OidcUserRequest userRequest(String userNameAttributeName) {
    final var registration =
        ClientRegistration
            .withRegistrationId("hururaa-bff")
            .clientId("hururaa-bff")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost/login/oauth2/code/hururaa-bff")
            .authorizationUri("https://localhost/auth/authorize")
            .tokenUri("https://localhost/auth/token")
            .userNameAttributeName(userNameAttributeName)
            .build();
    final var request = mock(OidcUserRequest.class);
    when(request.getClientRegistration()).thenReturn(registration);
    return request;
  }

  @Test
  void wrapsTheDelegateUserIntoAHururaaOidcUserWithPermissionsFromTheExtractor() {
    final var oidcUser = defaultOidcUser();
    when(delegate.loadUser(any())).thenReturn(oidcUser);

    final var user = service.loadUser(userRequest("preferred_username"));

    assertThat(user).isInstanceOf(HururaaOidcUser.class);
    final var hururaaUser = (HururaaOidcUser) user;
    assertThat(hururaaUser.getPermissionsByTenant()).containsOnlyKeys("tenant1", "tenant3");
    assertThat(hururaaUser.getPermissionsByTenant().get("tenant3")).contains("hururaa.users.read");
    assertThat(hururaaUser.getName()).isEqualTo("manager3");
    assertThat(hururaaUser.getAuthorities()).isEqualTo(oidcUser.getAuthorities());
    assertThat(hururaaUser.getIdToken()).isSameAs(oidcUser.getIdToken());
    assertThat(hururaaUser.getUserInfo()).isSameAs(oidcUser.getUserInfo());
  }

  @Test
  void defaultsTheNameToTheSubjectWhenNoUserNameAttributeIsConfigured() {
    when(delegate.loadUser(any())).thenReturn(defaultOidcUser());

    final var user = service.loadUser(userRequest(""));

    assertThat(user.getName()).isEqualTo("b3029704-4137-4d49-93f5-7f1aef4279cd");
  }

  @Test
  void yieldsEmptyPermissionsWhenTheRolesNamespaceIsAnotherClient() {
    when(delegate.loadUser(any())).thenReturn(defaultOidcUser());
    final var otherService =
        new HururaaOidcUserService(delegate, new TenantPermissionsExtractor("other-client"));

    final var user = (HururaaOidcUser) otherService.loadUser(userRequest("preferred_username"));

    assertThat(user.getPermissionsByTenant().get("tenant3")).isEmpty();
  }
}
