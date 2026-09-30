package pf.hururaa.keycloak;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.keycloak.admin.model.OrganizationRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import pf.hururaa.direction.domain.Direction;
import pf.hururaa.direction.domain.User;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;

/**
 * <p>
 * Adapter in front of {@link CachingKeycloakOrganizationRepository}: the directions (Keycloak
 * organizations) and their members, addressed by the organization's alias rather than Keycloak's
 * internal id.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class DirectionService {

  private final CachingKeycloakOrganizationRepository organizationRepo;

  private final KeycloakRepresentationMapper mapper;

  /**
   * @return every direction, by alias
   */
  public List<Direction> findAll() throws HururaaProblemException {
    return organizationRepo
        .findAll()
        .stream()
        .map(mapper::toDirection)
        .sorted(Comparator.comparing(Direction::alias))
        .toList();
  }

  /**
   * @param direction a direction alias
   * @return the direction, empty if no organization has that alias
   */
  public Optional<Direction> findByAlias(String direction) throws HururaaProblemException {
    final var org = findOrganization(direction);
    return org.isPresent() ? Optional.of(mapper.toDirection(org.get())) : Optional.empty();
  }

  /**
   * @param direction a direction alias
   * @return whether an organization has that alias
   */
  public boolean exists(String direction) throws HururaaProblemException {
    return findOrganization(direction).isPresent();
  }

  /**
   * @param direction a direction alias
   * @param userId a user id
   * @return the user if a member of the direction, empty otherwise
   * @throws HururaaProblemException {@code DIRECTION_NOT_FOUND} if the direction does not exist
   */
  public Optional<User> findMember(String direction, String userId)
      throws HururaaProblemException {
    final var member = organizationRepo.findMember(requireOrgId(direction), userId);
    return member.isPresent() ? Optional.of(mapper.toUser(member.get())) : Optional.empty();
  }

  /**
   * @param direction a direction alias
   * @param userId a user id
   * @return the user
   * @throws HururaaProblemException {@code NOT_A_MEMBER} if the user is not a member of the
   *         direction, {@code DIRECTION_NOT_FOUND} if the direction does not exist
   */
  public User requireMember(String direction, String userId) throws HururaaProblemException {
    final var member = findMember(direction, userId);
    if (member.isEmpty()) {
      throw new HururaaProblemException(
          ProblemType.NOT_A_MEMBER,
          "User %s is not a member of direction %s".formatted(userId, direction),
          Map.of("userId", userId));
    }
    return member.get();
  }

  /**
   * @param direction a direction alias
   * @param search a string contained in the username, first or last name, or e-mail of the
   *        members to return; blank for all of them
   * @param pageable page index and size; sort is ignored
   * @return the direction's members matching {@code search}
   */
  public Page<User> searchMembers(String direction, String search, Pageable pageable)
      throws HururaaProblemException {
    return organizationRepo
        .findMembers(requireOrgId(direction), search, pageable)
        .map(mapper::toUser);
  }

  /**
   * @param direction a direction alias
   * @return Keycloak's id for the organization
   * @throws HururaaProblemException {@code DIRECTION_NOT_FOUND} if no organization has that alias
   */
  String requireOrgId(String direction) throws HururaaProblemException {
    final var org = findOrganization(direction);
    if (org.isEmpty()) {
      throw new HururaaProblemException(
          ProblemType.DIRECTION_NOT_FOUND,
          "No direction with alias %s".formatted(direction),
          Map.of("direction", direction));
    }
    return Objects.requireNonNull(org.get().getId(), "organization id");
  }

  /**
   * Looked up in the (cached) list of every organization rather than with Keycloak's
   * {@code search}, which matches names and not aliases.
   */
  private Optional<OrganizationRepresentation> findOrganization(String direction)
      throws HururaaProblemException {
    return organizationRepo
        .findAll()
        .stream()
        .filter(org -> direction.equals(org.getAlias()))
        .findAny();
  }
}
