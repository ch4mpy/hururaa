package pf.hururaa.application.web;

import java.util.Objects;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants.ComponentModel;
import org.springframework.beans.factory.annotation.Autowired;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.domain.ApplicationRole;
import pf.hururaa.keycloak.KeycloakAdminApiProperties;

/**
 * An abstract class rather than an interface: the client IDs of the response are derived from the
 * application's prefix with the configured suffixes.
 */
@Mapper(componentModel = ComponentModel.SPRING)
public abstract class ApplicationMapper {

  @Autowired
  protected KeycloakAdminApiProperties keycloakProperties;

  public ApplicationResponse toApplicationResponse(Application domain) {
    return new ApplicationResponse(
        Objects.requireNonNull(domain.getId(), "a response is built from a persisted application"),
        domain.getClientPrefix(),
        domain.getName(),
        domain.getDirection(),
        keycloakProperties.bffClientId(domain.getClientPrefix()),
        keycloakProperties.apiClientId(domain.getClientPrefix()));
  }

  public Application toApplication(ApplicationCreationRequest request) {
    return Application
        .builder()
        .clientPrefix(request.clientPrefix())
        .name(request.name())
        .direction(request.direction())
        .build();
  }

  public abstract ApplicationRoleResponse toApplicationRoleResponse(ApplicationRole domain);
}
