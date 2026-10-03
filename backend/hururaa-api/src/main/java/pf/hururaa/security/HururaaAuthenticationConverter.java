package pf.hururaa.security;

import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.JwtAbstractAuthenticationTokenConverter;
import pf.hururaa.commons.security.TenantPermissionsExtractor;
import pf.hururaa.uaa.HururaaPermission;
import pf.hururaa.uaa.UaaProperties;

/**
 * Picked by spring-addons' resource server auto-configuration to turn JWT access tokens into
 * {@link HururaaJwtAuthentication} instances.
 *
 * <p>
 * The static {@link HururaaPermission Hurura'a roles} held in the
 * {@link UaaProperties#getPlatformOrganization() DSI} become the authentication's authorities:
 * {@code hasAuthority('hururaa.direction.admin')} is a Hurura'a administrator. The roles held in
 * the other directions are read by the access rules from the per-direction permissions.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
public class HururaaAuthenticationConverter implements JwtAbstractAuthenticationTokenConverter {

  private final TenantPermissionsExtractor permissionsExtractor;

  private final UaaProperties uaaProperties;

  public HururaaAuthenticationConverter(TenantPermissionsExtractor permissionsExtractor,
      UaaProperties uaaProperties) {
    this.permissionsExtractor = permissionsExtractor;
    this.uaaProperties = uaaProperties;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    final var permissionsByTenant = permissionsExtractor.extract(jwt.getClaims());
    final var hururaaAuthorities = permissionsByTenant
        .getOrDefault(uaaProperties.getPlatformOrganization(), Set.of())
        .stream()
        .filter(HururaaPermission.ALL::contains)
        .sorted()
        .map(SimpleGrantedAuthority::new)
        .toList();
    return new HururaaJwtAuthentication(jwt, permissionsByTenant, hururaaAuthorities);
  }
}
