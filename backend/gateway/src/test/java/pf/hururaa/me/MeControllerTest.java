package pf.hururaa.me;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import pf.hururaa.security.HururaaOidcUser;
import pf.hururaa.security.HururaaOidcUserFixture;

class MeControllerTest {

  private final MeController controller = new MeController();

  private static OAuth2AuthenticationToken authentication(HururaaOidcUser user) {
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), "hururaa-bff");
  }

  @Test
  void givenUserIsAnonymous_whenGetMe_thenAnonymous() {
    final var anonymous = new AnonymousAuthenticationToken(
        "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    assertThat(controller.getMe(anonymous)).isEqualTo(UserResponse.ANONYMOUS);
  }

  @Test
  void givenUserIsAuthenticated_whenGetMe_thenUserInfoAndDirections() {
    final var me = controller.getMe(authentication(HururaaOidcUserFixture.hururaaOidcUser()));

    assertThat(me.sub()).isEqualTo("manager3");
    assertThat(me.email()).isEqualTo("thortellini@tenant3.pf");
    assertThat(me.username()).isEqualTo("manager3");
    assertThat(me.firstName()).isEqualTo("Thor");
    assertThat(me.lastName()).isEqualTo("Tellini");
    assertThat(me.directions()).containsExactly("tenant1", "tenant3");
  }

  @Test
  void givenMembershipGrantingNoRole_whenGetMe_thenTheDirectionIsListedAnyway() {
    // a direction administrator or an application manager usually holds no token role: their
    // delegations are stored by hururaa-api
    final var me = controller.getMe(authentication(HururaaOidcUserFixture
        .hururaaOidcUser(Map.of("dpam", Set.of(), "dsi", Set.of("hururaa.admin")))));

    assertThat(me.directions()).containsExactly("dpam", "dsi");
  }
}
