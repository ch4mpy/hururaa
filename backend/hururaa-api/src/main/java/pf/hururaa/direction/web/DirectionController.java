package pf.hururaa.direction.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
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
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.direction.MemberFilter;
import pf.hururaa.direction.MemberSearchService;
import pf.hururaa.direction.domain.DelegatedDirection;
import pf.hururaa.events.DirectionEvents;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.history.PermissionChangeResponse;
import pf.hururaa.history.PermissionHistoryMapper;
import pf.hururaa.history.PermissionHistoryService;
import pf.hururaa.history.domain.PermissionChangeCategory;
import pf.hururaa.history.domain.PermissionHistoryFilter;
import pf.hururaa.journal.PermissionJournal;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.uaa.DelegationGroups;
import pf.hururaa.uaa.DelegationService;
import pf.hururaa.uaa.HururaaPermission;
import pf.hururaa.uaa.UaaProperties;

@Tag(name = "Directions")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
@Slf4j
public class DirectionController {
  public static final String DIRECTION_PLACEHOLDER = "direction";
  public static final String USER_ID_PLACEHOLDER = "userId";
  public static final String BASE_PATH = "/directions";
  public static final String DIRECTION_PATH = BASE_PATH + "/{" + DIRECTION_PLACEHOLDER + "}";
  public static final String ADMINS_PATH = DIRECTION_PATH + "/admins";
  public static final String ADMIN_PATH = ADMINS_PATH + "/{" + USER_ID_PLACEHOLDER + "}";
  public static final String USERS_PATH = DIRECTION_PATH + "/users";
  public static final String USER_GROUPS_PATH =
      USERS_PATH + "/{" + USER_ID_PLACEHOLDER + "}/groups";
  public static final String HISTORY_PATH = DIRECTION_PATH + "/history";
  /** The categories a permission history is filtered by (a repeatable query parameter). */
  public static final String CATEGORIES_PARAM = "categories";
  /** The group whose members are searched. */
  public static final String GROUP_PARAM = "group";
  /** The application whose groups' members are searched. */
  public static final String APPLICATION_ID_PARAM = "applicationId";
  /** The role granted by the groups whose members are searched. */
  public static final String ROLE_PARAM = "role";

  private final DirectionService directionService;

  private final MemberSearchService memberSearchService;

  private final GroupService groupService;

  private final DelegationService delegationService;

  private final ApplicationRepository applicationRepository;

  private final PermissionHistoryService permissionHistoryService;

  private final PermissionHistoryMapper permissionHistoryMapper;

  private final DirectoryMapper directoryMapper;

  private final ResourceEventPublisher resourceEvents;

  private final PermissionJournal permissionJournal;

  private final UaaProperties uaaProperties;

