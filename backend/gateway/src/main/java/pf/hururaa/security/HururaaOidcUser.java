package pf.hururaa.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.util.StringUtils;
import lombok.Getter;
import pf.hururaa.commons.security.HururaaAuthentication;

/**
 * The BFF's {@link HururaaAuthentication}: the {@link DefaultOidcUser} principal of the
 * {@code oauth2Login} session, extended with the per-tenant permissions (see
 * {@link HururaaOidcUserService}).
 * <p>
 * Session serialization: Java serialization works out of the box; for JSON, use the modules
 * returned by {@link HururaaSecurityJacksonModule#getModules(ClassLoader)}.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class HururaaOidcUser extends DefaultOidcUser implements HururaaAuthentication {
  private static final long serialVersionUID = -4054101216657565027L;

  @Getter
  private final Map<String, Set<String>> permissionsByTenant;

  /**
   * @param authorities the granted authorities
   * @param idToken the ID token
   * @param userInfo the user-info response (if fetched)
   * @param nameAttributeKey the claim to use as {@link #getName()} (defaults to {@code sub})
   * @param permissionsByTenant the per-tenant permissions
   */
  public HururaaOidcUser(
      Collection<? extends GrantedAuthority> authorities,
      OidcIdToken idToken,
      @Nullable OidcUserInfo userInfo,
      @Nullable String nameAttributeKey,
      Map<String, Set<String>> permissionsByTenant) {
    super(
        authorities,
        idToken,
        userInfo,
        StringUtils.hasText(nameAttributeKey) ? nameAttributeKey : IdTokenClaimNames.SUB);
    this.permissionsByTenant = permissionsByTenant;
  }
}
