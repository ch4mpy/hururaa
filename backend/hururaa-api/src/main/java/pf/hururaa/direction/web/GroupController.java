package pf.hururaa.direction.web;

import org.springframework.web.bind.annotation.RequestParam;
import org.jspecify.annotations.Nullable;
import java.util.Set;
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
import pf.hururaa.history.domain.PermissionHistoryFilter;
import pf.hururaa.history.domain.PermissionChangeCategory;
import pf.hururaa.history.PermissionHistoryService;
import pf.hururaa.history.PermissionHistoryMapper;
import pf.hururaa.history.PermissionChangeResponse;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.application.web.ApplicationController;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.direction.domain.DelegatedDirection;
import pf.hururaa.direction.domain.DelegatedGroup;
import pf.hururaa.events.DirectionEvents;
import pf.hururaa.journal.PermissionJournal;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.uaa.HururaaPermission;

/**
 * The groups of the directions. A group belongs to an application of its direction, whose client
 * prefix starts its name ({@code escales.agent}): it is created under that application, and only
 * grants that application's roles. Once created, a group is addressed by its name under its
 * direction.
 */
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
  public static final String APPLICATION_ID_PLACEHOLDER =
      ApplicationController.APPLICATION_ID_PLACEHOLDER;
  public static final String ROLE_PLACEHOLDER = "role";
  public static final String USER_ID_PLACEHOLDER = "userId";
  public static final String BASE_PATH = DirectionController.DIRECTION_PATH + "/groups";
  public static final String APPLICATION_GROUPS_PATH =
      ApplicationController.DIRECTION_APPLICATION_PATH + "/groups";
  public static final String GROUP_PATH = BASE_PATH + "/{" + GROUP_PLACEHOLDER + "}";
  public static final String ROLES_PATH = GROUP_PATH + "/roles";
  public static final String ROLE_PATH = ROLES_PATH + "/{" + ROLE_PLACEHOLDER + "}";
  public static final String MEMBERS_PATH = GROUP_PATH + "/members";
  public static final String MEMBER_PATH = MEMBERS_PATH + "/{" + USER_ID_PLACEHOLDER + "}";
  public static final String HISTORY_PATH = GROUP_PATH + "/history";

  private final GroupService groupService;

  private final ApplicationRepository applicationRepository;

  private final KeycloakAdminApiProperties keycloakProperties;

  private final DirectoryMapper directoryMapper;

  private final ResourceEventPublisher resourceEvents;

  private final PermissionJournal permissionJournal;

  private final PermissionHistoryService permissionHistoryService;

  private final PermissionHistoryMapper permissionHistoryMapper;

  /**
   * Lists a direction's groups, with the application each belongs to.
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
    final var applications = applicationRepository.findByDirectionOrderByNameAsc(direction.alias());
    return groupService
        .findAll(direction.alias())
        .stream()
        .map(group -> directoryMapper.toGroupResponse(group,
            Application.owningGroup(applications, group.name()).orElse(null)))
        .toList();
  }

  /**
   * Lists an application's groups.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator
   * ({@code hururaa.admin}), administrator of the direction, or manager of one of its
   * applications. The application must be managed by {@code direction}.
   * </p>
   *
   * @param direction the direction managing the application
   * @param application the application resolved from the {@code applicationId} path variable
   * @return the application's groups, by name
   */
  @GetMapping(path = APPLICATION_GROUPS_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("(hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.hasDelegate(authentication.name))"
      + " and #application.direction == #direction.alias")
  public List<GroupResponse> getApplicationGroups(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application)
      throws HururaaProblemException {
    return groupService
        .findAll(direction.alias())
        .stream()
        .filter(group -> application.ownsGroup(group.name()))
        .map(group -> directoryMapper.toGroupResponse(group, application))
        .toList();
  }

  /**
   * Creates a group of an application, named {@code <prefix>.<name>}. Idempotent: returns the
   * location of the existing group when the application already has one with that name.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or a manager of the application. The application must be managed by
   * {@code direction}.
   * </p>
   *
   * @param direction the direction managing the application
   * @param application the application resolved from the {@code applicationId} path variable
   * @param request the group to create
   * @return the location of the group
   */
  @PostMapping(path = APPLICATION_GROUPS_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @PreAuthorize("(hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.isAdministeredBy(authentication.name)"
      + " or #application.isManagedBy(authentication.name))"
      + " and #application.direction == #direction.alias")
  public ResponseEntity<Void> createGroup(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @Parameter(schema = @Schema(type = "integer"), description = "The ID of the application")
      @PathVariable(name = APPLICATION_ID_PLACEHOLDER) Application application,
      @RequestBody @Valid GroupRequest request,
      Authentication authentication) throws HururaaProblemException {
    final var name = application.groupName(request.name());
    final var isNew = groupService.findByName(direction.alias(), name).isEmpty();
    final var group = groupService.save(direction.alias(), name);
    if (isNew) {
      permissionJournal.groupCreated(application, group.name());
    }
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
   * Reads a group.
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
   * @return the group, with the application it belongs to
   */
  @GetMapping(path = GROUP_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.direction.hasDelegate(authentication.name)")
  public GroupResponse getGroup(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group) {
    return directoryMapper.toGroupResponse(group);
  }

  /**
   * Deletes a group, which revokes the roles it granted from its members.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or a manager of the group's application.
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
    permissionJournal.groupDeleted(direction, group.application(), group.name());
    log.info("{} deleted group {} of {}", authentication.getName(), group.name(), direction);
    publish(direction, group.name(), EventType.DELETE);
  }

  /**
   * Lists the roles of its application a group grants to its members (none for a group which
   * belongs to no application: such a group grants no role through Hurura'a).
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
   * @return the roles granted by the group, by name
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
    final var application = group.application();
    if (application == null) {
      return List.of();
    }
    final var clientId = keycloakProperties.apiClientId(application.getClientPrefix());
    return groupService
        .findClientRoles(direction, group.name(), clientId)
        .stream()
        .map(role -> new GroupRoleResponse(Objects.requireNonNull(application.getId()),
            application.getName(), clientId, role))
        .toList();
  }

  /**
   * Makes a group grant a role of its application. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or a manager of the group's application.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group resolved from its name
   * @param role the name of one of the roles of the group's application
   */
  @PutMapping(path = ROLE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.isManageableBy(authentication.name)")
  public void addGroupRole(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group,
      @PathVariable(name = ROLE_PLACEHOLDER) String role,
      Authentication authentication) throws HururaaProblemException {
    final var application = requireApplication(group);
    if (groupService.addClientRole(direction, group.name(),
        keycloakProperties.apiClientId(application.getClientPrefix()), role)) {
      permissionJournal.groupRoleGranted(application, group.name(), role);
    }
    log.info("{} made group {} of {} grant role {}", authentication.getName(), group.name(),
        direction, role);
    publish(direction, group.name(), EventType.UPDATE);
  }

  /**
   * Stops a group from granting a role of its application. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}), an administrator of
   * the direction, or a manager of the group's application.
   * </p>
   *
   * @param direction the direction's alias
   * @param group the group resolved from its name
   * @param role the name of one of the roles of the group's application
   */
  @DeleteMapping(path = ROLE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.isManageableBy(authentication.name)")
  public void removeGroupRole(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group,
      @PathVariable(name = ROLE_PLACEHOLDER) String role,
      Authentication authentication) throws HururaaProblemException {
    final var application = requireApplication(group);
    if (groupService.removeClientRole(direction, group.name(),
        keycloakProperties.apiClientId(application.getClientPrefix()), role)) {
      permissionJournal.groupRoleRevoked(application, group.name(), role);
    }
    log.info("{} made group {} of {} stop granting role {}", authentication.getName(),
        group.name(), direction, role);
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
   * the direction, or a manager of the group's application.
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
    if (groupService.addMember(direction, group.name(), userId)) {
      permissionJournal.groupMemberAdded(direction, group.application(), group.name(), userId);
    }
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
   * the direction, or a manager of the group's application.
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
    if (groupService.removeMember(direction, group.name(), userId)) {
      permissionJournal.groupMemberRemoved(direction, group.application(), group.name(), userId);
    }
    log.info("{} removed {} from group {} of {}", authentication.getName(), userId,
        group.name(), direction);
    publish(direction, group.name(), EventType.UPDATE);
  }

  /**
   * Lists who changed what about a group, and when, newest first: its creation and deletion, the
   * roles it grants and its members.
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
   * @param categories only the changes of these categories; all of them when absent
   * @param pageParams the requested page index and size
   * @return a page of the group's changes
   */
  @GetMapping(path = HISTORY_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #group.direction.hasDelegate(authentication.name)")
  public PagedModel<PermissionChangeResponse> getGroupHistory(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction,
      @Parameter(schema = @Schema(type = "string"), description = "The group's name")
      @PathVariable(name = GROUP_PLACEHOLDER) DelegatedGroup group,
      @RequestParam(name = DirectionController.CATEGORIES_PARAM, required = false)
      @Nullable Set<PermissionChangeCategory> categories,
      @ParameterObject @Valid PageParams pageParams) throws HururaaProblemException {
    return new PagedModel<>(permissionHistoryService
        .find(new PermissionHistoryFilter(direction, null, group.name(),
            categories == null ? Set.of() : categories), pageParams.toPageable())
        .map(permissionHistoryMapper::toPermissionChangeResponse));
  }

  /**
   * A group only grants roles of the application it belongs to: Keycloak scopes neither clients to
   * organizations nor groups to clients, this is where the rule is enforced.
   */
  private static Application requireApplication(DelegatedGroup group)
      throws HururaaProblemException {
    final var application = group.application();
    if (application == null) {
      throw new HururaaProblemException(
          ProblemType.GROUP_WITHOUT_APPLICATION,
          "Group %s of %s belongs to no application".formatted(group.name(),
              group.direction().alias()),
          Map.of("direction", group.direction().alias(), "group", group.name()));
    }
    return application;
  }

  private void publish(String direction, String group, EventType eventType) {
    resourceEvents.publish(DirectionEvents.of(direction, DirectionEvents.GROUP, group, eventType));
  }
}
