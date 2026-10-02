package pf.hururaa.application.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creation and update differ: the client prefix identifies the application's Keycloak clients and
 * can't change afterwards.
 *
 * @param clientPrefix what the application's Keycloak client IDs start with ({@code te-fenua} for
 *        {@code te-fenua-bff} and {@code te-fenua-api}); they are created if they don't exist yet
 * @param name the display name
 */
public record ApplicationCreationRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z][a-z0-9-]*$") String clientPrefix,
    @NotBlank @Size(max = 255) String name) {
}
