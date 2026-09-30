package pf.hururaa.security;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson mixin for {@link HururaaOidcUser}, modeled after Spring Security's
 * {@code DefaultOidcUserMixin} (which does not apply to subclasses' constructors).
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 * @see HururaaSecurityJacksonModule
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties({"attributes"})
abstract class HururaaOidcUserMixin {

  @JsonCreator
  HururaaOidcUserMixin(
      @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities,
      @JsonProperty("idToken") OidcIdToken idToken,
      @JsonProperty("userInfo") OidcUserInfo userInfo,
      @JsonProperty("nameAttributeKey") String nameAttributeKey,
      @JsonProperty("permissionsByTenant") Map<String, Set<String>> permissionsByTenant) {}
}
