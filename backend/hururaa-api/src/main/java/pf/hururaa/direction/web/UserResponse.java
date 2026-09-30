package pf.hururaa.direction.web;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;

public record UserResponse(
    @NotNull String id,
    @NotNull String username,
    @Nullable String firstName,
    @Nullable String lastName,
    @Nullable String email) {
}
