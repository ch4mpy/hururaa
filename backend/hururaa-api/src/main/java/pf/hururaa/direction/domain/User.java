package pf.hururaa.direction.domain;

import org.jspecify.annotations.Nullable;

/**
 * A Keycloak user, as seen by Hurura'a: read-only (identities are managed in Keycloak).
 *
 * @param id the user's id, which is also the {@code sub} claim of their tokens
 * @param username the user's username
 * @param firstName the user's first name, if known
 * @param lastName the user's last name, if known
 * @param email the user's e-mail, if known
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record User(
    String id,
    String username,
    @Nullable String firstName,
    @Nullable String lastName,
    @Nullable String email) {
}