  /**
   * Lists the directions (Keycloak organizations), by alias.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be authenticated.
   * </p>
   *
   * @return every direction
   */
  @GetMapping(path = BASE_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public List<DirectionResponse> getDirections() throws HururaaProblemException {
    return directionService.findAll().stream().map(directoryMapper::toDirectionResponse).toList();
  }

  /**
   * Creates a direction: a Keycloak organization, enabled, without domain, with its
   * {@value DelegationGroups#ADMINS} group (granting {@code hururaa.direction.admin}) and no
   * administrator yet.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator.
   * </p>
   *
   * @param request the direction to create
   * @return the location of the created direction
   */
  @PostMapping(path = BASE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.DIRECTION_ADMIN + "')")
  public ResponseEntity<Void> createDirection(
      @RequestBody @Valid DirectionCreationRequest request,
      Authentication authentication) throws HururaaProblemException {
    final var direction =
        directionService.create(request.alias(), request.name(), request.description());
    delegationService.provisionDirection(direction.alias());
    permissionJournal.directionCreated(direction.alias());
    log.info("{} created direction {}", authentication.getName(), direction.alias());
    // the Hurura'a administrators, members of the DSI, are the ones listing every direction
    resourceEvents.publish(DirectionEvents.of(uaaProperties.getPlatformOrganization(),
        DirectionEvents.DIRECTION, direction.alias(), EventType.CREATE));
    final var location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path(DIRECTION_PATH)
        .buildAndExpand(direction.alias())
        .toUri();
    return ResponseEntity.created(location).build();
  }

  /**
   * Retrieves a single direction.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be authenticated.
   * </p>
   *
   * @param direction the direction's alias
   * @return the requested direction
   */
  @GetMapping(path = DIRECTION_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public DirectionResponse getDirection(
      @PathVariable(name = DIRECTION_PLACEHOLDER) String direction)
      throws HururaaProblemException {
    return directoryMapper.toDirectionResponse(directionService
        .findByAlias(direction)
        .orElseThrow(() -> new HururaaProblemException(ProblemType.DIRECTION_NOT_FOUND,
            "No direction with alias %s".formatted(direction), Map.of("direction", direction))));
  }

  /**
   * Lists the administrators of a direction: the members of its {@value DelegationGroups#ADMINS}
   * group, who decide who manages each of its applications.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator, administrator of the
   * direction, or manager of one of its applications.
   * </p>
   *
   * @param direction the direction's alias
   * @return the direction's administrators, by username
   */
  @GetMapping(path = ADMINS_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("#direction.hasDelegate(authentication)")
  public List<UserResponse> getDirectionAdmins(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction)
      throws HururaaProblemException {
    return delegationService
        .findAdmins(direction.alias())
        .stream()
        .map(directoryMapper::toUserResponse)
        .toList();
  }

  /**
   * Designates a member of a direction as one of its administrators (adds them to its
   * {@value DelegationGroups#ADMINS} group): effective when their token is renewed. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator.
   * </p>
   *
   * @param direction the direction's alias
   * @param userId the designated user's id; must be a member of the direction
   */
  @PutMapping(path = ADMIN_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.DIRECTION_ADMIN + "')")
  public void addDirectionAdmin(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    if (delegationService.addAdmin(direction.alias(), userId)) {
      permissionJournal.directionAdminGranted(direction.alias(), userId);
      log.info("{} designated {} as administrator of {}", authentication.getName(), userId,
          direction.alias());
      resourceEvents.publish(DirectionEvents.of(direction.alias(), DirectionEvents.DIRECTION,
          direction.alias(), EventType.UPDATE));
    }
  }

  /**
   * Revokes an administrator of a direction (removes them from its
   * {@value DelegationGroups#ADMINS} group): effective when their token is renewed. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator.
   * </p>
   *
   * @param direction the direction's alias
   * @param userId the revoked administrator's id
   */
  @DeleteMapping(path = ADMIN_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.DIRECTION_ADMIN + "')")
  public void removeDirectionAdmin(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    if (delegationService.removeAdmin(direction.alias(), userId)) {
      permissionJournal.directionAdminRevoked(direction.alias(), userId);
      log.info("{} revoked {} as administrator of {}", authentication.getName(), userId,
          direction.alias());
      resourceEvents.publish(DirectionEvents.of(direction.alias(), DirectionEvents.DIRECTION,
          direction.alias(), EventType.UPDATE));
    }
  }

  /**
   * Searches a direction's members by username, first name, last name, or e-mail, optionally
   * narrowed to the members of one of its groups, of the groups of one of its applications, or of
   * those granting a role.
   *
   * <p>
   * Without narrowing, results follow Keycloak's own ordering (the Keycloak Admin REST API exposes
   * no sort parameter for organization members); narrowed, they are sorted by username.
   * </p>
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator, administrator of the
   * direction, or manager of one of its applications. The application, if any, must be managed by
   * {@code direction}.
   * </p>
   *
   * @param direction the direction's alias
   * @param search a string contained in username, first or last name, or e-mail; empty matches
   *        all members
   * @param group only the members of this group of the direction
   * @param application only the members of this application's groups
   * @param role only the members of a group granting a role with this name (of the group's
   *        application)
   * @param pageParams the requested page index and size
   * @return a page of the direction's members matching the search criteria
   */
  @GetMapping(path = USERS_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("#direction.hasDelegate(authentication)"
      + " and (#application == null or #application.direction == #direction.alias)")
  public PagedModel<UserResponse> getDirectionUsers(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @RequestParam(required = false, defaultValue = "") String search,
      @RequestParam(name = GROUP_PARAM, required = false) @Nullable String group,
      @Parameter(schema = @Schema(type = "integer"),
          description = "The ID of the application whose groups' members are searched")
      @RequestParam(name = APPLICATION_ID_PARAM, required = false) @Nullable Application application,
      @RequestParam(name = ROLE_PARAM, required = false) @Nullable String role,
      @ParameterObject @Valid PageParams pageParams) throws HururaaProblemException {
    return new PagedModel<>(memberSearchService
        .search(direction.alias(), new MemberFilter(search, blankToNull(group), application,
            blankToNull(role)), pageParams.toPageable())
        .map(directoryMapper::toUserResponse));
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }

  /**
   * Lists the groups of a direction a member belongs to, but the
   * {@link DelegationGroups delegation groups} (their delegations are listed apart).
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator, administrator of the
   * direction, or manager of one of its applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param userId the member's id
   * @return the member's groups in the direction, by name
   */
  @GetMapping(path = USER_GROUPS_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("#direction.hasDelegate(authentication)")
  public List<GroupResponse> getDirectionUserGroups(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId) throws HururaaProblemException {
    final var applications = applicationRepository.findByDirectionOrderByNameAsc(direction.alias());
    return groupService
        .findByMember(direction.alias(), userId)
        .stream()
        .filter(group -> !DelegationGroups.isReserved(group.name()))
        .map(group -> directoryMapper.toGroupResponse(group,
            Application.owningGroup(applications, group.name()).orElse(null)))
        .toList();
  }

  /**
   * Lists who changed what permissions in a direction, and when, newest first: directions
   * created, delegations (administrators and application managers), applications registered,
   * renamed or unregistered, application roles, groups, the roles they grant and their
   * members. Changes made outside Hurura'a (in Keycloak's console) are not known.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator, administrator of the
   * direction, or manager of one of its applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param categories only the changes of these categories; all of them when absent
   * @param pageParams the requested page index and size
   * @return a page of the direction's permission changes, with their author and the user they
   *         concern (one who has left both the direction and the DSI is returned with their id as
   *         username)
   */
  @GetMapping(path = HISTORY_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("#direction.hasDelegate(authentication)")
  public PagedModel<PermissionChangeResponse> getDirectionHistory(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @RequestParam(name = CATEGORIES_PARAM, required = false)
      @Nullable Set<PermissionChangeCategory> categories,
      @ParameterObject @Valid PageParams pageParams) throws HururaaProblemException {
    return new PagedModel<>(permissionHistoryService
        .find(PermissionHistoryFilter.ofDirection(direction.alias(),
            categories == null ? Set.of() : categories), pageParams.toPageable())
        .map(permissionHistoryMapper::toPermissionChangeResponse));
  }
}
