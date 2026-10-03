package pf.hururaa.application.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.application.domain.Application;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.direction.domain.DelegatedDirection;
import pf.hururaa.direction.web.DirectionController;
import pf.hururaa.direction.web.DirectoryMapper;
import pf.hururaa.direction.web.UserResponse;
import pf.hururaa.journal.PermissionJournal;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.uaa.DelegationService;

/**
 * The managers of an application: the members of its {@code hururaa.<prefix>.product-owners} group,
 * who hold {@code hururaa.application.<prefix>.manage} in the application's direction. A
 * designation or a revocation takes effect when the delegate's token is renewed.
 */
@Tag(name = "Application Managers")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
@Slf4j
public class ApplicationManagerController {
  public static final String DIRECTION_PLACEHOLDER = DirectionController.DIRECTION_PLACEHOLDER;
  public static final String APPLICATION_ID_PLACEHOLDER =
      ApplicationController.APPLICATION_ID_PLACEHOLDER;
  public static final String USER_ID_PLACEHOLDER = "userId";
  public static final String BASE_PATH =
      ApplicationController.DIRECTION_APPLICATION_PATH + "/managers";
  public static final String MANAGER_PATH = BASE_PATH + "/{" + USER_ID_PLACEHOLDER + "}";

  private final DelegationService delegationService;

  private final DirectoryMapper directoryMapper;

  private final ResourceEventPublisher resourceEvents;

  private final PermissionJournal permissionJournal;

  /**
   * Lists the managers of an application: the members of its direction allowed to define its
   * roles, aggregate them into groups and assign users to those groups.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the application: Hurura'a administrator, administrator of
   * its direction, or manager of the application. The application must be managed by
   * {@code direction}.
   * </p>
   *
   * @param direction the direction managing the application
   * @param application the application resolved from the {@code applicationId} path variable
   * @return the application's managers, by username
   */
  @GetMapping(path = BASE_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("(#direction.isAdministeredBy(authentication)"
      + " or #application.isManagedBy(authentication))"
      + " and #application.direction == #direction.alias")
  public List<UserResponse> getApplicationManagers(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application)
      throws HururaaProblemException {
    return delegationService
        .findManagers(application)
        .stream()
        .map(directoryMapper::toUserResponse)
        .toList();
  }

  /**
   * Designates a member of the application's direction as manager of the application. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the application: Hurura'a administrator, administrator of
   * its direction, or manager of the application. The application must be managed by
   * {@code direction}.
   * </p>
   *
   * @param direction the direction managing the application
   * @param application the application resolved from the {@code applicationId} path variable
   * @param userId the designated user's id; must be a member of the application's direction
   */
  @PutMapping(path = MANAGER_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("(#direction.isAdministeredBy(authentication)"
      + " or #application.isManagedBy(authentication))"
      + " and #application.direction == #direction.alias")
  public void addApplicationManager(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    if (delegationService.addManager(application, userId)) {
      permissionJournal.applicationManagerGranted(application, userId);
      log.info("{} designated {} as manager of {}", authentication.getName(), userId,
          application.getClientPrefix());
      resourceEvents.publish(ApplicationController.eventFor(application,
          application.getDirection(), EventType.UPDATE));
    }
  }

  /**
   * Revokes a manager of an application. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the application: Hurura'a administrator, administrator of
   * its direction, or manager of the application. The application must be managed by
   * {@code direction}.
   * </p>
   *
   * @param direction the direction managing the application
   * @param application the application resolved from the {@code applicationId} path variable
   * @param userId the revoked manager's id
   */
  @DeleteMapping(path = MANAGER_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("(#direction.isAdministeredBy(authentication)"
      + " or #application.isManagedBy(authentication))"
      + " and #application.direction == #direction.alias")
  public void removeApplicationManager(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    if (delegationService.removeManager(application, userId)) {
      permissionJournal.applicationManagerRevoked(application, userId);
      log.info("{} revoked {} as manager of {}", authentication.getName(), userId,
          application.getClientPrefix());
      resourceEvents.publish(ApplicationController.eventFor(application,
          application.getDirection(), EventType.UPDATE));
    }
  }
}
