package pf.hururaa.security;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import com.c4_soft.springaddons.security.oidc.OAuthentication;
import com.c4_soft.springaddons.security.oidc.OpenidClaimSet;
import com.c4_soft.springaddons.security.oidc.OpenidToken;
import lombok.Getter;
import pf.hururaa.commons.security.HururaaAuthentication;

/**
 * The resource server's {@link HururaaAuthentication}: an {@link OAuthentication} built from the
 * JWT access token, exposing the OpenID claims, the per-tenant permissions, and the Hurura'a roles
 * held in the DSI as authorities (see {@link HururaaAuthenticationConverter}).
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class HururaaJwtAuthentication extends OAuthentication<OpenidToken>
    implements HururaaAuthentication {
  private static final long serialVersionUID = 8007920992502248385L;

  @Getter
  private final Map<String, Set<String>> permissionsByTenant;

  /**
   * @param authorities the Hurura'a roles held in the DSI (see
   *        {@link HururaaAuthenticationConverter})
   */
  public HururaaJwtAuthentication(Jwt jwt, Map<String, Set<String>> permissionsByTenant,
      Collection<? extends GrantedAuthority> authorities) {
    super(new OpenidToken(new OpenidClaimSet(jwt.getClaims()), jwt.getTokenValue()), authorities);
    this.permissionsByTenant = permissionsByTenant;
  }
}
