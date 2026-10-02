package pf.hururaa.direction.domain;

/**
 * A group of a direction (a Keycloak organization group): its members are granted, in the context
 * of that direction, the application roles mapped to the group. A group belongs to the application
 * its name starts with ({@code <prefix>.<name>}), and only grants that application's roles.
 *
 * @param id Keycloak's group id
 * @param direction the alias of the direction owning the group
 * @param name the group's name, unique within its direction
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record Group(String id, String direction, String name) {
}
