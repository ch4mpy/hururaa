# Plan : erreurs de la gateway en problèmes RFC 9457 typés (0003)

- Spec : [v1](spec.md) (approuvée le 2026-10-14, risque élevé : authentification)
- Statut : approuvé
- Branche : `0003-erreurs-gateway-problemes-types` (PR #26)
- Approuvé : 2026-10-14, ch4mpy (développeur et tech lead : risque élevé, authentification)

## Contexte

La spec veut que toute erreur reçue par la SPA, de la gateway comme de hururaa-api, soit un
problème d'un type de `ProblemType` (six types génériques), avec spring-addons 9.5.2 et un
pass-through de session invalide réduit à `/me`. Constats de lecture qui orientent le plan :

- Un `@ControllerAdvice` **ne s'applique pas** aux routes fonctionnelles de Spring Cloud Gateway ni
  au gestionnaire de ressources statiques (la `404` des routes inconnues) :
  `AbstractHandlerMethodExceptionResolver.shouldApplyTo` (spring-webmvc 7.0.9) ne retient que les
  `HandlerMethod`, sauf handlers explicitement mappés. D'où un **`HandlerExceptionResolver`**,
  inséré juste avant `DefaultHandlerExceptionResolver` (`WebMvcConfigurer#extendHandlerExceptionResolvers`) :
  après les `@ExceptionHandler` de `HururaaExceptionHandler`, avant les `sendError` du framework.
- Décision du développeur : **corps écrits sur place** (testables en MockMvc) ; un `ErrorController`
  reste en filet pour ce qui atteint `/error` (exceptions de filtres, `sendError` du conteneur).
- Le proxy de la gateway (`RestClientProxyExchange`, gateway-server-webmvc 5.0.3) passe par
  `RestClient` : API injoignable ou délai dépassé = `ResourceAccessException`.
- spring-addons 9.5.2 : point d'entrée remplaçable (`DefaultAuthenticationEntryPointCondition`),
  stratégie de session invalide aussi (`@ConditionalOnMissingBean`) ; `permit-all-pass-through`
  remplacé par `pass-through-patterns` (vide par défaut) ; post-processeurs
  `ClientSynchronizedHttpSecurityPostProcessor`, `ResourceServerSynchronizedHttpSecurityPostProcessor`
  (appliqué en dernier, il remplace le point d'entrée `sendError(401)` du resource server) et
  `ResourceServerExpressionInterceptUrlRegistryPostProcessor`.
- La `401` vide d'une route inconnue de la gateway vient de sa chaîne resource server (tout sauf
  `/v3/api-docs` y exige un jeton) : elle passe en `permitAll`, sauf `/actuator/**`.
- Le module commun garde le paquet `pf.hururaa.problem` : aucun import ne change, et le scan de
  composants des deux applications (`pf.hururaa`) le trouve sans auto-configuration (PA-1 : les
  starters ne changent pas).
- La stratégie de session invalide de Hurura'a **n'ouvre pas** de session (celle de spring-addons
  en ouvre une) : seul `/me` en ouvre une (PA-2), les requêtes parallèles reçoivent `401` sans
  créer de session orpheline ; le front rappelle `/me` sur une `401` (`sessionExpiredInterceptor`).
- Les clients générés (`backend/openapi/out`, `@api/*`) ne sont pas versionnés : `npm run api` les
  régénère ; seuls les catalogues `src/locale/*.xlf` sont des fichiers dérivés commités.

## Fichiers

| Fichier | Changement | Pour |
| --- | --- | --- |
| `docs/intent/0003-…/plan.md` | nouveau : ce plan | — |
| `docs/intent/0003-…/intent.md`, `docs/intent/README.md` | statut *planifiée* (tenue de l'index) | — |
| `backend/pom.xml` | spring-addons 9.5.1 → **9.5.2** ; module `hururaa-problem` et sa gestion de dépendance | EX-11, CA-11 |
| `backend/hururaa-problem/pom.xml` | nouveau module (jar simple : spring-webmvc, spring-security-web, jackson, swagger-annotations ; tests) | EX-1…8 |
| `backend/hururaa-problem/src/main/java/pf/hururaa/problem/ProblemType.java` | déplacé de hururaa-api ; + `UNAUTHENTICATED`, `ACCESS_DENIED`, `NOT_FOUND`, `INVALID_REQUEST`, `SERVER_ERROR`, `SERVICE_UNAVAILABLE` (Javadoc : paramètres aucun) | EX-1…8 |
| `…/HururaaProblemDetail.java` | déplacé ; constructeur avec statut explicite (pour `INVALID_REQUEST`) ; reçoit la constante `PROBLEM_TYPE_SCHEMA_REF` | EX-6 |
| `…/ProblemResponses.java` | nouveau : écrit un problème (`application/problem+json`, `instance` = URI demandée, `detail` générique) sur une `HttpServletResponse`, avec le `JsonMapper` du contexte | EX-1…8 |
| `…/ProblemAuthenticationEntryPoint.java`, `…/ProblemAccessDeniedHandler.java` | nouveaux : `401` `UNAUTHENTICATED` (en-tête `WWW-Authenticate` optionnel), `403` `ACCESS_DENIED` | EX-1, EX-2, EX-5 |
| `…/ProblemHandlerExceptionResolver.java` + `…/ProblemWebMvcConfiguration.java` | nouveaux : `ErrorResponse` → `NOT_FOUND` (`NoResourceFound`, `NoHandlerFound`), autre 4xx → `INVALID_REQUEST` (statut gardé), sinon `SERVER_ERROR` (journalisé) ; ignore les exceptions de sécurité, `AsyncRequestNotUsableException` et les réponses déjà engagées (SSE) ; inséré avant `DefaultHandlerExceptionResolver` | EX-4, EX-6, EX-7 |
| `…/HururaaErrorController.java` | nouveau : filet `/error` (remplace `BasicErrorController`), type selon le statut, `@PreAuthorize("permitAll()")` | EX-7 |
| `backend/hururaa-problem/src/test/java/pf/hururaa/problem/*Test.java` | nouveaux : résolveur, filet `/error`, forme JSON (`type`, `status`, `parameters`) | CA-6, CA-7 |
| `backend/hururaa-api/pom.xml` | dépendance `hururaa-problem` | — |
| `backend/hururaa-api/src/main/java/pf/hururaa/problem/{ProblemType,HururaaProblemDetail}.java` | supprimés (déplacés) | — |
| `…/hururaa-api/…/problem/ProblemTypeOpenApiCustomizer.java`, `HururaaExceptionHandler.java` | constante déplacée ; Javadoc (plus de `ProblemDetailsExceptionHandler` de Boot) | — |
| `…/hururaa-api/…/security/ProblemSecurityConfiguration.java` | nouveau : `ResourceServerSynchronizedHttpSecurityPostProcessor` (point d'entrée `WWW-Authenticate: Bearer` + problème, accès refusé) | EX-2, EX-5 |
| `backend/hururaa-api/src/main/resources/application.yml` | retire `spring.mvc.problemdetails.enabled` ; `/error` en `permit-all` du resource server | EX-6, EX-7 |
| `backend/hururaa-api/src/test/java/pf/hururaa/HururaaFixtures.java` | importe `ProblemSecurityConfiguration` | CA-2, CA-5 |
| `backend/hururaa-api/src/test/java/pf/hururaa/problem/ApiProblemsTest.java` | nouveau `@WebMvcTest` (vrai contrôleur, ex. applications) : anonyme → `401` `unauthenticated` ; membre sans rôle → `403` `access-denied` ; JSON illisible → `400`, méthode → `405` `invalid-request` ; `APPLICATION_NOT_FOUND` inchangé | CA-2, CA-5, CA-6, CA-10 |
| `backend/gateway/pom.xml` | dépendance `hururaa-problem` | — |
| `backend/gateway/src/main/java/pf/hururaa/security/ProblemSecurityConfiguration.java` | nouveau : beans `AuthenticationEntryPoint` et `InvalidSessionStrategy` (`401`, sans session ni `Location`), `ClientSynchronizedHttpSecurityPostProcessor` (accès refusé), `ResourceServerExpressionInterceptUrlRegistryPostProcessor` (`/actuator/**` authentifié, reste `permitAll`) | EX-1, EX-3, EX-4, EX-5 |
| `backend/gateway/src/main/java/pf/hururaa/ApiUnavailableExceptionResolver.java` | nouveau : `ResourceAccessException` → `503` `SERVICE_UNAVAILABLE` (journalisé) | EX-8 |
| `backend/gateway/src/main/java/pf/hururaa/SpringSecurityPatchConfiguration.java` | `401` du rafraîchissement refusé : problème `UNAUTHENTICATED`, sans `Location` | EX-2 |
| `backend/gateway/src/main/resources/application.yml` | `pass-through-patterns: [/me]` ; commentaires (0003) | EX-3, EX-11 |
| `backend/gateway/src/test/java/pf/hururaa/GatewayProblemsTest.java` | nouveau (MockMvc, WireMock) : les cas de la gateway (CA-1, 3, 4, 5, 7, 10) | EX-12 |
| `backend/gateway/src/test/java/pf/hururaa/ApiUnavailableTest.java` | nouveau : `rest-api-v1` sur un port fermé → `503` | CA-8 |
| `backend/gateway/src/test/java/pf/hururaa/RestfulOAuth2FilterTest.java` | nouveau (unitaire) : rafraîchissement refusé → `401` problème sans `Location` | CA-2 |
| `backend/gateway/src/test/java/pf/hururaa/security/InvalidSessionTest.java`, `InvalidSessionSafeguardTest.java` | modifiés : propriété 9.5.2 ; `/bff/**` + cookie inconnu → `401` de la gateway, sans nouvelle session ; corps problème au lieu du texte | CA-3 |
| `backend/gateway/src/test/resources/wiremock/hururaa-api/mappings/*.json` | stub d'un `APPLICATION_NOT_FOUND` (traversée inchangée) ; `401` sans jeton en problème | CA-2, CA-10 |
| `frontend/angular/projects/hururaa/src/app/core/problem-messages.ts` (+ `.spec.ts`) | six messages (tableau de la spec) | EX-9, CA-8, CA-9 |
| `frontend/angular/projects/hururaa/src/app/core/http-error.interceptor.ts` (+ `.spec.ts`), `session-expired.interceptor.spec.ts` | commentaire ; cas `503` typé (message dédié), `401` typé (état déconnecté, « Session expirée… ») | CA-8, CA-9 |
| `frontend/angular/projects/hururaa/src/locale/messages*.xlf` | régénérés (skill `angular-fix-i18n`) | UX-1 |
| `deploy/smoke.sh` | `expect_problem` (statut, `application/problem+json`, type) : route inconnue `404`, `/bff/events` et `/bff/api` sans session `401`, `/bff/api` cookie inconnu `401` | EX-12 |
| `CLAUDE.md` | module `hururaa-problem`, section *Error responses* (types génériques, résolveur, filet `/error`, entrée de la gateway), pass-through `/me` | — |

## Ordre de travail

1. **spring-addons 9.5.2** (`backend/pom.xml`, `application.yml` : `pass-through-patterns: [/me]`,
   tests d'invalid-session adaptés à la propriété et au nouveau trajet de `/bff/**`, statuts
   seulement à ce stade) — skill : aucun — vérification :
   `cd backend && ./mvnw -pl gateway -am verify` ; contre-épreuve : sans `pass-through-patterns`,
   le cas `/me` + cookie inconnu échoue (`401`).
2. **Module `hururaa-problem`** (déplacement de `ProblemType` et `HururaaProblemDetail`, six types,
   écrivain, point d'entrée, accès refusé, résolveur, filet `/error`, tests unitaires) ; hururaa-api
   compile contre lui — skill : aucun — vérification :
   `./mvnw -pl hururaa-problem,hururaa-api -am verify`.
3. **hururaa-api** (post-processeur de sécurité, `/error` ouvert, `problemdetails` retiré, fixtures,
   `ApiProblemsTest`) — skill : `create-spring-rest-controller-test` (conventions des tests
   d'accès) — vérification : `./mvnw -pl hururaa-api verify` (tous les tests d'accès existants
   verts).
4. **Gateway** (configuration de sécurité, résolveur `503`, filtre de rafraîchissement,
   `GatewayProblemsTest`, `ApiUnavailableTest`, `RestfulOAuth2FilterTest`, tests d'invalid-session
   sur les corps, stubs WireMock) — skill : aucun — vérification : `./mvnw -pl gateway verify`.
5. **Front** (`./build-openapi.sh`, `npm run api`, six messages, specs) puis skill
   `angular-fix-i18n` après le dernier texte — vérification : `./verify.sh --frontend --quick`.
6. **Test de fumée** (`deploy/smoke.sh`) — skill : aucun — vérification : `bash -n deploy/smoke.sh` ;
   pile de dev avec gateway et API de cette branche : `deploy/smoke.sh https://host.docker.internal -k`
   → `SMOKE OK`.
7. **CLAUDE.md** — skill : `sdlc-evals` (`/sdlc-evals run`, rapport pour la PR) — vérification :
   rapport des évals sans régression.
8. **Preuve complète** : `./verify.sh` → `VERIFY OK`, lignes automatisables de *Preuve*, agent
   `verifier`.

## Risques

- Le résolveur commun avale une `AccessDeniedException` ou une `AuthenticationException` (une `401`
  ou `403` deviendrait `500`) → il les ignore ; les tests d'accès existants de hururaa-api (SEC-7)
  et `ApiProblemsTest` / `GatewayProblemsTest` le prouvent.
- Il précède les `@ExceptionHandler` de `HururaaExceptionHandler` (les `404` métier perdraient leur
  type) → inséré juste avant `DefaultHandlerExceptionResolver`, dans la liste du composite ; CA-10
  (`APPLICATION_NOT_FOUND` inchangé) le vérifie.
- Flux SSE (`text/event-stream`, réponse engagée, délai de l'émetteur) → réponses engagées et
  `AsyncRequestNotUsableException` ignorées ; `SseEmitterRegistryTest` et CA-1 restent verts.
- La chaîne resource server de la gateway passe en `permitAll` → seul `/actuator/**` reste
  authentifié (le port de management, décision 0004) ; sur le port principal, `/actuator/**` répond
  `401` plutôt que `404` (aucune route de la SPA). Test : `/actuator/info` anonyme → `401`.
- `/error` ouvert dans hururaa-api → il ne rend qu'un problème générique tiré des attributs de la
  requête ; appelé directement, `404` `not-found`.
- Oubli du renommage de 9.5.2 → `/me` répondrait `401` (régression de 0002) → `InvalidSessionTest`
  et contre-épreuve de l'étape 1 ; `smoke.sh` après déploiement.
- Sans nouvelle session, un cookie périmé revient sur chaque requête jusqu'à `/me` → chacune reçoit
  `401`, ce qui déclenche déjà l'appel à `/me` dans le front ; aucun changement du front requis.
- Forme JSON d'un `ProblemDetail` écrit hors de Spring MVC (mixin Jackson) → tests sur `$.type`,
  `$.status`, `$.parameters` au niveau racine.
- Les applications des directions (PA-1) : aucun starter modifié ; le module n'est pas
  auto-configuré.
- `CLAUDE.md` modifié → évals obligatoires (étape 7).

## Preuve

| Critère | Comment | Test, commande ou vérification |
| --- | --- | --- |
| CA-1 | test automatisé | `GatewayProblemsTest` : `/bff/events/dsi` sans session → `401` `unauthenticated`, ni URL ni `Location` ; `smoke.sh` |
| CA-2 | tests automatisés + fumée | `ApiProblemsTest` (anonyme → `401`, `WWW-Authenticate: Bearer`) ; `RestfulOAuth2FilterTest` ; `smoke.sh` (`/bff/api/v1/me/delegations` sans session, relayé) |
| CA-3 | test automatisé + contre-épreuve | `InvalidSessionTest` (`/me` → `200` anonyme ; `/bff/events/dsi` → `401` sans `Location` ni nouvelle session) ; échoue sans `pass-through-patterns` |
| CA-4 | test automatisé + fumée | `GatewayProblemsTest` : `/inconnue` sans et avec session → `404` `not-found` ; `smoke.sh` |
| CA-5 | tests automatisés | `GatewayProblemsTest` (non-membre sur `/bff/events/daf`, `POST /logout` sans CSRF → `403`) ; `ApiProblemsTest` (règle refusée → `403`) |
| CA-6 | tests automatisés | `ApiProblemsTest` (`400`, `405` `invalid-request`) ; tests du résolveur |
| CA-7 | tests automatisés | `GatewayProblemsTest` (`IllegalStateException` de `SseController` sans session → `500` `server-error`, `detail` générique) ; test du résolveur (journalisé, non envoyé) |
| CA-8 | tests automatisés + manuel | `ApiUnavailableTest` (`503` `service-unavailable`) ; `http-error.interceptor.spec.ts` (message dédié) ; dev : API arrêtée, ouvrir une application → bannière « Service momentanément indisponible… » |
| CA-9 | test automatisé | `session-expired.interceptor.spec.ts` / `http-error.interceptor.spec.ts` : `401` typé → état déconnecté et « Session expirée… » |
| CA-10 | tests automatisés | `GatewayProblemsTest` (stub `APPLICATION_NOT_FOUND` relayé tel quel) ; `ApiProblemsTest` |
| CA-11 | commande | `./verify.sh` → `VERIFY OK` |

## Écarts

<!-- rempli pendant l'implémentation : date — quoi — pourquoi — effet sur les fichiers ou la preuve -->

- 2026-10-14 — Étape 2 : `ProblemResponses` n'est pas un `@Component` mais un `@Bean` de
  `ProblemWebMvcConfiguration` — les tranches `@WebMvcTest` ne chargent pas les composants simples
  (contexte en échec dans les tests des contrôleurs). Aucun effet sur la preuve.
- 2026-10-14 — Étape 3 : le résolveur commun type aussi `HttpMessageNotReadableException` et
  `TypeMismatchException` (`400` `invalid-request`) : ce ne sont pas des `ErrorResponse`, et
  `DefaultHandlerExceptionResolver` les aurait rendues sans corps. Test ajouté au résolveur.
- 2026-10-14 — Étape 4 : `invalid-session.status` retiré de `application.yml` de la gateway — sans
  effet une fois la stratégie remplacée par celle de Hurura'a. `InvalidSessionSafeguardTest` vérifie
  désormais le problème de cette stratégie (plus de `Location`).
- 2026-10-14 — Étape 4 : CA-7 prouvé par un contrôleur de test qui échoue
  (`GatewayProblemsTest.FailingController`) et non par `SseController` sans session : en MockMvc,
  le support de test de Spring Security ouvre une session. Pour la même raison, « aucune session
  ouverte » (CA-3) se prouve sur la stratégie elle-même : nouveau fichier
  `backend/gateway/src/test/java/pf/hururaa/security/ProblemSecurityConfigurationTest.java`.
- 2026-10-14 — Étape 4 : un `POST` **sans** jeton CSRF répond `401` `unauthenticated`, pas `403` :
  `CsrfConfigurer` confie `MissingCsrfTokenException` à la stratégie de session invalide. Décision du
  PO : garder ce comportement de Spring → **spec v2** (EX-5, CA-5), à approuver avant de reprendre
  l'étape 4. Preuve de CA-5 : jeton invalide → `403`, jeton absent → `401` (`GatewayProblemsTest`).
- 2026-10-14 — Étape 5 : `HururaaErrorController` annoté `@Hidden` — springdoc le publiait dans les
  deux specs OpenAPI (un service `hururaaErrorController` généré dans `@api/*`). Specs et clients
  régénérés, sans `/error`.
