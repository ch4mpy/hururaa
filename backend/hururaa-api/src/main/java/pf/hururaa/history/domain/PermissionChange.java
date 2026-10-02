package pf.hururaa.history.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import pf.hururaa.direction.domain.User;

/**
 * A change of who may do what in a direction, from the audit of Hurura'a's own data (delegations,
 * applications) or from the journal of what Hurura'a changed in Keycloak.
 *
 * @param timestamp when the change was made
 * @param author who made it; {@code null} for a change made without an authenticated user (a
 *        migration, for instance)
 * @param type what changed
 * @param subject the user concerned: the delegate designated or revoked, the group member added
 *        or removed
 * @param applicationId the application concerned
 * @param applicationName the application's name at the time of the change
 * @param role the application role concerned
 * @param group the group concerned
 * @param otherDirection for an application moved between directions, the one it came from or went
 *        to
 * @param formerApplicationName for a renamed application, its name before
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record PermissionChange(
    Instant timestamp,
    @Nullable User author,
    PermissionChangeType type,
    @Nullable User subject,
    @Nullable Long applicationId,
    @Nullable String applicationName,
    @Nullable String role,
    @Nullable String group,
    @Nullable String otherDirection,
    @Nullable String formerApplicationName) {

  public PermissionChangeCategory category() {
    return type.category();
  }
}
