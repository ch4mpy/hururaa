package pf.hururaa.application.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param name the display name
 * @param direction alias of the direction managing the application; changing it drops the
 *        application's managers, who are members of the former direction
 */
public record ApplicationUpdateRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String direction) {
}
