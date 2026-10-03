package pf.hururaa.application.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An application's direction never changes: only its name does.
 *
 * @param name the display name
 */
public record ApplicationUpdateRequest(@NotBlank @Size(max = 255) String name) {
}
