package pf.hururaa.keycloak;

import java.util.List;
import java.util.Map;
import org.keycloak.admin.model.ClientRepresentation;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;

/**
 * Creates the Keycloak clients of an application registered in Hurura'a, when they don't exist
 * yet, with the same settings as those of the dev realm's applications:
 * <ul>
 * <li>{@code <prefix>-bff}: confidential, authorization code with PKCE ({@code S256}) and refresh
 * token, the {@code organization} scope by default (the roles of the user's directions are in the
 * tokens). Its redirect URIs are placeholders ({@code /<prefix>/...}) to adjust in Keycloak once the
 * application's BFF is deployed;</li>
 * <li>{@code <prefix>-api}: confidential, client credentials only. It carries the application's
 * roles, and its service account is granted the
 * {@link KeycloakAdminApiProperties#getApiServiceAccountRoles() configured} {@code realm-management}
 * roles.</li>
 * </ul>
 *
 * <p>
 * Keycloak generates the client secrets: the application's team reads them in the admin console.
 * Existing clients are left untouched, so registering an application for clients created by hand
 * works as before.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientProvisioningService {

  static final String REALM_MANAGEMENT_CLIENT_ID = "realm-management";

  private final CachingKeycloakClientRepository clientRepo;

  private final CachingKeycloakRoleRepository roleRepo;

  private final KeycloakAdminApiProperties properties;

  /**
   * Idempotent: creates whichever of the application's two clients is missing.
   *
   * @param clientPrefix the application's client prefix
   * @param name the application's display name (used in the clients' names)
   */
  public void ensureApplicationClients(String clientPrefix, String name)
      throws HururaaProblemException {
    final var bffClientId = properties.bffClientId(clientPrefix);
    if (clientRepo.findById(bffClientId).isEmpty()) {
      clientRepo.create(bffClient(clientPrefix, bffClientId, name));
      log.info("Created Keycloak client {}", bffClientId);
    }
    final var apiClientId = properties.apiClientId(clientPrefix);
    if (clientRepo.findById(apiClientId).isEmpty()) {
      clientRepo.create(apiClient(apiClientId, name));
      grantServiceAccountRoles(apiClientId);
      log.info("Created Keycloak client {}", apiClientId);
    }
  }

  private void grantServiceAccountRoles(String apiClientId) throws HururaaProblemException {
    final var wanted = properties.getApiServiceAccountRoles();
    if (wanted.isEmpty()) {
      return;
    }
    final var roles = roleRepo
        .findAllClientRoles(REALM_MANAGEMENT_CLIENT_ID)
        .stream()
        .filter(role -> wanted.contains(role.getName()))
        .toList();
    if (roles.size() != wanted.size()) {
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Some of %s are not %s roles".formatted(wanted, REALM_MANAGEMENT_CLIENT_ID),
          Map.of());
    }
    clientRepo.grantServiceAccountRoles(apiClientId, REALM_MANAGEMENT_CLIENT_ID, roles);
  }

  static ClientRepresentation bffClient(String clientPrefix, String clientId, String name) {
    return new ClientRepresentation()
        .clientId(clientId)
        .name(name + " (BFF)")
        .description("Authorization code (PKCE) and refresh token: users log in to " + name)
        .enabled(true)
        .protocol("openid-connect")
        .publicClient(false)
        .clientAuthenticatorType("client-secret")
        .standardFlowEnabled(true)
        .implicitFlowEnabled(false)
        .directAccessGrantsEnabled(false)
        .serviceAccountsEnabled(false)
        .redirectUris(List.of("/%s/login/oauth2/code/%s".formatted(clientPrefix, clientId)))
        .webOrigins(List.of("+"))
        .attributes(Map.of(
            "pkce.code.challenge.method", "S256",
            "post.logout.redirect.uris", "/%s/*".formatted(clientPrefix)))
        .defaultClientScopes(List.of("acr", "profile", "organization", "basic", "email"))
        .optionalClientScopes(
            List.of("web-origins", "address", "phone", "offline_access", "roles",
                "microprofile-jwt"));
  }

  static ClientRepresentation apiClient(String clientId, String name) {
    return new ClientRepresentation()
        .clientId(clientId)
        .name(name + " (API)")
        .description("Client credentials: " + name
            + "'s REST API calls the admin API. Carries " + name + "'s roles.")
        .enabled(true)
        .protocol("openid-connect")
        .publicClient(false)
        .clientAuthenticatorType("client-secret")
        .standardFlowEnabled(false)
        .implicitFlowEnabled(false)
        .directAccessGrantsEnabled(false)
        .serviceAccountsEnabled(true);
  }
}
