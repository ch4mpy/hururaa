package pf.hururaa.direction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import pf.hururaa.PersistenceConfiguration.Revinfo;
import pf.hururaa.application.domain.Application;
import pf.hururaa.direction.domain.DelegationChange;
import pf.hururaa.direction.domain.DelegationChange.Change;
import pf.hururaa.direction.domain.DelegationChange.Delegation;
import pf.hururaa.direction.domain.DirectionAdmin;
import pf.hururaa.direction.domain.User;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.uaa.UaaProperties;

/**
 * Who designated or revoked whom, and when, in a direction: replays the Envers revisions of
 * {@link DirectionAdmin} and of {@link Application#getManagers()}.
 *
 * <p>
 * A deleted row's audit record holds only its id (Envers does not store data at delete), and an
 * application may have moved between directions: each entity's revisions are therefore replayed
 * in order, a grant belonging to the direction of the new state and a revocation to the direction
 * of the previous one.
 * </p>
 *
 * <p>
 * The whole history of the direction is read on each call and paged in memory: delegations change
 * rarely, and merging two audit sources in SQL would bind this service to Envers' table layout.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class DelegationHistoryService {

  private static final Comparator<RecordedChange> NEWEST_FIRST = Comparator
      .comparingLong(RecordedChange::revision)
      .reversed()
      .thenComparing(RecordedChange::delegation)
      .thenComparing(RecordedChange::delegateId);

  private final EntityManager entityManager;

  private final DirectionService directionService;

  private final UaaProperties uaaProperties;

  /**
   * @param direction a direction alias
   * @param pageable page index and size; sort is ignored (newest change first)
   * @return the delegation changes in the direction, newest first, with their author and
   *         delegate resolved from Keycloak (a user who left the direction and the platform is
   *         returned with their id as username)
   * @throws HururaaProblemException {@code DIRECTION_NOT_FOUND} if the direction does not exist
   */
  public Page<DelegationChange> findByDirection(String direction, Pageable pageable)
      throws HururaaProblemException {
    final var changes = new ArrayList<RecordedChange>();
    changes.addAll(directionAdminChanges(direction));
    changes.addAll(applicationManagerChanges(direction));
    changes.sort(NEWEST_FIRST);

    final var from = (int) Math.min(pageable.getOffset(), changes.size());
    final var to = Math.min(from + pageable.getPageSize(), changes.size());
    final var users = new UserResolver(direction);
    final var page = new ArrayList<DelegationChange>(to - from);
    for (final var change : changes.subList(from, to)) {
      page.add(new DelegationChange(change.revision(), change.timestamp(),
          change.authorId() == null ? null : users.resolve(change.authorId()),
          change.delegation(), change.change(), users.resolve(change.delegateId()),
          change.applicationId(), change.applicationName()));
    }
    return new PageImpl<>(page, pageable, changes.size());
  }

  private List<RecordedChange> directionAdminChanges(String direction) {
    final var changes = new ArrayList<RecordedChange>();
    for (final var revisions : revisionsOfEntitiesOnceIn(DirectionAdmin.class, direction)
        .values()) {
      @Nullable DirectionAdmin previous = null;
      for (final var revision : revisions) {
        final var current = revision.isDeletion() ? null : revision.entity();
        if (previous != null && previous.getDirection().equals(direction)
            && (current == null || !isSameAdmin(previous, current))) {
          changes.add(revision.change(Delegation.DIRECTION_ADMIN, Change.REVOKED,
              previous.getUserId(), null, null));
        }
        if (current != null && current.getDirection().equals(direction)
            && (previous == null || !isSameAdmin(previous, current))) {
          changes.add(revision.change(Delegation.DIRECTION_ADMIN, Change.GRANTED,
              current.getUserId(), null, null));
        }
        previous = current;
      }
    }
    return changes;
  }

  private static boolean isSameAdmin(DirectionAdmin a, DirectionAdmin b) {
    return a.getDirection().equals(b.getDirection()) && a.getUserId().equals(b.getUserId());
  }

  private List<RecordedChange> applicationManagerChanges(String direction) {
    final var changes = new ArrayList<RecordedChange>();
    for (final var entry : revisionsOfEntitiesOnceIn(Application.class, direction).entrySet()) {
      final var applicationId = entry.getKey();
      Set<String> previousManagers = Set.of();
      @Nullable String previousName = null;
      for (final var revision : entry.getValue()) {
        final var current = revision.isDeletion() ? null : revision.entity();
        final Set<String> currentManagers = current != null
            && current.getDirection().equals(direction) ? new TreeSet<>(current.getManagers())
                : Set.of();
        final var name = current != null ? current.getName() : previousName;
        for (final var revoked : previousManagers) {
          if (!currentManagers.contains(revoked)) {
            changes.add(revision.change(Delegation.APPLICATION_MANAGER, Change.REVOKED, revoked,
                applicationId, name));
          }
        }
        for (final var granted : currentManagers) {
          if (!previousManagers.contains(granted)) {
            changes.add(revision.change(Delegation.APPLICATION_MANAGER, Change.GRANTED, granted,
                applicationId, name));
          }
        }
        previousManagers = currentManagers;
        previousName = name;
      }
    }
    return changes;
  }

  /**
   * @return the revisions, oldest first and grouped by entity id, of every entity of
   *         {@code type} which has been in {@code direction} at some point
   */
  @SuppressWarnings("unchecked")
  private <T> Map<Long, List<AuditedRevision<T>>> revisionsOfEntitiesOnceIn(Class<T> type,
      String direction) {
    final var reader = AuditReaderFactory.get(entityManager);
    final var ids = new TreeSet<Long>();
    for (final var entity : (List<T>) reader
        .createQuery()
        .forRevisionsOfEntity(type, true, false)
        .add(AuditEntity.property("direction").eq(direction))
        .getResultList()) {
      ids.add((Long) Objects.requireNonNull(entityManager
          .getEntityManagerFactory()
          .getPersistenceUnitUtil()
          .getIdentifier(entity)));
    }
    final var revisions = new LinkedHashMap<Long, List<AuditedRevision<T>>>();
    if (ids.isEmpty()) {
      return revisions;
    }
    for (final var row : (List<Object[]>) reader
        .createQuery()
        .forRevisionsOfEntity(type, false, true)
        .add(AuditEntity.id().in(ids))
        .addOrder(AuditEntity.revisionNumber().asc())
        .getResultList()) {
      final var entity = (T) row[0];
      final var id = (Long) Objects.requireNonNull(
          entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity));
      revisions
          .computeIfAbsent(id, _ -> new ArrayList<>())
          .add(new AuditedRevision<>(entity, (Revinfo) row[1], (RevisionType) row[2]));
    }
    return revisions;
  }

  private record AuditedRevision<T>(T entity, Revinfo info, RevisionType type) {

    boolean isDeletion() {
      return type == RevisionType.DEL;
    }

    RecordedChange change(Delegation delegation, Change change, String delegateId,
        @Nullable Long applicationId, @Nullable String applicationName) {
      return new RecordedChange(Objects.requireNonNull(info.getId()),
          Instant.ofEpochMilli(info.getTimestamp()), info.getUsername(), delegation, change,
          delegateId, applicationId, applicationName);
    }
  }

  /** A {@link DelegationChange} whose users are not resolved yet. */
  private record RecordedChange(
      long revision,
      Instant timestamp,
      @Nullable String authorId,
      Delegation delegation,
      Change change,
      String delegateId,
      @Nullable Long applicationId,
      @Nullable String applicationName) {
  }

  /**
   * Resolves users first among the direction's members, then among the platform organization's
   * (where the platform administrators designating direction administrators are), once per id.
   */
  private class UserResolver {
    private final String direction;

    private final Map<String, User> resolved = new HashMap<>();

    UserResolver(String direction) {
      this.direction = direction;
    }

    User resolve(String userId) throws HururaaProblemException {
      final var known = resolved.get(userId);
      if (known != null) {
        return known;
      }
      var member = directionService.findMember(direction, userId);
      if (member.isEmpty()) {
        member = directionService.findMember(uaaProperties.getPlatformOrganization(), userId);
      }
      final var user = member.orElseGet(() -> new User(userId, userId, null, null, null));
      resolved.put(userId, user);
      return user;
    }
  }
}
