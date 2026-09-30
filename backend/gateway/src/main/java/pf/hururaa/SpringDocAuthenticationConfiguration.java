/**
 *
 */
package pf.hururaa;

import java.util.List;
import java.util.Objects;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * Add to the OpenAPI specification the end-points to:
 * <ul>
 * <li>initiate an OAuth2 Authorization Code flow for each registration with
 * authorization_code</li>
 * <li>perform <a href=
 * "https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Intiated
 * Logout</a></li>
 * </ul>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
class SpringDocAuthenticationConfiguration {

    @Bean
    OpenApiCustomizer springDocAuthCustomizer(
            InMemoryClientRegistrationRepository clientRegistrationRepository) {
        return openapi -> {
            // Add a "path" for each registration with the authorization_code grant type
            clientRegistrationRepository.forEach(registration -> {
                if (Objects
                        .equals(
                                AuthorizationGrantType.AUTHORIZATION_CODE,
                                registration.getAuthorizationGrantType())) {
                    openapi
                            .getPaths()
                            .addPathItem(
                                    "/oauth2/authorization/%s".formatted(registration.getRegistrationId()),
                                    new PathItem()
                                            .get(
                                                    new Operation()
                                                            .tags(List.of("Gateway"))
                                                            .operationId(
                                                                    "startLoginWith%s%s"
                                                                            .formatted(
                                                                                    registration
                                                                                            .getRegistrationId()
                                                                                            .substring(0, 1)
                                                                                            .toUpperCase(),
                                                                                    registration
                                                                                            .getRegistrationId()
                                                                                            .substring(1)
                                                                                            .replaceAll("-", "")))
                                                            .responses(
                                                                    new ApiResponses()
                                                                            .addApiResponse(
                                                                                    Integer.valueOf(
                                                                                            HttpStatus.OK.value())
                                                                                            .toString(),
                                                                                    new ApiResponse().description(
                                                                                            HttpStatus.OK.name())))
                                                            .description(
                                                                    "Initiate an authorization code flow with the URI to follow in the response's Location header")));
                }
            });

            // Add a "path" for logout
            openapi
                    .getPaths()
                    .addPathItem(
                            "/logout",
                            new PathItem()
                                    .post(
                                            new Operation()
                                                    .tags(List.of("Gateway"))
                                                    .operationId("logout")
                                                    .responses(
                                                            new ApiResponses()
                                                                    .addApiResponse(
                                                                            Integer.valueOf(HttpStatus.ACCEPTED.value())
                                                                                    .toString(),
                                                                            new ApiResponse().description(
                                                                                    HttpStatus.ACCEPTED.name())))
                                                    .description("logout")));
        };
    }
}
