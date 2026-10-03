package pf.hururaa.uaa.web;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.application.web.ApplicationMapper;
import pf.hururaa.uaa.HururaaPermission;
import pf.hururaa.uaa.UaaProperties;

@Tag(name = "Delegations")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
public class DelegationController {
  public static final String BASE_PATH = "/me/delegations";

  private final UaaProperties uaaProperties;

  private final ApplicationRepository applicationRepository;

  private final ApplicationMapper applicationMapper;

  /**
   * What the current user may do in Hurura'a, level by level of the delegation chain, as read from
   * their token (Hurura'a roles held in each direction): this is what the frontend adapts its menus
   * and actions to. A delegation granted or revoked since the token was issued shows once it is
   * renewed.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be authenticated.
   * </p>
   *
   * @return the current user's delegations
   */
  @GetMapping(path = BASE_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public DelegationsResponse getMyDelegations(Authentication authentication) {
    return new DelegationsResponse(
        authentication
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .filter(HururaaPermission.ALL::contains)
            .sorted()
            .toList(),
        uaaProperties.getPlatformOrganization(),
        HururaaPermission.administeredDirections(authentication).stream().sorted().toList(),
        applicationRepository
            .findAllByOrderByNameAsc()
            .stream()
            .filter(application -> application.isManagedBy(authentication))
            .map(applicationMapper::toApplicationResponse)
            .toList());
  }
}
