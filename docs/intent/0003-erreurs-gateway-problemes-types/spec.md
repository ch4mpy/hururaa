# Spec : erreurs de la gateway en problèmes RFC 9457 typés

- Intention : [0003](intent.md)
- Statut : brouillon (v3 à approuver ; v1 et v2 approuvées)
- Version : 3
- Risque : élevé — le changement touche l'authentification (point d'entrée, session invalide) et
  les réponses des règles d'accès
- Politiques appliquées : policy-securite-api @229653c, policy-ux-i18n @229653c,
  policy-delegation @229653c, policy-tracabilite @229653c
- Approuvée : v1 et v2 le 2026-10-14, ch4mpy (PO) ; tech lead : ch4mpy, 2026-10-14 (PO et tech
  lead sont la même personne : accord consigné une seule fois)

## Résumé

Toute erreur que la SPA reçoit de la gateway ou de hururaa-api devient un problème RFC 9457 d'un
type de `ProblemType` : `401` sans session, accès refusé, route inconnue (`404`), requête rejetée
par le framework, erreur interne, API injoignable. Six types génériques rejoignent l'énumération ;
le front leur donne un message, dont un message dédié quand l'API est injoignable. La gateway passe
à spring-addons 9.5.2.

## Exigences

- EX-1 Une requête sans session sur une route protégée de la gateway (`/bff/events/{direction}`…)
  reçoit `401` `unauthenticated`, sans URL de connexion ni libellé de spring-addons dans le corps.
  (intention : résultat 1, problème « fuite »)
- EX-2 Un appel `/bff/api/v1/**` sans session, ou avec un jeton que hururaa-api refuse, reçoit
  `401` `unauthenticated`. (résultat 1)
- EX-3 Un cookie de session que la gateway ne connaît plus donne `200` anonyme sur `/me` (0002) et
  `401` `unauthenticated` ailleurs, sans redirection (0001). (contrainte « ne rien perdre »)
- EX-4 Une route que ni la gateway ni hururaa-api ne connaît répond, sans session, `401`
  `unauthenticated` (refus par défaut, EX-13) et, avec une session, `404` `not-found`. (résultat 2 ;
  v3)
- EX-5 Un accès refusé (règle `@PreAuthorize`, jeton CSRF invalide), de la gateway comme de
  hururaa-api, répond `403` `access-denied`. Un jeton CSRF **absent** garde le traitement de Spring
  Security, qui y voit une session expirée : `401` `unauthenticated`. (résultat 1 ; v2)
- EX-6 Une requête que le framework rejette (corps illisible, méthode ou type de contenu non
  supporté…) répond avec son statut `4xx` et le type `invalid-request`. (résultat 1)
- EX-7 Une exception non gérée répond `500` `server-error` ; son détail est journalisé, jamais
  envoyé. (résultat 1, SEC-6)
- EX-8 hururaa-api injoignable ou sans réponse : la gateway répond `503` `service-unavailable`.
  (résultat 1)
- EX-9 Le front affiche, pour chaque nouveau type, un message traduit (fr, en) ; pour
  `service-unavailable`, un message dédié. Une `401` garde l'effet de 0001 : état déconnecté.
  (résultats 3 et 4, UX-2)
- EX-10 Les problèmes typés de hururaa-api traversent la gateway inchangés. (résultat 1)
- EX-11 La gateway et hururaa-api utilisent spring-addons 9.5.2. (contrainte)
- EX-12 Un test automatisé prouve chaque cas EX-1 à EX-8 : statut, `application/problem+json`,
  type connu. (critère de réussite)
