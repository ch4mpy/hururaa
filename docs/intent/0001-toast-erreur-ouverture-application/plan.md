# Plan : toast d'erreur à l'ouverture de l'application

- Spec : [v2](spec.md)
- Statut : approuvé
- Branche : `fix/0001-toast-erreur-ouverture-application`
- Approuvé : 2026-10-12, ch4mpy (développeur et tech lead, risque élevé)

## Contexte

À l'ouverture, un cookie de session que la gateway ne connaît plus déclenche la stratégie « session
invalide » de spring-addons-starter-oidc 9.5.0 : `302` par défaut (`invalid-session.status: FOUND`)
vers un `Location` faux (client-uri + chemin **sans** le context path `/gateway`). La SPA suit la
redirection, reçoit du HTML et affiche « Requête refusée par le serveur ». Vérifié en lisant le jar
9.5.0 (`SpringAddonsInvalidSessionStrategy`, `SpringAddonsOidcClientProperties$InvalidSessionProperties`).

Correction par configuration seule (`invalid-session.status: UNAUTHORIZED`, EX-7) ; `/me` répond
alors `401` une fois, que la SPA traite comme l'anonyme (spec v2). Les améliorations de spring-addons
(route permit-all qui continue avec une nouvelle session, `Location` qui garde le context path)
suivront par une nouvelle intention.

## Fichiers

| Fichier | Changement | Pour |
| --- | --- | --- |
| `docs/intent/0001-…/plan.md` | nouveau : ce plan | — |
| `frontend/angular/projects/hururaa/src/app/core/http-error.interceptor.ts` | modifié : message « réponse inattendue » pour une réponse 2xx illisible (`http.error.unexpected`) | EX-4, CA-4 |
| `…/core/http-error.interceptor.spec.ts` | modifié : cas 2xx illisible ; cas 401 inchangé | CA-4 |
| `…/core/session-expired.interceptor.ts` | nouveau : sur une `401` d'une requête autre que la sonde `/me`, rafraîchit l'utilisateur (`UserService` obtenu tard par `Injector`, pas de cycle d'injection) | EX-6, CA-7 |
| `…/core/session-expired.interceptor.spec.ts` | nouveau | CA-7 |
| `…/core/user.service.ts` | modifié : `getMe` avec un `HttpContext` (`SKIP_ERROR_BANNER` + jeton « sonde de session ») ; `401` → anonyme sans bannière | EX-1, CA-1 |
| `…/core/user.service.spec.ts` | modifié : `401` sur `/me` → anonyme, pas de bannière | CA-1 |
| `…/app.config.ts` | modifié : enregistre `sessionExpiredInterceptor` après `httpErrorInterceptor` | EX-6 |
| `…/src/locale/messages.xlf`, `messages.en-US.xlf` | modifiés par le skill `angular-fix-i18n` : `http.error.unexpected` | EX-4, UX-1 |
| `backend/gateway/src/main/resources/application.yml` | modifié : `com.c4-soft.springaddons.oidc.client.invalid-session.status: UNAUTHORIZED` (section commune, donc aussi le profil `demo`) | EX-1, EX-2, EX-7, CA-8 |
| `backend/gateway/src/test/java/pf/hururaa/security/InvalidSessionTest.java` | nouveau : `@SpringBootTest` + MockMvc, identifiant de session inconnu → `401` sans `302` sur `/me` et `/bff/**` ; sans cookie → `200` / `401` | CA-1, CA-2 |

## Ordre de travail

1. Message « réponse inattendue » (intercepteur + spec) — skill : aucun — vérification :
   `cd frontend/angular && npm run test:ci && npm run lint`
2. Traductions — skill : `angular-fix-i18n` — vérification : `cd frontend/angular && npm run build`
3. `/me` en sonde de session et `401` → anonyme sans bannière (`UserService` + spec) — skill : aucun —
   vérification : `npm run test:ci && npm run lint`
4. `sessionExpiredInterceptor` (+ spec, enregistrement dans `app.config.ts`) — skill : aucun —
   vérification : `npm run test:ci && npm run lint`
5. Gateway : propriété `invalid-session.status: UNAUTHORIZED` et `InvalidSessionTest` — skill :
   aucun — vérification : `cd backend && ./mvnw -pl gateway verify`
6. Preuve complète + agent `verifier` (environnement de dev démarré, gateway relancée).

## Risques

- Le test `@SpringBootTest` de la gateway, le premier du module, exige ses dépendances au
  démarrage (découverte OIDC de Keycloak, RabbitMQ) → réutiliser les mappings WireMock de
  `src/test/resources/wiremock/keycloak` (`wiremock-spring-boot` est déjà une dépendance) ; si le
  contexte ne démarre pas sans infrastructure, écart : preuve CA-1/CA-2 par `curl` en dev.
- La connexion démarre par un appel XHR (`startLoginWithHururaabff`) : avec un cookie périmé, il
  recevrait `401` → à l'ouverture, `/me` a déjà remplacé la session ; CA-5 le vérifie (connexion
  après redémarrage de la gateway).
