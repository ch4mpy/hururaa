package pf.hururaa.history;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;
import pf.hururaa.history.domain.PermissionChangeCategory;
import pf.hururaa.history.domain.PermissionChangeType;

/**
 * A change of who may do what in a direction.
 *
 * @param timestamp when the change was made
 * @param authorId who made the change; absent for a change made without an authenticated user
 * @param subjectId the user concerned: the delegate designated or revoked, the group member added
 *        or removed
 * @param applicationName the application's name at the time of the change
 * @param formerApplicationName for a renamed application, its name before
 */
public record PermissionChangeResponse(
    @NotNull Instant timestamp,
    @NotNull PermissionChangeType type,
    @NotNull PermissionChangeCategory category,
    @Nullable String authorId,
    @Nullable String authorUsername,
    @Nullable String authorFirstName,
    @Nullable String authorLastName,
    @Nullable String subjectId,
    @Nullable String subjectUsername,
    @Nullable String subjectFirstName,
    @Nullable String subjectLastName,
    @Nullable Long applicationId,
    @Nullable String applicationName,
    @Nullable String role,
    @Nullable String group,
    @Nullable String formerApplicationName) {
}
