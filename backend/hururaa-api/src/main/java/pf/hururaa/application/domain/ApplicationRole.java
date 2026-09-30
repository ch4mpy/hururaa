package pf.hururaa.application.domain;

import org.jspecify.annotations.Nullable;

/**
 * A role of an application: a client role of its {@code <prefix>-api} Keycloak client. Granted to
 * users through the groups of the application's direction, it is found in their tokens under
 * {@code organization.<direction>.resource_access.<prefix>-api.roles}.
 *
 * @param name the role's name, unique within the application
 * @param description what the role allows, if documented
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record ApplicationRole(String name, @Nullable String description) {
}
