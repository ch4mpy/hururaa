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
import pf.hururaa.direction.domain.DelegatedDirection;
import pf.hururaa.direction.domain.DelegatedGroup;
import pf.hururaa.events.DirectionEvents;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.uaa.HururaaPermission;

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

  private final DirectoryMapper directoryMapper;

  private final ResourceEventPublisher resourceEvents;

  /**
   * Lists a direction's groups.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator
   * ({@code hururaa.admin}), administrator of the direction, or manager of one of its
   * applications.
   * </p>
   *
   * @param direction the direction's alias
   * @return the direction's groups, by name
   */
  @GetMapping(path = BASE_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.hasDelegate(authentication.name)")
  public List<GroupResponse> getGroups(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction)
      throws HururaaProblemException {
    return groupService
        .findAll(direction.alias())
        .stream()
        .map(directoryMapper::toGroupResponse)
        .toList();
  }

  /**
   * Creates a group in a direction. Idempotent: returns the location of the existing group when
   * one already has that name.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or to manage at least one of its applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param request the group to create
   * @return the location of the group
   */
  @PostMapping(path = BASE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.isGroupCreatableBy(authentication.name)")
  public ResponseEntity<Void> createGroup(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @RequestBody @Valid GroupRequest request,
      Authentication authentication) throws HururaaProblemException {
    final var group = groupService.save(direction.alias(), request.name());
    log.info("{} created group {} in {}", authentication.getName(), group.name(),
        direction.alias());
    publish(direction.alias(), group.name(), EventType.CREATE);
    final var location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path(GROUP_PATH)
        .buildAndExpand(direction.alias(), group.name())
        .toUri();
    return ResponseEntity.created(location).build();
  }

  /**
   * Deletes a group, which revokes the roles it granted from its members.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or to manage at least one of its applications and every application whose
   * roles the group grants.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group resolved from its name
   */
  @DeleteMapping(path = GROUP_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.isManageableBy(authentication.name)")
  public void deleteGroup(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group,
      Authentication authentication) throws HururaaProblemException {
    groupService.delete(direction, group.name());
    log.info("{} deleted group {} of {}", authentication.getName(), group.name(), direction);
    publish(direction, group.name(), EventType.DELETE);
  }

  /**
   * Lists the application roles a group grants to its members (only roles of the direction's
   * applications are considered: no other can be granted through Hurura'a).
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator
   * ({@code hururaa.admin}), administrator of the direction, or manager of one of its
   * applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group resolved from its name
   * @return the roles granted by the group, by application name then role name
   */
  @GetMapping(path = ROLES_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.direction.hasDelegate(authentication.name)")
  public List<GroupRoleResponse> getGroupRoles(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group)
      throws HururaaProblemException {
    final var roles = new ArrayList<GroupRoleResponse>();
    for (final var application : applicationRepository.findByDirectionOrderByNameAsc(direction)) {
      final var clientId = keycloakProperties.apiClientId(application.getClientPrefix());
      for (final var role : groupService.findClientRoles(direction, group.name(), clientId)) {
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
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or a manager of the application.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group resolved from its name
   * @param application the application resolved from the {@code applicationId} path variable,
   *        which must be managed by {@code direction}
   * @param role the name of one of the application's roles
   */
  @PutMapping(path = ROLE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.direction.isAdministeredBy(authentication.name)"
      + " or #application.isManagedBy(authentication.name)")
  public void addGroupRole(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @PathVariable(name = ROLE_PLACEHOLDER) String role,
      Authentication authentication) throws HururaaProblemException {
    requireInDirection(application, direction);
    groupService.addClientRole(direction, group.name(),
        keycloakProperties.apiClientId(application.getClientPrefix()), role);
    log.info("{} made group {} of {} grant role {} of {}", authentication.getName(),
        group.name(), direction, role, application.getClientPrefix());
    publish(direction, group.name(), EventType.UPDATE);
  }

  /**
   * Stops a group from granting a role of one of its direction's applications. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or a manager of the application.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group resolved from its name
   * @param application the application resolved from the {@code applicationId} path variable,
   *        which must be managed by {@code direction}
   * @param role the name of one of the application's roles
   */
  @DeleteMapping(path = ROLE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.direction.isAdministeredBy(authentication.name)"
      + " or #application.isManagedBy(authentication.name)")
  public void removeGroupRole(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @PathVariable(name = ROLE_PLACEHOLDER) String role,
      Authentication authentication) throws HururaaProblemException {
    requireInDirection(application, direction);
    groupService.removeClientRole(direction, group.name(),
        keycloakProperties.apiClientId(application.getClientPrefix()), role);
    log.info("{} made group {} of {} stop granting role {} of {}", authentication.getName(),
        group.name(), direction, role, application.getClientPrefix());
    publish(direction, group.name(), EventType.UPDATE);
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
   * Requires the user to have a say on the direction: Hurura'a administrator
   * ({@code hururaa.admin}), administrator of the direction, or manager of one of its
   * applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group resolved from its name
   * @param pageParams the requested page index and size
   * @return a page of the group's members
   */
  @GetMapping(path = MEMBERS_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.direction.hasDelegate(authentication.name)")
  public PagedModel<UserResponse> getGroupMembers(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group,
      @ParameterObject @Valid PageParams pageParams) throws HururaaProblemException {
    return new PagedModel<>(groupService
        .findMembers(direction, group.name(), pageParams.toPageable())
        .map(directoryMapper::toUserResponse));
  }

  /**
   * Adds a member of the direction to one of its groups. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or to manage at least one of its applications and every application whose
   * roles the group grants.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group resolved from its name
   * @param userId the added user's id; must be a member of the direction
   */
  @PutMapping(path = MEMBER_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.isManageableBy(authentication.name)")
  public void addGroupMember(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    groupService.addMember(direction, group.name(), userId);
    log.info("{} added {} to group {} of {}", authentication.getName(), userId, group.name(),
        direction);
    publish(direction, group.name(), EventType.UPDATE);
  }

  /**
   * Removes a member from a group. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or to manage at least one of its applications and every application whose
   * roles the group grants.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group resolved from its name
   * @param userId the removed user's id
   */
  @DeleteMapping(path = MEMBER_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.isManageableBy(authentication.name)")
  public void removeGroupMember(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    groupService.removeMember(direction, group.name(), userId);
    log.info("{} removed {} from group {} of {}", authentication.getName(), userId,
        group.name(), direction);
    publish(direction, group.name(), EventType.UPDATE);
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
