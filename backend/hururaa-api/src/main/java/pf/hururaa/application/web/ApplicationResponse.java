package pf.hururaa.application.web;

import jakarta.validation.constraints.NotNull;

/**
 * @param bffClientId the Keycloak client the application's users log in with
 * @param apiClientId the Keycloak client carrying the application's roles
 */
public record ApplicationResponse(
    @NotNull Long id,
    @NotNull String clientPrefix,
    @NotNull String name,
    @NotNull String direction,
    @NotNull String bffClientId,
    @NotNull String apiClientId) {
}
