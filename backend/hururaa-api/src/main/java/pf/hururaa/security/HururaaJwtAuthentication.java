package pf.hururaa.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import com.c4_soft.springaddons.security.oidc.OAuthentication;
import com.c4_soft.springaddons.security.oidc.OpenidClaimSet;
import com.c4_soft.springaddons.security.oidc.OpenidToken;
import lombok.Getter;
import pf.hururaa.commons.security.HururaaAuthentication;

/**
 * The resource server's {@link HururaaAuthentication}: an {@link OAuthentication} built from the
 * JWT access token, exposing the OpenID claims and the per-tenant permissions (see
 * {@link HururaaAuthenticationConverter}).
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class HururaaJwtAuthentication extends OAuthentication<OpenidToken>
    implements HururaaAuthentication {
  private static final long serialVersionUID = 8007920992502248385L;

  @Getter
  private final Map<String, Set<String>> permissionsByTenant;

  public HururaaJwtAuthentication(Jwt jwt, Map<String, Set<String>> permissionsByTenant) {
    super(new OpenidToken(new OpenidClaimSet(jwt.getClaims()), jwt.getTokenValue()), List.of());
    this.permissionsByTenant = permissionsByTenant;
  }
}
