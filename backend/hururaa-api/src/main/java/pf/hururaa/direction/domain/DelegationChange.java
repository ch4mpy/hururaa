package pf.hururaa.direction.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A delegation granted or revoked in a direction, as recorded by an Envers revision: a direction
 * administrator designated or revoked, or a manager of one of the direction's applications.
 *
 * @param revision the Envers revision number
 * @param timestamp when the revision was committed
 * @param author who made the change; {@code null} for a revision written without an authenticated
 *        user (a migration, for instance)
 * @param delegation which delegation changed
 * @param change whether it was granted or revoked
 * @param delegate the user the delegation was granted to, or revoked from
 * @param applicationId the application managed, for an {@link Delegation#APPLICATION_MANAGER}
 *        delegation
 * @param applicationName the application's name at the time of the change, for an
 *        {@link Delegation#APPLICATION_MANAGER} delegation
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record DelegationChange(
    long revision,
    Instant timestamp,
    @Nullable User author,
    Delegation delegation,
    Change change,
    User delegate,
    @Nullable Long applicationId,
    @Nullable String applicationName) {

  public enum Delegation {
    DIRECTION_ADMIN, APPLICATION_MANAGER
  }

  public enum Change {
    GRANTED, REVOKED
  }
}
