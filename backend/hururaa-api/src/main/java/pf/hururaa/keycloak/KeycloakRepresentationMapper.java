package pf.hururaa.keycloak;

import java.util.Objects;
import org.keycloak.admin.model.GroupRepresentation;
import org.keycloak.admin.model.MemberRepresentation;
import org.keycloak.admin.model.OrganizationRepresentation;
import org.keycloak.admin.model.RoleRepresentation;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants.ComponentModel;
import pf.hururaa.application.domain.ApplicationRole;
import pf.hururaa.direction.domain.Direction;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.direction.domain.User;

/**
 * Keycloak admin API representations to Hurura'a domain objects. The generated representations
 * have every property nullable: the ones Keycloak always sets are checked here, once, rather than
 * wherever the domain objects are used.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Mapper(componentModel = ComponentModel.SPRING)
public interface KeycloakRepresentationMapper {

  default Direction toDirection(OrganizationRepresentation dto) {
    return new Direction(
        Objects.requireNonNull(dto.getAlias(), "organization alias"),
        Objects.requireNonNull(dto.getName(), "organization name"),
        dto.getDescription());
  }

  default Group toGroup(GroupRepresentation dto, String direction) {
    return new Group(
        Objects.requireNonNull(dto.getId(), "group id"),
        direction,
        Objects.requireNonNull(dto.getName(), "group name"));
  }

  default User toUser(MemberRepresentation dto) {
    return new User(
        Objects.requireNonNull(dto.getId(), "member id"),
        Objects.requireNonNull(dto.getUsername(), "member username"),
        dto.getFirstName(),
        dto.getLastName(),
        dto.getEmail());
  }

  default ApplicationRole toApplicationRole(RoleRepresentation dto) {
    return new ApplicationRole(Objects.requireNonNull(dto.getName(), "role name"),
        dto.getDescription());
  }
}
