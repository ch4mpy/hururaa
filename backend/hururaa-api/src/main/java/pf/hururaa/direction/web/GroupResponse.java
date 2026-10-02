package pf.hururaa.direction.web;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * @param id Keycloak's group id
 * @param name the group's full name ({@code escales.agent})
 * @param applicationId the application the group belongs to, absent for a group created outside of
 *        Hurura'a whose name matches no application of its direction
 * @param applicationName that application's display name
 */
public record GroupResponse(
    @NotNull String id,
    @NotNull String name,
    @Nullable Long applicationId,
    @Nullable String applicationName) {
}
