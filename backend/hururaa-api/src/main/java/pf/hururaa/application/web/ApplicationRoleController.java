package pf.hururaa.application.web;

import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
import pf.hururaa.application.domain.Application;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.keycloak.ClientRoleService;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.problem.HururaaProblemException;

@Tag(name = "Application Roles")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
@Slf4j
public class ApplicationRoleController {
  public static final String APPLICATION_ID_PLACEHOLDER =
      ApplicationController.APPLICATION_ID_PLACEHOLDER;
  public static final String ROLE_PLACEHOLDER = "role";
  public static final String BASE_PATH = ApplicationController.APPLICATION_PATH + "/roles";
  public static final String ROLE_PATH = BASE_PATH + "/{" + ROLE_PLACEHOLDER + "}";

  private final ClientRoleService clientRoleService;

  private final KeycloakAdminApiProperties keycloakProperties;

  private final ApplicationMapper applicationMapper;

  private final ResourceEventPublisher resourceEvents;

  /**
   * Lists an application's roles (the client roles of its {@code <prefix>-api} Keycloak client).
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the application: platform administrator
   * ({@code hururaa.applications.manage}), administrator of its direction, or manager of the
   * application.
   * </p>
   *
   * @param application the application resolved from the {@code applicationId} path variable
   * @return the application's roles, by name
   */
  @GetMapping(path = BASE_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("@uaa.canReadApplication(authentication, #application)")
  public List<ApplicationRoleResponse> getApplicationRoles(
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application)
      throws HururaaProblemException {
    return clientRoleService
        .findAll(keycloakProperties.apiClientId(application.getClientPrefix()))
        .stream()
        .map(applicationMapper::toApplicationRoleResponse)
        .toList();
  }

  /**
   * Defines a role for an application. Idempotent: returns the location of the existing role when
   * the application already has one with that name.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a manager of the application.
   * </p>
   *
   * @param application the application resolved from the {@code applicationId} path variable
   * @param request the role to define
   * @return the location of the role
   */
  @PostMapping(path = BASE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @PreAuthorize("@uaa.isApplicationManager(authentication, #application)")
  public ResponseEntity<Void> createApplicationRole(
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @RequestBody @Valid ApplicationRoleRequest request,
      Authentication authentication) throws HururaaProblemException {
    clientRoleService.save(keycloakProperties.apiClientId(application.getClientPrefix()),
        request.name(), request.description());
    log.info("{} created role {} of application {}", authentication.getName(), request.name(),
        application.getClientPrefix());
    resourceEvents.publish(ApplicationController.eventFor(application, application.getDirection(),
        EventType.UPDATE));
    final var location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path(ROLE_PATH)
        .buildAndExpand(application.getId(), request.name())
        .toUri();
    return ResponseEntity.created(location).build();
  }

  /**
   * Deletes a role of an application, which revokes it from every group granting it. Idempotent:
   * does nothing when the application has no such role.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a manager of the application.
   * </p>
   *
   * @param application the application resolved from the {@code applicationId} path variable
   * @param role the role's name
   */
  @DeleteMapping(path = ROLE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@uaa.isApplicationManager(authentication, #application)")
  public void deleteApplicationRole(
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @PathVariable(name = ROLE_PLACEHOLDER) String role,
      Authentication authentication) throws HururaaProblemException {
    clientRoleService.delete(keycloakProperties.apiClientId(application.getClientPrefix()), role);
    log.info("{} deleted role {} of application {}", authentication.getName(), role,
        application.getClientPrefix());
    resourceEvents.publish(ApplicationController.eventFor(application, application.getDirection(),
        EventType.UPDATE));
  }
}
