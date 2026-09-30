package pf.hururaa.security;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import pf.hururaa.commons.security.TenantPermissionsExtractor;

/**
 * Picked by {@code oauth2Login} (Spring Security looks up an
 * {@code OAuth2UserService<OidcUserRequest, OidcUser>} bean) to turn the ID token and user-info
 * claims into a {@link HururaaOidcUser}.
 *
 * <p>
 * The public constructor is {@code @Autowired} explicitly because a second (package-private)
 * constructor exists for unit tests: with several constructors and no annotated one, Spring only
 * tries a no-args constructor.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
public class HururaaOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

  private final TenantPermissionsExtractor permissionsExtractor;

  @Autowired
  public HururaaOidcUserService(TenantPermissionsExtractor permissionsExtractor) {
    this(new OidcUserService(), permissionsExtractor);
  }

  HururaaOidcUserService(
      OAuth2UserService<OidcUserRequest, OidcUser> delegate,
      TenantPermissionsExtractor permissionsExtractor) {
    this.delegate = delegate;
    this.permissionsExtractor = permissionsExtractor;
  }

  @Override
  public @Nullable OidcUser loadUser(OidcUserRequest userRequest)
      throws OAuth2AuthenticationException {
    final var oidcUser = delegate.loadUser(userRequest);
    if (oidcUser == null) {
      return null;
    }
    return new HururaaOidcUser(
        oidcUser.getAuthorities(),
        oidcUser.getIdToken(),
        oidcUser.getUserInfo(),
        userRequest
            .getClientRegistration()
            .getProviderDetails()
            .getUserInfoEndpoint()
            .getUserNameAttributeName(),
        permissionsExtractor.extract(oidcUser.getClaims()));
  }
}
