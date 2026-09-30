package pf.hururaa.direction.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param name the group's name, unique in its direction: part of the URLs addressing the group,
 *        hence restricted to lower-case letters, digits, dots, dashes and underscores
 */
public record GroupRequest(
    @NotBlank @Size(max = 255) @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*$") String name) {
}
