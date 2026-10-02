package pf.hururaa.application.web;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.application.ApplicationService;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEvent;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.events.DirectionEvents;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.direction.domain.DelegatedDirection;
import pf.hururaa.direction.domain.DirectionAdmin;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.direction.web.DirectionController;
import pf.hururaa.uaa.HururaaPermission;

@Tag(name = "Applications")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
@Slf4j
public class ApplicationController {
  public static final String APPLICATION_ID_PLACEHOLDER = "applicationId";
  public static final String DIRECTION_PARAM = "direction";
  public static final String MANAGEABLE_PARAM = "manageable";
  public static final String BASE_PATH = "/applications";
  public static final String APPLICATION_PATH =
      BASE_PATH + "/{" + APPLICATION_ID_PLACEHOLDER + "}";
  /**
   * Where an application is changed, and its roles and managers addressed: under its direction, so
   * that the access rules can read the direction's administrators.
   */
  public static final String DIRECTION_PLACEHOLDER = DirectionController.DIRECTION_PLACEHOLDER;
  public static final String DIRECTION_APPLICATIONS_PATH =
      DirectionController.DIRECTION_PATH + "/applications";
  public static final String DIRECTION_APPLICATION_PATH =
      DIRECTION_APPLICATIONS_PATH + "/{" + APPLICATION_ID_PLACEHOLDER + "}";

  private final ApplicationRepository applicationRepository;

  private final ApplicationService applicationService;

  private final ApplicationMapper applicationMapper;

  private final ResourceEventPublisher resourceEvents;

  private final DirectionAdminRepository directionAdminRepository;

  /**
   * Lists the registered applications, by name: which direction manages each of them is no secret.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be authenticated.
   * </p>
   *
   * @param direction when set, only the applications managed by that direction are returned
   * @param manageable when {@code true}, only the applications the user has management rights on
   *        are returned: all of them for a Hurura'a administrator ({@code hururaa.admin}), those of
   *        the directions they administer, and those they manage
   * @return the applications
   */
  @GetMapping(path = BASE_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public List<ApplicationResponse> getApplications(
      @RequestParam(name = DIRECTION_PARAM, required = false) @Nullable String direction,
      @RequestParam(name = MANAGEABLE_PARAM, required = false, defaultValue = "false")
      boolean manageable,
      Authentication authentication) {
    final var applications = direction == null || direction.isBlank()
        ? applicationRepository.findAllByOrderByNameAsc()
        : applicationRepository.findByDirectionOrderByNameAsc(direction);
    if (!manageable || hasAuthority(authentication, HururaaPermission.Names.ADMIN)) {
      return applications.stream().map(applicationMapper::toApplicationResponse).toList();
    }
    final var administeredDirections = directionAdminRepository
        .findByUserIdOrderByDirection(authentication.getName())
        .stream()
        .map(DirectionAdmin::getDirection)
        .collect(Collectors.toSet());
    return applications
        .stream()
        .filter(application -> administeredDirections.contains(application.getDirection())
            || application.isManagedBy(authentication.getName()))
        .map(applicationMapper::toApplicationResponse)
        .toList();
  }

  /**
   * Registers an application in the direction managing it, creating its Keycloak clients
   * ({@code <prefix>-bff} and {@code <prefix>-api}) when they don't exist yet.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), or an administrator
   * of the direction.
   * </p>
   *
   * @param direction the direction managing the application
   * @param request the application to register
   * @return the location of the registered application
   */
  @PostMapping(path = DIRECTION_APPLICATIONS_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.isAdministeredBy(authentication.name)")
  public ResponseEntity<Void> createApplication(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @RequestBody @Valid ApplicationCreationRequest request,
      Authentication authentication) throws HururaaProblemException {
    final var application = applicationService
        .create(applicationMapper.toApplication(request, direction.alias()));
    log.info("{} registered application {} for direction {}", authentication.getName(),
        application.getClientPrefix(), application.getDirection());
    resourceEvents.publish(eventFor(application, application.getDirection(), EventType.CREATE));
    return ResponseEntity.created(locationOf(application)).build();
  }

  /**
   * Retrieves a single application.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be authenticated.
   * </p>
   *
   * @param application the application resolved from the {@code applicationId} path variable
   * @return the requested application
   */
  @GetMapping(path = APPLICATION_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public ApplicationResponse getApplication(
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application) {
    return applicationMapper.toApplicationResponse(application);
  }

  /**
   * Renames an application and sets the direction managing it. Moving it to another direction drops
   * its managers, and is refused while groups of its current direction grant its roles.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), or an administrator
   * of {@code direction} keeping the application in it (only Hurura'a administrators move
   * applications). The application must be managed by {@code direction}.
   * </p>
   *
   * @param direction the direction managing the application
   * @param application the application resolved from the {@code applicationId} path variable
   * @param request the application's new name and direction
   */
  @PutMapping(path = DIRECTION_APPLICATION_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("(hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or (#direction.isAdministeredBy(authentication.name)"
      + " and #request.direction == #direction.alias))"
      + " and #application.direction == #direction.alias")
  public void updateApplication(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @RequestBody @Valid ApplicationUpdateRequest request,
      Authentication authentication) throws HururaaProblemException {
    final var formerDirection = application.getDirection();
    final var updated = applicationService.update(application, request.name(), request.direction());
    log.info("{} updated application {} (direction {})", authentication.getName(),
        updated.getClientPrefix(), updated.getDirection());
    resourceEvents.publish(eventFor(updated, formerDirection, EventType.UPDATE));
    if (!formerDirection.equals(updated.getDirection())) {
      resourceEvents.publish(eventFor(updated, updated.getDirection(), EventType.UPDATE));
    }
  }

  /**
   * Unregisters an application (its Keycloak clients are left untouched). Refused while groups of
   * its direction grant its roles.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), or an administrator
   * of {@code direction}. The application must be managed by {@code direction}.
   * </p>
   *
   * @param direction the direction managing the application
   * @param application the application resolved from the {@code applicationId} path variable
   */
  @DeleteMapping(path = DIRECTION_APPLICATION_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("(hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.isAdministeredBy(authentication.name))"
      + " and #application.direction == #direction.alias")
  public void deleteApplication(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      Authentication authentication) throws HururaaProblemException {
    applicationService.delete(application);
    log.info("{} unregistered application {}", authentication.getName(),
        application.getClientPrefix());
    resourceEvents.publish(eventFor(application, application.getDirection(), EventType.DELETE));
  }

  static URI locationOf(Application application) {
    return ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path(APPLICATION_PATH)
        .buildAndExpand(application.getId())
        .toUri();
  }

  static ResourceEvent eventFor(
      Application application,
      String direction,
      EventType eventType) {
    return DirectionEvents
        .of(direction, DirectionEvents.APPLICATION, String.valueOf(application.getId()), eventType);
  }

  private static boolean hasAuthority(Authentication authentication, String authority) {
    return authentication
        .getAuthorities()
        .stream()
        .anyMatch(granted -> authority.equals(granted.getAuthority()));
  }
}
