package pf.hururaa.history;

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
import java.util.stream.Stream;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import pf.hururaa.PersistenceConfiguration.Revinfo;
import pf.hururaa.application.domain.Application;
import pf.hururaa.direction.domain.User;
import pf.hururaa.history.domain.PermissionChange;
import pf.hururaa.history.domain.PermissionChangeType;
import pf.hururaa.history.domain.PermissionHistoryFilter;
import pf.hururaa.journal.domain.PermissionEvent;
import pf.hururaa.journal.domain.PermissionEventType;
import pf.hururaa.journal.jpa.PermissionEventRepository;
import pf.hururaa.keycloak.DirectionService;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.uaa.UaaProperties;

/**
 * Who changed what permissions in a direction, and when, newest first. Merges two sources:
 *
 * <ul>
 * <li>the Envers revisions of {@link Application} (applications registered, renamed,
 * unregistered);</li>
 * <li>the {@link PermissionEvent journal} of what Hurura'a changed in Keycloak, delegations
 * included (direction administrators and application managers are members of Keycloak
 * groups).</li>
 * </ul>
 *
 * <p>
 * A deleted row's audit record holds only its id (Envers does not store data at delete): each
 * entity's revisions are therefore replayed in order, a change belonging to the direction of the
 * new state and a revocation to the direction of the previous one. The audited history of the direction is read whole on each call
 * (it changes rarely); the journal, which grows with every group membership, is paged in SQL: for
 * the page wanted, its first {@code (page + 1) * size} events are enough to merge.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
public class PermissionHistoryService {

  private static final Comparator<RecordedChange> NEWEST_FIRST = Comparator
      .comparing(RecordedChange::timestamp)
      .thenComparingLong(RecordedChange::order)
      .reversed();

  private final EntityManager entityManager;

  private final PermissionEventRepository journal;

  private final DirectionService directionService;

  private final UaaProperties uaaProperties;

  /**
   * @param filter the direction and, optionally, the application, group and categories concerned
   * @param pageable page index and size; sort is ignored (newest change first)
   * @return the matching changes, newest first, with their author and subject resolved from
   *         Keycloak (a user who left both the direction and the DSI is returned with their id as
   *         username)
   */
  public Page<PermissionChange> find(PermissionHistoryFilter filter, Pageable pageable)
      throws HururaaProblemException {
    final var types = PermissionChangeType.of(filter.categories());
    final var audited = audited(filter.direction())
        .filter(change -> types.contains(change.type()))
        .filter(change -> filter.group() == null)
        .filter(change -> filter.applicationId() == null
            || filter.applicationId().equals(change.applicationId()))
        .toList();
    final var journaled = journaled(filter, types, (int) (pageable.getOffset() + pageable
        .getPageSize()));

    final var changes = new ArrayList<RecordedChange>(audited);
    changes.addAll(journaled.getContent());
    changes.sort(NEWEST_FIRST);
    final var from = (int) Math.min(pageable.getOffset(), changes.size());
    final var to = Math.min(from + pageable.getPageSize(), changes.size());
    final var users = new UserResolver(filter.direction());
    final var page = new ArrayList<PermissionChange>(to - from);
    for (final var change : changes.subList(from, to)) {
      page.add(change.resolve(users));
    }
    return new PageImpl<>(page, pageable, audited.size() + journaled.getTotalElements());
  }

  // ---------- journal ----------

  private Page<RecordedChange> journaled(PermissionHistoryFilter filter,
      Set<PermissionChangeType> types, int first) {
    final var eventTypes = Stream
        .of(PermissionEventType.values())
        .filter(type -> types.contains(PermissionChangeType.valueOf(type.name())))
        .toList();
    if (eventTypes.isEmpty() || first == 0) {
      return Page.empty();
    }
    Specification<PermissionEvent> spec = (event, query, cb) -> cb.and(
        cb.equal(event.get("direction"), filter.direction()),
        event.get("type").in(eventTypes));
    if (filter.applicationId() != null) {
      final var applicationId = filter.applicationId();
      spec = spec.and((event, query, cb) -> cb.equal(event.get("applicationId"), applicationId));
    }
    if (filter.group() != null) {
      final var group = filter.group();
      spec = spec.and((event, query, cb) -> cb.equal(event.get("groupName"), group));
    }
    return journal
        .findAll(spec, PageRequest.of(0, first, Sort.by(Sort.Order.desc("id"))))
        .map(PermissionHistoryService::recorded);
  }

  private static RecordedChange recorded(PermissionEvent event) {
    return new RecordedChange(event.getOccurredAt(), Objects.requireNonNull(event.getId()),
        event.getAuthorId(), PermissionChangeType.valueOf(event.getType().name()),
        event.getUserId(), event.getApplicationId(), event.getApplicationName(), event.getRole(),
        event.getGroupName(), null);
  }

  // ---------- audit ----------

  private Stream<RecordedChange> audited(String direction) {
    return applicationChanges(direction).stream();
  }

  private List<RecordedChange> applicationChanges(String direction) {
    final var changes = new ArrayList<RecordedChange>();
    for (final var entry : revisionsOfEntitiesOnceIn(Application.class, direction).entrySet()) {
      final var applicationId = entry.getKey();
      @Nullable String previousDirection = null;
      @Nullable String previousName = null;
      for (final var revision : entry.getValue()) {
        final var current = revision.isDeletion() ? null : revision.entity();
        final var currentDirection = current == null ? null : current.getDirection();
        final var name = current == null ? previousName : current.getName();
        final var wasHere = direction.equals(previousDirection);
        final var isHere = direction.equals(currentDirection);
        final var about = new ApplicationRef(applicationId, name);

        if (!wasHere && isHere) {
          changes.add(revision.change(PermissionChangeType.APPLICATION_REGISTERED).about(about));
        } else if (wasHere && isHere && !Objects.equals(previousName, name)) {
          changes.add(revision.change(PermissionChangeType.APPLICATION_RENAMED).about(about)
              .formerApplicationName(previousName));
        }
        if (wasHere && !isHere) {
          changes.add(revision.change(PermissionChangeType.APPLICATION_UNREGISTERED).about(about));
        }

        previousDirection = currentDirection;
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
      ids.add(idOf(entity));
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
      revisions
          .computeIfAbsent(idOf(entity), _ -> new ArrayList<>())
          .add(new AuditedRevision<>(entity, (Revinfo) row[1], (RevisionType) row[2]));
    }
    return revisions;
  }

  private Long idOf(Object entity) {
    return (Long) Objects.requireNonNull(
        entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity));
  }

  private record ApplicationRef(Long id, @Nullable String name) {
  }

  private record AuditedRevision<T>(T entity, Revinfo info, RevisionType type) {

    boolean isDeletion() {
      return type == RevisionType.DEL;
    }

    RecordedChange change(PermissionChangeType changeType) {
      return new RecordedChange(Instant.ofEpochMilli(info.getTimestamp()),
          Objects.requireNonNull(info.getId()), info.getUsername(), changeType, null, null, null,
          null, null, null);
    }
  }

  /**
   * A {@link PermissionChange} whose users are not resolved yet. {@code order} breaks timestamp
   * ties (the revision number, or the journal entry id).
   */
  private record RecordedChange(
      Instant timestamp,
      long order,
      @Nullable String authorId,
      PermissionChangeType type,
      @Nullable String subjectId,
      @Nullable Long applicationId,
      @Nullable String applicationName,
      @Nullable String role,
      @Nullable String group,
      @Nullable String formerApplicationName) {

    RecordedChange about(ApplicationRef application) {
      return new RecordedChange(timestamp, order, authorId, type, subjectId, application.id(),
          application.name(), role, group, formerApplicationName);
    }

    RecordedChange formerApplicationName(@Nullable String name) {
      return new RecordedChange(timestamp, order, authorId, type, subjectId, applicationId,
          applicationName, role, group, name);
    }

    PermissionChange resolve(UserResolver users) throws HururaaProblemException {
      return new PermissionChange(timestamp, authorId == null ? null : users.resolve(authorId),
          type, subjectId == null ? null : users.resolve(subjectId), applicationId,
          applicationName, role, group, formerApplicationName);
    }
  }

  /**
   * Resolves users first among the direction's members, then among the DSI's (where the Hurura'a
   * administrators are), once per id.
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
