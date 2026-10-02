package pf.hururaa.direction.web;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param alias how the direction is addressed: part of the URLs and the key of the tokens'
 *        {@code organization} claim, hence restricted to lower-case letters, digits and dashes
 * @param name the organization's name, unique in the realm
 * @param description the direction's full name (e.g. "Direction des affaires foncières")
 */
public record DirectionCreationRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z][a-z0-9-]*$") String alias,
    @NotBlank @Size(max = 255) String name,
    @Nullable @Size(max = 255) String description) {
}