- EX-13 Ce que la gateway sert aux requêtes anonymes est **listé explicitement** dans sa
  configuration, en trois catégories commentées ; tout le reste exige une authentification (refus
  par défaut) :
  - les endpoints actuator publics : `health` et ses sondes, sur le port de management (décision
    0004) ;
  - les routes relayées vers un service en aval (`/bff/api/v1/**`) : leur contrôle d'accès est
    celui de ce service ;
  - les endpoints de la gateway qui fonctionnent sans session : `/me`, connexion (`/login/**`,
    `/oauth2/**`), déconnexion (`/logout`, sans effet sans session), back-channel logout, `/error`,
    `/v3/api-docs` (profil `openapi`).

  Les endpoints propres de la gateway non listés (`/bff/events/**`) exigent une session, en plus
  de leur `@PreAuthorize`. Un test vérifie que chaque route relayée de la configuration est dans
  la liste. (constat 6 de la revue de la PR #26, décision du PO et tech lead ; v3)
- EX-14 La gateway n'expose plus l'endpoint actuator `info`, qui ne servait rien (`{}`, aucun
  consommateur) : seul `health` reste exposé. (constat 6 ; v3)

## Hors périmètre

- Les erreurs du reverse proxy (nginx en dev, Caddy en démo) quand la gateway elle-même est
  arrêtée : le front garde son message générique (statut `502`/`0`). Typer celles-ci touche le
  déploiement : une autre intention si besoin.
- Les navigations du navigateur (connexion, retour de Keycloak, déconnexion) : pages, pas appels.
- Les erreurs du port de management (actuator) : ni la SPA ni les directions ne l'appellent ; la
  liste de ses endpoints publics, elle, est dans le périmètre (EX-13).
- Les applications des directions : starters inchangés (PA-1).
- Les listes explicites de hururaa-api : il liste déjà ses routes publiques ; EX-13 ne vise que la
  gateway (v3).

## Conception

### Comportement actuel

- Gateway, chaîne client de spring-addons (`/login/**`, `/oauth2/**`, `/logout/**`, `/bff/**`,
  `/me/**`, [application.yml](../../../backend/gateway/src/main/resources/application.yml)) :
  - sans session, une règle `@PreAuthorize` (`SseController`, `@tpe.isMember`) déclenche
    `SpringAddonsAuthenticationEntryPoint` : `401`, `Location` et `WWW-Authenticate` vers
    `/login`, corps texte « Unauthorized. Please authenticate at … » (lu dans la source 9.5.2) ;
  - un cookie inconnu sur une route hors pass-through déclenche `SpringAddonsInvalidSessionStrategy` :
    `401` (configuré), corps « Invalid session. Please authenticate. » (asserté par
    `InvalidSessionTest` et `InvalidSessionSafeguardTest`) ;
  - authentifié mais refusé : gestionnaire d'accès refusé par défaut de Spring Security (`403`,
    corps d'erreur de Spring Boot, pas un problème).
- Gateway, autres chemins : chaîne resource server de spring-addons (aucun endpoint à jeton
  derrière, seul `/v3/api-docs` y est ouvert) : `401` vide pour toute route inconnue (constaté).
- `/bff/api/v1/**` : `TokenRelay=` relaie sans jeton quand il n'y a pas de session ; hururaa-api
  répond `401` vide (point d'entrée du resource server). Une erreur de connexion vers l'API n'a
  pas de traitement : erreur Spring Boot de la gateway (non observé).
- hururaa-api : `HururaaExceptionHandler` type les erreurs métier ; les autres passent par le
  `ProblemDetailsExceptionHandler` de Spring Boot (`spring.mvc.problemdetails.enabled`), de type
  `about:blank`, que le front ne sait pas traduire.
- `ProblemType` vit dans hururaa-api ; la gateway n'y a pas accès.
- Front : `problemMessage` ne traduit que les types connus, sinon `httpErrorMessage` donne un
  message par classe de statut ; `sessionExpiredInterceptor` passe à l'état déconnecté sur toute
  `401` hors `/me`.
- spring-addons 9.5.2 (publiée le 2026-10-13, [PR #315](https://github.com/ch4mpy/spring-addons/pull/315)) :
  le point d'entrée garde le chemin de contexte ; `invalid-session.permit-all-pass-through` est
  **remplacé** par `invalid-session.pass-through-patterns`, vide par défaut. Le point d'entrée et
  la stratégie de session invalide restent des beans remplaçables
  (`DefaultAuthenticationEntryPointCondition`, `@ConditionalOnMissingBean`). Leurs propriétés ne
  règlent que le statut et le `Location` ; le corps texte est écrit en dur : seul un bean de la
  gateway peut répondre un problème typé.

### Comportement par persona

Identique pour tous les personas : une erreur s'affiche dans la bannière avec le message de son
type ; une `401` repasse l'interface en état déconnecté. Aucun droit ne change (règles d'accès
inchangées).

### API et contrats

Nouveaux types de `ProblemType` (`urn:hururaa:problem:<slug>`), sans paramètre :

| Constante | Slug | Statut | Émis par |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | `unauthenticated` | 401 | gateway (sans session, session inconnue, jeton CSRF absent), hururaa-api (jeton absent ou refusé) |
| `ACCESS_DENIED` | `access-denied` | 403 | gateway (règle, jeton CSRF invalide), hururaa-api |
| `NOT_FOUND` | `not-found` | 404 | gateway, hururaa-api (route inconnue) |
| `INVALID_REQUEST` | `invalid-request` | 4xx du framework (400 par défaut) | gateway, hururaa-api |
| `SERVER_ERROR` | `server-error` | 500 | gateway, hururaa-api |
| `SERVICE_UNAVAILABLE` | `service-unavailable` | 503 | gateway (hururaa-api injoignable, délai dépassé) |

- Corps : `type`, `title`, `status`, `detail` générique (jamais le message d'une exception),
  `instance`, `parameters` vide ; `Content-Type: application/problem+json`. Ni URL de connexion ni
  en-tête `Location` sur une `401`.
- `INVALID_REQUEST` est le seul type dont le statut varie (400, 405, 406, 413, 415…) : un type par
  statut multiplierait les messages sans aider l'utilisateur.
- Choix technique proposé : **déplacer `ProblemType` et `HururaaProblemDetail` dans un module
  commun** lu par la gateway et hururaa-api, sans auto-configuration. Une seule énumération fermée
  (SEC-6 tel qu'écrit), un seul registre exhaustif au front, généré de `@api/hururaa-api`.
  Alternative : une énumération propre à la gateway, et un front qui fusionne deux registres —
  contraire à la lettre de SEC-6, écartée.
- Sous `/bff/api/v1/**`, c'est hururaa-api qui décide : sans session, `401` avant `404` (la route
  de relais existe ; l'API n'évalue pas une route sans jeton).
- spring-addons 9.5.2 : `invalid-session.pass-through-patterns: /me` (→ PA-2).
- Listes d'accès anonyme de la gateway (EX-13), dans ses deux chaînes de filtres actuelles :
  `client.permit-all` (routes relayées, `/me`, connexion, déconnexion, back-channel logout) et
  `resourceserver.permit-all` (sondes `health`, `/error`, `/v3/api-docs`). Pas de règle « tout
  autoriser » : un chemin absent des listes reçoit le `401` de la chaîne qui le traite.
- OpenAPI : les types apparaissent dans `hururaa-api.openapi.json` ; `npm run api` les génère.

### Interface

Messages (`core/problem-messages.ts`) :

| Type | fr | en |
| --- | --- | --- |
| `unauthenticated` | Session expirée, veuillez vous reconnecter | Session expired, please log in again |
| `access-denied` | Accès refusé | Access denied |
| `not-found` | Ressource introuvable | Resource not found |
| `invalid-request` | Requête refusée par le serveur | Request rejected by the server |
| `server-error` | Erreur du serveur, veuillez réessayer plus tard | Server error, please try again later |
| `service-unavailable` | Service momentanément indisponible, réessayez dans quelques instants | Service temporarily unavailable, please try again in a moment |

Les cinq premiers reprennent les textes génériques actuels ; `httpErrorMessage` garde ses
messages par statut pour ce qui n'est pas typé (reverse proxy, réseau).

### Données

Aucune.

## Politiques appliquées

| Règle | Comment elle est respectée |
| --- | --- |
| SEC-1 | Aucun endpoint ajouté ; `SseController` garde sa règle. Les endpoints de la gateway hors des listes anonymes exigent en plus une authentification (EX-13). |
| SEC-4 | Inchangé : les `404` métier des convertisseurs restent prioritaires sur `NOT_FOUND`. |
| SEC-6 | Toute erreur reçue par la SPA est un type de l'unique `ProblemType` (EX-1 à EX-8) ; `detail` générique, 5xx journalisées (EX-7). |
| SEC-7 | Les tests d'accès gardent leurs statuts ; de nouveaux tests vérifient le type des corps (EX-12). |
| UX-1 | Six messages fr/en, `angular-fix-i18n` après le dernier texte. |
| UX-2 | Chaque nouveau type reçoit son message dans le registre exhaustif (EX-9). |
| UX-3 | Bannière inchangée pour les échecs. |
| DEL-3, DEL-8 | Aucune règle d'accès ni délégation modifiée ; une `401` ne retire aucun droit. |
| TRA-* | Non touchées : aucun changement de permissions. |

## Points d'attention

### PA-1 Applications des directions

- Enjeu : typer les `401`/`403` dans `common-security-starter` changerait aussi les réponses des
  applications des directions qui l'utilisent, avec des types `urn:hururaa:problem:*`.
- Options : A, gateway et hururaa-api seulement, starters inchangés / B, dans le starter, pour
  toutes les applications.
- Recommandation : A — l'espace `urn:hururaa:problem:` est celui de Hurura'a ; une direction qui le
  veut le demandera par une intention.
- Décide : PO
- Décision : A, gateway et hururaa-api seulement (PO, 2026-10-14)

### PA-2 Pass-through réduit à `/me`

- Enjeu : 9.5.2 remplace `permit-all-pass-through` (toutes les routes `permit-all`, dont
  `/bff/**`) par une liste. Avec `/me` seul, un cookie inconnu sur `/bff/**` ne passe plus en
  anonyme vers l'API mais reçoit `401` `unauthenticated` de la gateway : même effet pour
  l'utilisateur, plus de session orpheline par requête parallèle.
- Options : A, `/me` / B, `/me` et `/bff/**` (comportement de 0002).
- Recommandation : A — c'est ce que spring-addons#314 demandait ; le résultat visible ne change pas.
  Motif exact `/me` plutôt que `/me/**` : `MeController` n'a pas de sous-chemin, le plus étroit
  suffit.
- Décide : tech lead
- Décision : A, `/me` (tech lead, 2026-10-14)

### PA-3 Statut de l'API injoignable

- Enjeu : `503` (service indisponible) ou `502` (passerelle en erreur) pour `SERVICE_UNAVAILABLE`.
- Options : A, `503` pour tout échec vers l'API (connexion refusée, délai) / B, `502` connexion,
  `504` délai.
- Recommandation : A — un seul type, un seul message, le statut le plus parlant.
- Décide : tech lead
- Décision : A, `503` (tech lead, 2026-10-14)

## Critères d'acceptation

- CA-1 Étant donné aucune session, quand on ouvre `/gateway/bff/events/dsi`, alors `401`
  `application/problem+json` de type `unauthenticated`, sans URL dans le corps. (EX-1, EX-12)
- CA-2 Étant donné aucune session, quand on appelle `/gateway/bff/api/v1/me/delegations`, alors
  `401` `unauthenticated` ; de même sur hururaa-api appelée sans jeton. (EX-2, EX-12)
- CA-3 Étant donné un cookie de session inconnu, quand on appelle `/gateway/me`, alors `200`
  anonyme ; quand on appelle `/gateway/bff/events/dsi`, alors `401` `unauthenticated` sans
  `Location`. (EX-3)
- CA-4 Étant donné aucune session, quand on appelle `/gateway/inconnue`, alors `401`
  `unauthenticated` ; étant donné une session, alors `404` `not-found`. (EX-4, EX-12 ; v3)
- CA-5 Étant donné un utilisateur connecté non membre de `daf`, quand il ouvre
  `/gateway/bff/events/daf`, alors `403` `access-denied` ; étant donné un membre sans rôle, quand
  hururaa-api refuse une écriture par sa règle, alors `403` `access-denied` ; étant donné un `POST`
  avec un jeton CSRF invalide, alors `403` `access-denied` ; sans jeton CSRF, alors `401`
  `unauthenticated`. (EX-5, EX-12)
- CA-6 Étant donné un corps JSON illisible ou une méthode non supportée, quand on appelle
  hururaa-api, alors `400` (resp. `405`) `invalid-request`. (EX-6, EX-12)
- CA-7 Étant donné une exception non gérée, alors `500` `server-error`, `detail` générique, le
  message de l'exception dans les journaux seulement. (EX-7, EX-12)
- CA-8 Étant donné hururaa-api arrêtée, quand on appelle `/gateway/bff/api/v1/…` avec une session,
  alors `503` `service-unavailable`, et le front affiche « Service momentanément indisponible,
  réessayez dans quelques instants ». (EX-8, EX-9, EX-12)
- CA-9 Étant donné une `401` `unauthenticated` sur un appel de l'API, alors le front passe à
  l'état déconnecté et affiche « Session expirée, veuillez vous reconnecter ». (EX-9)
- CA-10 Étant donné `APPLICATION_NOT_FOUND` renvoyé par hururaa-api, quand il traverse la
  gateway, alors son corps arrive inchangé. (EX-10)
- CA-11 `./verify.sh` finit par `VERIFY OK` avec spring-addons 9.5.2. (EX-11)
- CA-12 Étant donné la configuration de la gateway, alors chaque prédicat `Path=` de ses routes
  relayées figure dans `client.permit-all`, et un test échoue si une route relayée en est absente.
  (EX-13)
- CA-13 Étant donné aucune session, quand on appelle `/me`, la connexion, `/error` ou une sonde
  `health`, alors ils répondent comme avant ; quand on appelle `POST /logout` avec un jeton CSRF,
  alors `202`, sans effet ; quand on ouvre `/bff/events/dsi`, alors `401` `unauthenticated`.
  (EX-13)
- CA-14 Étant donné le port de management de la gateway, quand on appelle `/actuator/info`, alors
  il n'existe plus (`404`) ; `/actuator/health` répond toujours sans jeton. (EX-14)

## Questions de l'intention

- Où vit la correction : la gateway, spring-addons, ou les deux ? → Dans la gateway et
  hururaa-api seulement. spring-addons 9.5.2 garde le chemin de contexte et apporte
  `pass-through-patterns`, mais son point d'entrée écrit toujours un corps texte ; il reste un
  bean remplaçable (`DefaultAuthenticationEntryPointCondition`), de même que la stratégie de
  session invalide (`@ConditionalOnMissingBean`) : aucune évolution de spring-addons n'est
  nécessaire (source de la 9.5.2). Portée : gateway et hururaa-api (PA-1) ; pass-through : `/me` (PA-2).

## Historique

- v1 — 2026-10-13 — première version ; décisions PA-1 à PA-3 le 2026-10-14 (motif `/me` au lieu
  de `/me/**` pour PA-2), approuvée le 2026-10-14
- v2 — 2026-10-14 — EX-5, CA-5 et le tableau des types : un jeton CSRF absent répond `401`
  `unauthenticated`, seul un jeton invalide répond `403` `access-denied`. Constaté au build :
  Spring Security (`CsrfConfigurer`) confie un jeton absent à la stratégie de session invalide, pas
  au gestionnaire d'accès refusé ; le PO garde ce comportement (2026-10-14) plutôt que de le
  contourner selon l'état de la session. Approuvée le 2026-10-14.
- v3 — 2026-10-14 — Constat 6 de la revue de la PR #26 (la chaîne resource server de la gateway
  passait en `permitAll`), décision du PO et tech lead :
  - nouvelle EX-13 : listes explicites de ce que la gateway sert aux anonymes (actuator public,
    routes relayées, endpoints propres), refus par défaut, test de cohérence routes / `permit-all` ;
  - EX-4 et CA-4 : une route inconnue répond `401` sans session (conséquence du refus par défaut),
    `404` avec ;
  - nouvelle EX-14 : `info` retiré de l'exposition actuator de la gateway ;
  - CA-12 à CA-14 ; hururaa-api hors périmètre de EX-13.
