package pf.hururaa.application.web;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param name the role's name, as found in tokens: lower-case, dot-separated segments (e.g.
 *        {@code escales.bookings.edit})
 * @param description what the role allows
 */
public record ApplicationRoleRequest(
    @NotBlank @Size(max = 255) @Pattern(regexp = "^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*$") String name,
    @Nullable @Size(max = 255) String description) {
}
