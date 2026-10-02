# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Hurura'a is a proof of concept for the DSI: a Keycloak-backed UAA delegating, direction by
direction, the management of application permissions. The DSI runs Hurura'a for the applications
it is responsible for, which are attached to business directions (DPAM, DAF...). It is also a
direction like the others, and its `hururaa-admins` group (role `hururaa.admin`) administers
Hurura'a: say "Hurura'a administrators", never "platform" (`uaa.platform-organization` is just the
DSI's alias). `README.md` (French) describes the model, the delegation chain and the dev users.

## Repository layout

Fullstack monorepo: a Spring Boot backend (`backend/`), an Angular workspace (`frontend/angular/`),
and Docker-based dev infrastructure at the repo root.

- `backend/` — Maven multi-module reactor, `groupId` `pf.hururaa`
  - `common-events-starter` — `ResourceEvent` DTO and RabbitMQ wiring (`pf.hururaa.commons.events`), `ResourceEventPublisher` (publishes after commit, best effort). `ResourceEvent.ALL_MEMBERS` (`*`) as audience notifies every subscriber of the tenant.
  - `common-security-starter` — `HururaaAuthentication` (`getPermissionsByTenant()`), `TenantPermissionsExtractor` (per-organization client roles of the `roles-namespace` client, from the Keycloak `organization` claim), `HururaaPermissionEvaluator` (`hasPermission(#tenant, 'perm')`, `@tpe.isActive`, `@tpe.isMember` — a membership without roles is an empty set, still a key of the map), auto-configured with `@EnableMethodSecurity` by `HururaaSecurityConfiguration`. Apps must not redefine these.
  - `hururaa-api` — the REST API (package root `pf.hururaa`):
    - `keycloak` — adapters in front of the generated Keycloak admin client: package-private `CachingKeycloak*Repository` (caching, `HttpClientErrorException` → `HururaaProblemException`), public services `DirectionService` (organizations, addressed by alias, members), `GroupService` (organization groups, their members and client-role mappings), `ClientRoleService` (roles of a client), `KeycloakAdminApiProperties` (`realm-name`, `api-client-suffix`, `bff-client-suffix`: an application `escales` has the `escales-bff` and `escales-api` clients).
    - `application` — `Application` entity (client prefix, name, direction alias, `managers` element collection of Keycloak user ids), `ApplicationService` (unique prefix, creation of the missing `<prefix>-bff` / `<prefix>-api` clients by `keycloak.ClientProvisioningService`, whose service account gets `keycloak-admin-api.api-service-account-roles`, no move/delete while groups grant its roles), controllers for applications (`/applications`), their roles (client roles of `<prefix>-api`) and managers (under their direction: `/directions/{direction}/applications/{applicationId}/...`, so that access rules can read the direction's administrators). `{applicationId}` path variables are resolved by `ApplicationWebMvcConfiguration` (404 `APPLICATION_NOT_FOUND` before any access rule runs).
    - `direction` — `DirectionAdmin` entity, `DelegationResolver` (a Keycloak organization or group joined with the delegations stored for it: `DelegatedDirection` with `isAdministeredBy`, `isManagedBy`, `hasDelegate`, `DelegatedGroup` with `isManageableBy`), `DirectionWebMvcConfiguration` resolving `{direction}` and `{group}` path variables into them (404 `DIRECTION_NOT_FOUND` / `GROUP_NOT_FOUND` before any access rule runs; a group's converter reads `{direction}` from the request), controllers for directions (creation by Hurura'a administrators, admins, members search, permission history) and groups (roles, members). A group only grants roles of its own direction's applications (`APPLICATION_NOT_IN_DIRECTION`).
    - `uaa` — `HururaaPermission` (the single `hururaa.admin` client role of `hururaa-api`, carried by the DSI's `hururaa-admins` group and only effective in `uaa.platform-organization`, the DSI, where `security.HururaaAuthenticationConverter` turns them into authorities), the delegation chain in its `package-info`, `GET /me/delegations`.
    - `history` — `PermissionHistoryService`: who changed what permissions in a direction, newest first, filtered by application, group or `PermissionChangeCategory`. Merges the Envers revisions of `DirectionAdmin` and `Application` (replayed entity by entity, oldest first: a deleted row's audit holds only its id, and an application may move between directions, a grant belonging to the new state's direction and a revocation to the previous one's) with the journal (paged in SQL: its first `(page + 1) * size` events suffice to merge). Exposed under `/directions/{direction}/history`, `.../applications/{applicationId}/history` and `.../groups/{group}/history`; its test commits real revisions.
    - `journal` — `PermissionEvent` (append-only table `PERMISSION_EVENTS`) and `PermissionJournal`, journaling the permission changes Hurura'a makes in Keycloak (direction created, application roles, groups, group roles, group members), which Keycloak attributes to the API's service account. Controllers call it next to their `log.info` (kept: Loki is another audit path), only when the change was effective: the Keycloak services' write methods (`GroupService.addMember`... `ClientRoleService.save`/`delete`) return whether they changed anything, and skip the Keycloak call otherwise.
    - `events.DirectionEvents` — every event of this API is addressed to all members of the direction concerned (the gateway does not know database delegations, see `docs/decisions/0005`).
  - `gateway` — Spring Cloud Gateway OAuth2 BFF, see below.
- `frontend/angular/` — Angular 21 workspace (standalone, zoneless), app at `projects/hururaa`, generated clients at `projects/api/*`.
- Repo root — `deploy-dev.sh`, `compose-*.yml`, `keycloak/` (realm `public-facing` import, `keycloak-configuration.md`), `certs/`, `nginx-reverse-proxy/`, `.env`/`secrets/` (git-ignored, generated by `build-0-env.sh`).
- `.github/` — `ci.yml` (branches and PRs: backend, frontend, gitleaks and Trivy dependency scans), `deploy-demo.yml` (`master`: images to GHCR, Trivy image scan, Compose deployment to `hururaa.c4-soft.com`), Dependabot (Angular and PrimeNG majors held at 21, ngx-translate at 17 for pf-ui 21, TypeScript at 5.9 and ngx-device-detector at 11 with Angular 21, see `docs/decisions/0006`).
- `deploy/` — the demo stack (Caddy, `compose.yml`, `render-realms.sh` rendering the realm export with real secrets), its one-time setup in `deploy/README.md`. The Spring apps run there with the `demo` profile (last document of each `application.yml`): keep it in step when adding a host, URL or secret to the default configuration.

## Commands

```bash
./deploy-dev.sh           # (re-)builds and starts Keycloak, Postgres, Grafana LGTM, Mailpit, RabbitMQ, Nginx (host ports 443, 3643, 2633, 5672...: stop any other stack using them first)
./build-openapi.sh        # regenerates backend/openapi/out/*.openapi.json (mvnw clean verify -Popenapi,h2 -DskipTests)
```

Backend (`backend/`): `./mvnw clean install`, `./mvnw -pl hururaa-api test -Dtest=GroupControllerTest`, `./mvnw clean verify` (JaCoCo). JDK 26 (`.sdkmanrc`). Profiles `postgresql` (default) and `h2`. Liquibase context `dev` (default, `LIQUIBASE_CONTEXTS`) loads the dev applications and delegations matching the realm's fixed user ids; tests run with context `test` (`src/test/resources/application.properties`), `DevDataTest` checks the dev data. Never hand-edit dependency versions from memory: verify against Maven Central.

Frontend (`frontend/angular/`): `npm run start` (fr on 4200, en on 4205), `npm run build` (localized), `npm run test:ci`, `npm run lint`, `npm run extract-i18n`, `npm run api` (regenerate + build both `@api/*` libs — never hand-edit them). Every `ng` command is scoped to the `hururaa` project.

## Architecture

### Gateway (OAuth2 BFF)

Read the spring-addons-starter-oidc README before touching its security config. Session cookie + CSRF cookie/header for the SPA, `TokenRelay=` to `hururaa-api` on `/bff/api/v1/**`, tokens stored in the HTTP session, login/logout answering the target in a `Location` header (the SPA follows it with a real navigation), post-login/logout URIs via `X-POST-LOGIN-SUCCESS-URI` / `X-POST-LOGOUT-SUCCESS-URI`. Registration `hururaa-bff`, scopes `openid` and `organization:*`.

- `/me` (`getMe`) always answers `200`: identity and `directions`, the organizations the user is a member of (with or without roles). What the user may do comes from the API's `/me/delegations`, not from the gateway.
- `GET /bff/events/{tenant}` (`SseController`, `@tpe.isMember`) relays `hururaa-api`'s events of that direction; `SessionEndListener` pushes the `session` event when the HTTP session ends. Single gateway instance by decision (`docs/decisions/0002`).

### Security and delegations

Three levels (`README.md` §2): Hurura'a administrators (token role `hururaa.admin` in the DSI, acting at every level), direction administrators (`DIRECTION_ADMINS`: their direction's applications, roles, managers, groups and members), application managers (`Application.managers`: roles, co-managers, groups, members). Access rules are `@PreAuthorize` expressions written against the resolved path variables, without any rule bean: `hasAuthority('" + HururaaPermission.Names.X + "')` for Hurura'a roles (names are compile-time constants, never literals), `#direction.hasDelegate(authentication.name)`, `#group.isManageableBy(authentication.name)`, `#application.isManagedBy(authentication.name) and #application.direction == #direction.alias`... To check a new delegation, give the resolved object a method rather than adding a bean. The `Authentication`'s name is the `sub`, i.e. the Keycloak user id delegations are stored with. `@WebMvcTest` slices import `HururaaFixtures.WebMvcTestConfiguration` (method security, JWT converter, the real `DelegationResolver` with its properties) and mock repositories and Keycloak services (`HururaaFixtures.stubDevDelegations` stubs the dev data the converters read), so access-control tests exercise the real delegation rules; JWT claims fixtures are in `src/test/resources/jwt/`.

### Error responses (RFC 9457 problems)

In `pf.hururaa.problem`: closed `ProblemType` enum (URN `urn:hururaa:problem:<slug>`, parameters in its Javadoc), checked `HururaaProblemException` declared on endpoints and services (`rollbackFor` on writing transactions, `.unchecked()` in converters), `HururaaExceptionHandler` (5xx details logged, never sent). Adding a problem: a constant there, then a message in the frontend's `core/problem-messages.ts` (exhaustive record: compile error after `npm run api` until it exists), then the `angular-fix-i18n` skill.

### Persistence

Hibernate Envers audit, Liquibase-managed schema (`ddl-auto: none`), `open-in-view: false` (associations needed by endpoints are eager: `Application.managers`), `@Version` on `Application`. Use the `create-spring-jpa-entity` skill for new entities.

### Frontend

- pf-ui 21 (PrimeNG 21, `providePFTheme()`), PrimeFlex utilities (no Tailwind), Remix Icon (`<i class="ri-...">`, and PrimeNG `icon="ri-..."`). The shell is pf-ui's `pf-app` (header menus from `MenuItem`s, the `avatar` item, language selector switching between the `/fr/` and `/en/` builds); pages use `pf-page`, whose title and toolbar are `<ng-template #title>` / `<ng-template #toolbar>` (its content queries are `contentChild('title')` / `('toolbar')`, not the `titleTemplate` / `toolbarTemplate` property names of its typings). They search the whole content: the page's own `#title` must come before any nested one (a `p-card`'s), `pages/home.spec.ts` checks it. Specs rendering a page mock `pf-ui` with `testing/pf-page.stub.ts` (pf-ui can't load in jsdom). See `docs/decisions/0006` for why Angular 21.
- i18n: `@angular/localize` for the app (default `fr-PF`, subPath `fr`; `en-US`, subPath `en`; `@angular-eslint/template/i18n` enforced, PrimeNG technical attributes exempted in `eslint.config.js`), ngx-translate only for pf-ui's own labels (`PfUiTranslateLoader`, language from `LOCALE_ID`). PrimeNG's own labels and aria-labels (close buttons, paginators...) come from `primelocale` (`PrimeNG.setTranslation`, same language). API errors are translated from their problem type (`core/problem-messages.ts`), never displayed as sent by the server. Run the `angular-fix-i18n` skill after adding user-facing strings.
- `core/`: `UserService` (gateway `/me`, login/logout), `DelegationsService` (API `/me/delegations`: `canManageApplications`, `isDirectionAdmin`, `isManagerInDirection`... mirroring the API's access rules to adapt menus and actions), `ResourceEventsService` (one `EventSource` per direction of membership, `ResourceTypes` mirror `DirectionEvents`), `SessionEndService`, `httpErrorInterceptor` + `ErrorBanner` (failed calls), `confirm()` (PrimeNG `ConfirmationService`), toasts for successes (`injectNotifier`).
- Pages bind route params as inputs (`withComponentInputBinding`) and load with `rxResource`, reloading on the relevant `ResourceTypes`.
- Specs use `provideFakeEventStreams(streams)` (`core/resource-events.testing.ts`, jsdom has no `EventSource`).
- pf-ui loads its images from `/assets/img/...` (absolute): the nginx reverse proxy routes `/assets/`, and `angular.json` copies `node_modules/pf-ui/assets/img`.

### `.nvmrc` and `.sdkmanrc` copies

`frontend/angular/.nvmrc` and `.sdkmanrc` are plain copies of the root files: change both when bumping Node or Java.

## Spring conventions (skills)

Use the `create-spring-rest-dto`, `create-spring-mapper`, `create-spring-rest-endpoint`, `create-spring-rest-controller-test` and `create-spring-jpa-entity` skills rather than writing those by hand. Where a skill says to throw `ResponseStatusException` or `ApiProblemException`, this project throws the checked `HururaaProblemException`; where it suggests `hasAuthority(...)` rules, those are this project's Hurura'a roles (`HururaaPermission.Names` constants), combined with the delegation methods of the resolved path variables described above.
