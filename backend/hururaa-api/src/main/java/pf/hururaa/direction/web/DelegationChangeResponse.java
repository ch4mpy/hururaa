package pf.hururaa.direction.web;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;
import pf.hururaa.direction.domain.DelegationChange.Change;
import pf.hururaa.direction.domain.DelegationChange.Delegation;

/**
 * A delegation granted or revoked in a direction.
 *
 * @param revision the audit revision recording the change
 * @param timestamp when the change was made
 * @param authorId who made the change; absent for a change made without an authenticated user
 * @param applicationId the application managed, for an {@code APPLICATION_MANAGER} delegation
 * @param applicationName the application's name at the time of the change, for an
 *        {@code APPLICATION_MANAGER} delegation
 */
public record DelegationChangeResponse(
    @NotNull Long revision,
    @NotNull Instant timestamp,
    @Nullable String authorId,
    @Nullable String authorUsername,
    @Nullable String authorFirstName,
    @Nullable String authorLastName,
    @NotNull Delegation delegation,
    @NotNull Change change,
    @NotNull String delegateId,
    @NotNull String delegateUsername,
    @Nullable String delegateFirstName,
    @Nullable String delegateLastName,
    @Nullable Long applicationId,
    @Nullable String applicationName) {
}
