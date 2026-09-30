package pf.hururaa.direction.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PagedModel;
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
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.events.DirectionEvents;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.uaa.UaaAuthorization;

@Tag(name = "Groups")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
@Slf4j
public class GroupController {
  public static final String DIRECTION_PLACEHOLDER = DirectionController.DIRECTION_PLACEHOLDER;
  public static final String GROUP_PLACEHOLDER = "group";
  public static final String APPLICATION_ID_PLACEHOLDER = "applicationId";
  public static final String ROLE_PLACEHOLDER = "role";
  public static final String USER_ID_PLACEHOLDER = "userId";
  public static final String BASE_PATH = DirectionController.DIRECTION_PATH + "/groups";
  public static final String GROUP_PATH = BASE_PATH + "/{" + GROUP_PLACEHOLDER + "}";
  public static final String ROLES_PATH = GROUP_PATH + "/roles";
  public static final String ROLE_PATH = ROLES_PATH + "/{" + APPLICATION_ID_PLACEHOLDER + "}/{"
      + ROLE_PLACEHOLDER + "}";
  public static final String MEMBERS_PATH = GROUP_PATH + "/members";
  public static final String MEMBER_PATH = MEMBERS_PATH + "/{" + USER_ID_PLACEHOLDER + "}";

  private final GroupService groupService;

  private final ApplicationRepository applicationRepository;

  private final KeycloakAdminApiProperties keycloakProperties;

  private final UaaAuthorization uaa;

  private final DirectoryMapper directoryMapper;

  private final ResourceEventPublisher resourceEvents;

