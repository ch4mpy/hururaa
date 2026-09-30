package pf.hururaa.application.web;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;

public record ApplicationRoleResponse(@NotNull String name, @Nullable String description) {
}
