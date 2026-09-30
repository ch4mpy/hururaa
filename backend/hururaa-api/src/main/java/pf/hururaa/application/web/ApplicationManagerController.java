package pf.hururaa.application.web;

import java.util.ArrayList;
import java.util.Comparator;
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
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.direction.web.DirectoryMapper;
import pf.hururaa.direction.web.UserResponse;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.problem.HururaaProblemException;

@Tag(name = "Application Managers")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
@Slf4j
public class ApplicationManagerController {
  public static final String APPLICATION_ID_PLACEHOLDER =
      ApplicationController.APPLICATION_ID_PLACEHOLDER;
  public static final String USER_ID_PLACEHOLDER = "userId";
  public static final String BASE_PATH = ApplicationController.APPLICATION_PATH + "/managers";
  public static final String MANAGER_PATH = BASE_PATH + "/{" + USER_ID_PLACEHOLDER + "}";

  private final ApplicationRepository applicationRepository;

  private final DirectionService directionService;

  private final DirectoryMapper directoryMapper;

  private final ResourceEventPublisher resourceEvents;

  /**
   * Lists the managers of an application: the members of its direction allowed to define its
   * roles, aggregate them into groups and assign users to those groups.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the application: platform administrator
   * ({@code hururaa.applications.manage}), administrator of its direction, or manager of the
   * application.
   * </p>
   *
   * @param application the application resolved from the {@code applicationId} path variable
   * @return the application's managers, by username (a manager who has left the direction is
   *         listed with their id as username)
   */
  @GetMapping(path = BASE_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("@uaa.canReadApplication(authentication, #application)")
  public List<UserResponse> getApplicationManagers(
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application)
      throws HururaaProblemException {
    final var managers = new ArrayList<UserResponse>();
    for (final var userId : application.getManagers()) {
      final var member = directionService.findMember(application.getDirection(), userId);
      managers.add(member.isPresent() ? directoryMapper.toUserResponse(member.get())
          : new UserResponse(userId, userId, null, null, null));
    }
    managers.sort(Comparator.comparing(UserResponse::username));
    return managers;
  }

  /**
   * Designates a member of the application's direction as manager of the application. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be an administrator of the application's direction.
   * </p>
   *
   * @param application the application resolved from the {@code applicationId} path variable
   * @param userId the designated user's id; must be a member of the application's direction
   */
  @PutMapping(path = MANAGER_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@uaa.isDirectionAdmin(authentication, #application.direction)")
  public void addApplicationManager(
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    directionService.requireMember(application.getDirection(), userId);
    if (application.getManagers().add(userId)) {
      applicationRepository.save(application);
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
   * Requires the user to be an administrator of the application's direction.
   * </p>
   *
   * @param application the application resolved from the {@code applicationId} path variable
   * @param userId the revoked manager's id
   */
  @DeleteMapping(path = MANAGER_PATH)
  @Transactional
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@uaa.isDirectionAdmin(authentication, #application.direction)")
  public void removeApplicationManager(
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) {
    if (application.getManagers().remove(userId)) {
      applicationRepository.save(application);
      log.info("{} revoked {} as manager of {}", authentication.getName(), userId,
          application.getClientPrefix());
      resourceEvents.publish(ApplicationController.eventFor(application,
          application.getDirection(), EventType.UPDATE));
    }
  }
}
