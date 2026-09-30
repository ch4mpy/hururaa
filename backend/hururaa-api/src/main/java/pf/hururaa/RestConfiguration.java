package pf.hururaa;

import org.keycloak.admin.api.ClientRoleMappingsApi;
import org.keycloak.admin.api.ClientsApi;
import org.keycloak.admin.api.OrganizationsApi;
import org.keycloak.admin.api.RoleMapperApi;
import org.keycloak.admin.api.RolesApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

// See com.c4-soft.springaddons.rest.group properties in application.yml
@Configuration
@ImportHttpServices(group = "keycloak-group",
    types = {ClientsApi.class, ClientRoleMappingsApi.class, OrganizationsApi.class,
        RolesApi.class, RoleMapperApi.class})
public class RestConfiguration {

}
