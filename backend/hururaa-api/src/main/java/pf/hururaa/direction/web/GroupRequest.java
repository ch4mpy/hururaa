package pf.hururaa.direction.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param name the group's name within its application ({@code agent}), which the application's
 *        client prefix completes ({@code escales.agent}): part of the URLs addressing the group,
 *        hence restricted to lower-case letters, digits, dots, dashes and underscores
 */
public record GroupRequest(
    @NotBlank @Size(max = 190) @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*$") String name) {
}