  /**
   * Lists a direction's groups.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: platform administrator
   * ({@code hururaa.direction-admins.manage}), administrator of the direction, or manager of one of
   * its applications.
   * </p>
   *
   * @param direction the direction's alias
   * @return the direction's groups, by name
   */
  @GetMapping(path = BASE_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("@uaa.canReadDirection(authentication, #direction)")
  public List<GroupResponse> getGroups(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction)
      throws HururaaProblemException {
    return groupService.findAll(direction).stream().map(directoryMapper::toGroupResponse).toList();
  }

  /**
   * Creates a group in a direction. Idempotent: returns the location of the existing group when
   * one already has that name.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to manage at least one of the direction's applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param request the group to create
   * @return the location of the group
   */
  @PostMapping(path = BASE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @PreAuthorize("@uaa.isManagerInDirection(authentication, #direction)")
  public ResponseEntity<Void> createGroup(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @RequestBody @Valid GroupRequest request,
      Authentication authentication) throws HururaaProblemException {
    final var group = groupService.save(direction, request.name());
    log.info("{} created group {} in {}", authentication.getName(), group.name(), direction);
    publish(direction, group.name(), EventType.CREATE);
    final var location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path(GROUP_PATH)
        .buildAndExpand(direction, group.name())
        .toUri();
    return ResponseEntity.created(location).build();
  }

  /**
   * Deletes a group, which revokes the roles it granted from its members. Idempotent: does nothing
   * when no group has that name.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to manage at least one of the direction's applications, and every
   * application whose roles the group grants.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group's name
   */
  @DeleteMapping(path = GROUP_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@uaa.isManagerInDirection(authentication, #direction)")
  public void deleteGroup(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @PathVariable(name = GROUP_PLACEHOLDER) String group,
      Authentication authentication) throws HururaaProblemException {
    if (groupService.findByName(direction, group).isEmpty()) {
      return;
    }
    uaa.checkCanManageGroup(authentication, direction, group);
    groupService.delete(direction, group);
    log.info("{} deleted group {} of {}", authentication.getName(), group, direction);
    publish(direction, group, EventType.DELETE);
  }

  /**
   * Lists the application roles a group grants to its members (only roles of the direction's
   * applications are considered: no other can be granted through Hurura'a).
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: platform administrator
   * ({@code hururaa.direction-admins.manage}), administrator of the direction, or manager of one of
   * its applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group's name
   * @return the roles granted by the group, by application name then role name
   */
  @GetMapping(path = ROLES_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("@uaa.canReadDirection(authentication, #direction)")
  public List<GroupRoleResponse> getGroupRoles(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @PathVariable(name = GROUP_PLACEHOLDER) String group) throws HururaaProblemException {
    final var roles = new ArrayList<GroupRoleResponse>();
    for (final var application : applicationRepository.findByDirectionOrderByNameAsc(direction)) {
      final var clientId = keycloakProperties.apiClientId(application.getClientPrefix());
      for (final var role : groupService.findClientRoles(direction, group, clientId)) {
        roles.add(new GroupRoleResponse(Objects.requireNonNull(application.getId()),
            application.getName(), clientId, role));
      }
    }
    return roles;
  }

  /**
   * Makes a group grant a role of one of its direction's applications. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a manager of the application.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group's name
   * @param application the application resolved from the {@code applicationId} path variable,
   *        which must be managed by {@code direction}
   * @param role the name of one of the application's roles
   */
  @PutMapping(path = ROLE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@uaa.isApplicationManager(authentication, #application)")
  public void addGroupRole(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @PathVariable(name = GROUP_PLACEHOLDER) String group,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @PathVariable(name = ROLE_PLACEHOLDER) String role,
      Authentication authentication) throws HururaaProblemException {
    requireInDirection(application, direction);
    groupService.addClientRole(direction, group,
        keycloakProperties.apiClientId(application.getClientPrefix()), role);
    log.info("{} made group {} of {} grant role {} of {}", authentication.getName(), group,
        direction, role, application.getClientPrefix());
    publish(direction, group, EventType.UPDATE);
  }

  /**
   * Stops a group from granting a role of one of its direction's applications. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a manager of the application.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group's name
   * @param application the application resolved from the {@code applicationId} path variable,
   *        which must be managed by {@code direction}
   * @param role the name of one of the application's roles
   */
  @DeleteMapping(path = ROLE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@uaa.isApplicationManager(authentication, #application)")
  public void removeGroupRole(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @PathVariable(name = GROUP_PLACEHOLDER) String group,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @PathVariable(name = ROLE_PLACEHOLDER) String role,
      Authentication authentication) throws HururaaProblemException {
    requireInDirection(application, direction);
    groupService.removeClientRole(direction, group,
        keycloakProperties.apiClientId(application.getClientPrefix()), role);
    log.info("{} made group {} of {} stop granting role {} of {}", authentication.getName(),
        group, direction, role, application.getClientPrefix());
    publish(direction, group, EventType.UPDATE);
  }

  /**
   * Lists a group's members.
   *
   * <p>
   * Results follow Keycloak's own ordering and are not client-controllable: the Keycloak Admin
   * REST API exposes no sort parameter for group members.
   * </p>
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: platform administrator
   * ({@code hururaa.direction-admins.manage}), administrator of the direction, or manager of one of
   * its applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group's name
   * @param pageParams the requested page index and size
   * @return a page of the group's members
   */
  @GetMapping(path = MEMBERS_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("@uaa.canReadDirection(authentication, #direction)")
  public PagedModel<UserResponse> getGroupMembers(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @PathVariable(name = GROUP_PLACEHOLDER) String group,
      @ParameterObject @Valid PageParams pageParams) throws HururaaProblemException {
    return new PagedModel<>(groupService
        .findMembers(direction, group, pageParams.toPageable())
        .map(directoryMapper::toUserResponse));
  }

  /**
   * Adds a member of the direction to one of its groups. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to manage at least one of the direction's applications, and every
   * application whose roles the group grants.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group's name
   * @param userId the added user's id; must be a member of the direction
   */
  @PutMapping(path = MEMBER_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@uaa.isManagerInDirection(authentication, #direction)")
  public void addGroupMember(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @PathVariable(name = GROUP_PLACEHOLDER) String group,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    uaa.checkCanManageGroup(authentication, direction, group);
    groupService.addMember(direction, group, userId);
    log.info("{} added {} to group {} of {}", authentication.getName(), userId, group, direction);
    publish(direction, group, EventType.UPDATE);
  }

  /**
   * Removes a member from a group. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to manage at least one of the direction's applications, and every
   * application whose roles the group grants.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group's name
   * @param userId the removed user's id
   */
  @DeleteMapping(path = MEMBER_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@uaa.isManagerInDirection(authentication, #direction)")
  public void removeGroupMember(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @PathVariable(name = GROUP_PLACEHOLDER) String group,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    uaa.checkCanManageGroup(authentication, direction, group);
    groupService.removeMember(direction, group, userId);
    log.info("{} removed {} from group {} of {}", authentication.getName(), userId, group,
        direction);
    publish(direction, group, EventType.UPDATE);
  }

  /**
   * A group only grants roles of the applications managed by its own direction: Keycloak does not
   * scope clients to organizations, this is where the rule is enforced.
   */
  private static void requireInDirection(Application application, String direction)
      throws HururaaProblemException {
    if (!application.getDirection().equals(direction)) {
      throw new HururaaProblemException(
          ProblemType.APPLICATION_NOT_IN_DIRECTION,
          "Application %s is not managed by %s".formatted(application.getClientPrefix(),
              direction),
          Map.of("applicationId", Objects.requireNonNull(application.getId()), "direction",
              direction));
    }
  }

  private void publish(String direction, String group, EventType eventType) {
    resourceEvents.publish(DirectionEvents.of(direction, DirectionEvents.GROUP, group, eventType));
  }
}