- Boucle `401` → rafraîchissement → `401` : le rafraîchissement ne passe que par `/me`, que
  l'intercepteur ignore, et la gateway a ouvert une nouvelle session.
- Cycle d'injection (`UserService` → `HttpClient` → intercepteur → `UserService`) : `Injector`
  injecté au début de l'intercepteur, `UserService` obtenu seulement dans `catchError`.
- Le `Location` faux reste dans l'en-tête de la `401` : sans effet (une `401` n'est pas suivie) ;
  corrigé par l'intention spring-addons.

## Preuve

| Critère | Comment | Test, commande ou vérification |
| --- | --- | --- |
| CA-1 | test automatisé + manuel | `InvalidSessionTest` (`/me` + session inconnue → `401`, pas de `302`) ; `user.service.spec.ts` (`401` → anonyme, pas de bannière) ; dev : se connecter, redémarrer la gateway, recharger `/fr/` → aucun toast, accueil anonyme |
| CA-2 | test automatisé | `InvalidSessionTest` (`/bff/api/v1/me/delegations` + session inconnue → `401`) |
| CA-3 | manuel | gateway arrêtée, ouvrir `/fr/` → « Erreur du serveur, veuillez réessayer plus tard » |
| CA-4 | test automatisé | `http-error.interceptor.spec.ts` (réponse 2xx illisible → message `http.error.unexpected`) |
| CA-5 | manuel | `dpam.manager` : connexion, directions affichées, événement reçu (modifier un groupe dans un autre onglet), déconnexion ; déconnexion depuis la console Keycloak → session de la gateway fermée ; connexion juste après un redémarrage de la gateway |
| CA-6 | manuel, après déploiement (étape 5) | démo : se connecter, fermer et rouvrir le navigateur, ouvrir l'application → aucun toast |
| CA-7 | test automatisé + manuel | `session-expired.interceptor.spec.ts` (`401` hors `/me` → `refresh()`, `401` sur `/me` → rien) ; dev : connecté, redémarrer la gateway, cliquer un menu → « Session expirée… » puis en-tête avec *Connexion*, menus d'administration masqués |
| CA-8 | commande | `grep -n "invalid-session" backend/gateway/src/main/resources/application.yml` : seule la propriété spring-addons, aucun code |

## Écarts

<!-- rempli pendant l'implémentation : date — quoi — pourquoi — effet sur les fichiers ou la preuve -->

- 2026-10-12 — Étape 3 : `/me` ne porte pas `SKIP_ERROR_BANNER` mais un jeton `SESSION_PROBE`,
  déclaré dans `http-error.interceptor.ts` ; l'intercepteur ne tait que la `401` d'une sonde. —
  `SKIP_ERROR_BANNER` aurait aussi tu « Erreur du serveur… » gateway arrêtée (CA-3). — Fichiers :
  `http-error.interceptor.ts` et sa spec sont aussi modifiés à l'étape 3 (déjà listés) ; preuve
  inchangée, avec un cas de plus dans `http-error.interceptor.spec.ts` (`502` sur la sonde → message).
- 2026-10-12 — Étape 6 (preuve CA-7 en dev) : six appels en `401` simultanés ont relu `/me` cinq
  fois (l'utilisateur ne devient anonyme qu'à la première réponse). — `UserService.sessionExpired()`
  relit `/me` une seule fois par rafale ; l'intercepteur l'appelle. — Fichiers déjà listés
  (`user.service.ts`, `session-expired.interceptor.ts` et sa spec) ; preuve CA-7 complétée d'un cas
  « rafale de 401 → un seul `/me` ».
