package pf.hururaa.direction.web;

import jakarta.validation.constraints.NotNull;

/**
 * An application role granted by a group.
 *
 * @param applicationId the application the role belongs to
 * @param applicationName the application's display name
 * @param clientId the Keycloak client carrying the role (what the token's
 *        {@code resource_access} is indexed by)
 * @param role the role's name
 */
public record GroupRoleResponse(
    @NotNull Long applicationId,
    @NotNull String applicationName,
    @NotNull String clientId,
    @NotNull String role) {
}
