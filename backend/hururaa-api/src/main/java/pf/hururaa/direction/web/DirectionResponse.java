package pf.hururaa.direction.web;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;

public record DirectionResponse(
    @NotNull String alias,
    @NotNull String name,
    @Nullable String description) {
}
