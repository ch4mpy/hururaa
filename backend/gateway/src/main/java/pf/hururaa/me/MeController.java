package pf.hururaa.me;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.security.HururaaOidcUser;

@Tag(name = "Gateway")
@RestController
@RequestMapping(produces = { MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE })
@RequiredArgsConstructor
@Observed
@Slf4j
public class MeController {
  public static final String BASE_PATH = "/me";

  /**
   * Returns information of the current user if authenticated, ANONYMOUS otherwise, with the
   * directions the user is a member of (what the frontend opens event streams for). What the user
   * may do in Hurura'a is not a matter of token roles only (most of it is delegations stored by
   * {@code hururaa-api}): the frontend gets it from the API's {@code /me/delegations}.
   *
   * <h4>Access control</h4>
   * <p>
   * None: an anonymous caller gets {@link UserResponse#ANONYMOUS} rather than a {@code 401}.
   * </p>
   *
   * @return the current user
   */
  @GetMapping(path = BASE_PATH)
  public UserResponse getMe(Authentication auth) {
    if (auth instanceof OAuth2AuthenticationToken oauth
        && oauth.getPrincipal() instanceof HururaaOidcUser oidcUser) {
      return new UserResponse(
          oauth.getName(),
          oidcUser.getAttributes().getOrDefault(StandardClaimNames.EMAIL, "").toString(),
          oidcUser
              .getAttributes()
              .getOrDefault(StandardClaimNames.PREFERRED_USERNAME, "")
              .toString(),
          oidcUser.getAttributes().getOrDefault(StandardClaimNames.GIVEN_NAME, "").toString(),
          oidcUser.getAttributes().getOrDefault(StandardClaimNames.FAMILY_NAME, "").toString(),
          oidcUser.getPermissionsByTenant().keySet().stream().sorted().toList());
    }
    return UserResponse.ANONYMOUS;
  }
}
