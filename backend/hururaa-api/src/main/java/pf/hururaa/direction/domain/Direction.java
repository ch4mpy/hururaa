package pf.hururaa.direction.domain;

import org.jspecify.annotations.Nullable;

/**
 * A direction (department) of the administration: a Keycloak organization. Its members are the
 * users of the applications it manages, and its groups aggregate the roles of those applications.
 *
 * @param alias the organization's alias: what the {@code organization} claim of the tokens is
 *        indexed by, and how the direction is addressed in this API
 * @param name the organization's name
 * @param description the organization's description (the direction's full name, e.g.
 *        "Direction des systèmes d'information")
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record Direction(String alias, String name, @Nullable String description) {
}
