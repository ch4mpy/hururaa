package pf.hururaa.me;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @param directions the aliases of the directions (Keycloak organizations) the user is a member of,
 *        whether or not a group of theirs grants them roles there
 */
public record UserResponse(
        @Nullable String sub,
        @Nullable String email,
        @Nullable String username,
        @Nullable String firstName,
        @Nullable String lastName,
        List<String> directions) {
    public static final UserResponse ANONYMOUS =
        new UserResponse(null, null, null, null, null, List.of());
}
