package pf.hururaa.direction.web;

import jakarta.validation.constraints.NotNull;

public record GroupResponse(@NotNull String id, @NotNull String name) {
}
