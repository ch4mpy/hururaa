package pf.hururaa.direction.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.commons.events.ResourceEvent.EventType;
import pf.hururaa.commons.events.ResourceEventPublisher;
import pf.hururaa.direction.DelegationHistoryService;
import pf.hururaa.direction.domain.DelegatedDirection;
import pf.hururaa.direction.domain.DirectionAdmin;
import pf.hururaa.direction.jpa.DirectionAdminRepository;
import pf.hururaa.events.DirectionEvents;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.keycloak.GroupService;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.journal.PermissionJournal;
import pf.hururaa.problem.ProblemType;
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

  private final DirectionService directionService;

  private final GroupService groupService;

  private final DirectionAdminRepository directionAdminRepository;

  private final DelegationHistoryService delegationHistoryService;

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
   * Creates a direction: a Keycloak organization, enabled, without domain.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}).
   * </p>
   *
   * @param request the direction to create
   * @return the location of the created direction
   */
  @PostMapping(path = BASE_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')")
  public ResponseEntity<Void> createDirection(
      @RequestBody @Valid DirectionCreationRequest request,
      Authentication authentication) throws HururaaProblemException {
    final var direction =
        directionService.create(request.alias(), request.name(), request.description());
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
   * Lists the administrators of a direction: the members designated to decide who manages each of
   * its applications.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator
   * ({@code hururaa.admin}), administrator of the direction, or manager of one of its
   * applications.
   * </p>
   *
   * @param direction the direction's alias
   * @return the direction's administrators, by username (one who has left the direction is listed
   *         with their id as username)
   */
  @GetMapping(path = ADMINS_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.hasDelegate(authentication.name)")
  public List<UserResponse> getDirectionAdmins(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction)
      throws HururaaProblemException {
    final var admins = new ArrayList<UserResponse>();
    for (final var admin : directionAdminRepository
        .findByDirectionOrderByUserId(direction.alias())) {
      final var member = directionService.findMember(direction.alias(), admin.getUserId());
      admins.add(member.isPresent() ? directoryMapper.toUserResponse(member.get())
          : new UserResponse(admin.getUserId(), admin.getUserId(), null, null, null));
    }
    admins.sort(Comparator.comparing(UserResponse::username));
    return admins;
  }

  /**
   * Designates a member of a direction as one of its administrators. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}).
   * </p>
   *
   * @param direction the direction's alias
   * @param userId the designated user's id; must be a member of the direction
   */
  @PutMapping(path = ADMIN_PATH)
  @Transactional(rollbackFor = HururaaProblemException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')")
  public void addDirectionAdmin(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) throws HururaaProblemException {
    directionService.requireMember(direction.alias(), userId);
    if (!directionAdminRepository.existsByDirectionAndUserId(direction.alias(), userId)) {
      directionAdminRepository
          .save(DirectionAdmin.builder().direction(direction.alias()).userId(userId).build());
      log.info("{} designated {} as administrator of {}", authentication.getName(), userId,
          direction.alias());
      resourceEvents.publish(DirectionEvents.of(direction.alias(), DirectionEvents.DIRECTION,
          direction.alias(), EventType.UPDATE));
    }
  }

  /**
   * Revokes an administrator of a direction. Idempotent.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a Hurura'a administrator ({@code hururaa.admin}).
   * </p>
   *
   * @param direction the direction's alias
   * @param userId the revoked administrator's id
   */
  @DeleteMapping(path = ADMIN_PATH)
  @Transactional
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')")
  public void removeDirectionAdmin(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId,
      Authentication authentication) {
    final var admin = directionAdminRepository.findByDirectionAndUserId(direction.alias(), userId);
    if (admin.isPresent()) {
      directionAdminRepository.delete(admin.get());
      log.info("{} revoked {} as administrator of {}", authentication.getName(), userId,
          direction.alias());
      resourceEvents.publish(DirectionEvents.of(direction.alias(), DirectionEvents.DIRECTION,
          direction.alias(), EventType.UPDATE));
    }
  }

  /**
   * Searches a direction's members by username, first name, last name, or e-mail.
   *
   * <p>
   * Results follow Keycloak's own ordering and are not client-controllable: the Keycloak Admin
   * REST API exposes no sort parameter for organization members.
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
   * @param search a string contained in username, first or last name, or e-mail; empty matches
   *        all members
   * @param pageParams the requested page index and size
   * @return a page of the direction's members matching the search criteria
   */
  @GetMapping(path = USERS_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.hasDelegate(authentication.name)")
  public PagedModel<UserResponse> getDirectionUsers(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @RequestParam(required = false, defaultValue = "") String search,
      @ParameterObject @Valid PageParams pageParams) throws HururaaProblemException {
    return new PagedModel<>(directionService
        .searchMembers(direction.alias(), search, pageParams.toPageable())
        .map(directoryMapper::toUserResponse));
  }

  /**
   * Lists the groups of a direction a member belongs to.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator
   * ({@code hururaa.admin}), administrator of the direction, or manager of one of its
   * applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param userId the member's id
   * @return the member's groups in the direction, by name
   */
  @GetMapping(path = USER_GROUPS_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.hasDelegate(authentication.name)")
  public List<GroupResponse> getDirectionUserGroups(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @PathVariable(name = USER_ID_PLACEHOLDER) String userId) throws HururaaProblemException {
    return groupService
        .findByMember(direction.alias(), userId)
        .stream()
        .map(directoryMapper::toGroupResponse)
        .toList();
  }

  /**
   * Lists who designated or revoked whom, and when, in a direction: its administrators, and the
   * managers of its applications (including applications since moved to another direction or
   * deleted). Replayed from the audit trail, newest change first.
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to have a say on the direction: Hurura'a administrator
   * ({@code hururaa.admin}), administrator of the direction, or manager of one of its
   * applications.
   * </p>
   *
   * @param direction the direction's alias
   * @param pageParams the requested page index and size
   * @return a page of the direction's delegation changes, with their author and delegate (one who
   *         has left both the direction and the DSI is returned with their id as username)
   */
  @GetMapping(path = HISTORY_PATH)
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + HururaaPermission.Names.ADMIN + "')"
      + " or #direction.hasDelegate(authentication.name)")
  public PagedModel<DelegationChangeResponse> getDirectionHistory(
      @PathVariable(name = DIRECTION_PLACEHOLDER) DelegatedDirection direction,
      @ParameterObject @Valid PageParams pageParams) throws HururaaProblemException {
    return new PagedModel<>(delegationHistoryService
        .findByDirection(direction.alias(), pageParams.toPageable())
        .map(directoryMapper::toDelegationChangeResponse));
  }
}
