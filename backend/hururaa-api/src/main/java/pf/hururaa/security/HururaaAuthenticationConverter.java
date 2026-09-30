package pf.hururaa.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.JwtAbstractAuthenticationTokenConverter;
import pf.hururaa.commons.security.TenantPermissionsExtractor;

/**
 * Picked by spring-addons' resource server auto-configuration to turn JWT access tokens into
 * {@link HururaaJwtAuthentication} instances.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
public class HururaaAuthenticationConverter implements JwtAbstractAuthenticationTokenConverter {

  private final TenantPermissionsExtractor permissionsExtractor;

  public HururaaAuthenticationConverter(TenantPermissionsExtractor permissionsExtractor) {
    this.permissionsExtractor = permissionsExtractor;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    return new HururaaJwtAuthentication(jwt, permissionsExtractor.extract(jwt.getClaims()));
  }
}
