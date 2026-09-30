# Keycloak configuration

The dev realm is imported from `keycloak/import/public-facing-realm.json` when the Keycloak
container starts without it (`docker compose down -v` to re-import). It was derived from Tickee's
realm export (flows, client scopes and built-in clients), without its key pairs: Keycloak generates
fresh ones on import. To persist changes made in the admin console, run `keycloak/export.sh`.

## Realm `public-facing`

- Organizations enabled, local users only (no identity provider, no user federation).
- Locales `fr` (default) and `en`, brute-force protection on, SMTP to Mailpit
  (`host.docker.internal:1025`, `mailpit`/`secret`).

### Organizations (directions)

`dsi`, `dpam` and `daf`: name and alias are the same (the alias is what the `organization` claim
is indexed by and what Hurura'a addresses a direction with), the description is the direction's
full name. Their groups carry client roles of the `<prefix>-api` clients of their own applications
only (a Hurura'a rule, Keycloak does not enforce it).

### Clients

`hururaa-api` creates both clients of an application registered in Hurura'a when they don't exist
yet (`ClientProvisioningService`), with the settings below; the BFF's redirect URIs are placeholders
to adjust once the application is deployed.

For each application (`hururaa`, `te-fenua`, `escales`, `anahei`):

- `<prefix>-bff`: confidential, authorization code with PKCE (`S256`), refresh token, default
  scope `organization` (the gateway requests `organization:*` to get every membership). Only
  `hururaa-bff` has a running BFF: redirect URI `/gateway/login/oauth2/code/hururaa-bff` and
  back-channel logout to the gateway. The others have placeholder URIs (`/<prefix>/...`).
- `<prefix>-api`: confidential, client credentials only (service account), carries the
  application's roles.

Service accounts (`realm-management` roles):

- `hururaa-api`: `view-clients`, `query-clients`, `manage-clients` (application roles),
  `view-organizations`, `query-organizations`, `manage-organizations` (groups, their members and
  role mappings), `view-users`, `query-users`, `query-groups`, `manage-users`;
- the other `*-api`: `view-users`, `query-users`, `view-organizations`, `query-organizations`.

### Client scope `organization`

Two mappers: `oidc-organization-membership-mapper` (the `organization` claim, multivalued) and
`oidc-organization-group-membership-mapper` with `addGroupRoleMappings`, which nests the client
roles granted by the organization's groups under each organization:

```json
"organization": {
  "dsi": {
    "resource_access": { "hururaa-api": { "roles": ["hururaa.applications.manage", "hururaa.direction-admins.manage"] } },
    "groups": ["/sipf"]
  }
}
```

A member granted nothing in an organization still has an entry (`"dsi": { "groups": [] }`), which
is what `@tpe.isMember` relies on.

### Users

See the table in the root README: ten dev users (password `secret`) with fixed ids, which the
Liquibase dev data of `hururaa-api` references.
