package pf.hururaa.direction.domain;

/**
 * A group of a direction (a Keycloak organization group): its members are granted, in the context
 * of that direction, the application roles mapped to the group. A group may only grant roles of
 * the applications managed by its direction.
 *
 * @param id Keycloak's group id
 * @param direction the alias of the direction owning the group
 * @param name the group's name, unique within its direction
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record Group(String id, String direction, String name) {
}
