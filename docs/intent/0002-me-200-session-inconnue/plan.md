# Plan : /me répond 200 avec un cookie de session inconnu (0002)

- Intention : [0002](intent.md) (évolution, pas de spec : décision du PO)
- Statut : approuvé
- Branche : `0002-me-200-session-inconnue` (PR #25, une branche par intention)
- Approuvé : 2026-10-13, ch4mpy (développeur et tech lead : risque élevé, authentification)

## Contexte

Depuis 0001, `invalid-session.status: UNAUTHORIZED` (spring-addons-starter-oidc 9.5.0) fait
répondre `401` à `/gateway/me` avec un cookie de session inconnu. La **9.5.1** (spring-addons#313,
suite de #312 ; publiée sur Maven Central, `<release>9.5.1`) apporte, vérifié dans son jar et ses
sources :

- `invalid-session.permit-all-pass-through` (**`true` par défaut**) :
  `InvalidSessionPermitAllPassThroughFilter`, avant le `SessionManagementFilter` ; sur une route
  `permit-all` avec une session invalide, il ouvre une nouvelle session et la requête continue comme
  sans cookie ;
- un `Location` par défaut qui garde le context path.

Réponse à la question ouverte de l'intention : **9.5.1 couvre tout le besoin sans code propre à
Hurura'a** (version et configuration seulement).

Conséquence à connaître : dans la gateway, `/bff/**` est **aussi** `permit-all`. Une requête
`/bff/api/v1/**` avec un cookie inconnu continue donc en anonyme, sans jeton à relayer, et c'est
**hururaa-api** qui répond `401` (au lieu de la stratégie « session invalide »). Le comportement
observable est le même que livré par 0001 : `401`, pas de redirection, état déconnecté dans le front.
`/bff/events/{tenant}` répond `401` par la règle d'accès (`@tpe.isMember`). `status: UNAUTHORIZED`
reste utile pour la seule route non `permit-all` du client, `/logout`.

Le traitement « sonde de session » du front est **gardé** (décision du PO) : aucun changement dans
le front.

## Fichiers

| Fichier | Changement | Pour |
| --- | --- | --- |
| `docs/intent/0002-…/plan.md` | nouveau : ce plan | — |
| `docs/intent/0002-…/intent.md`, `docs/intent/README.md` | statut *planifiée* (tenue de l'index) | — |
| `backend/gateway/src/test/java/pf/hururaa/security/InvalidSessionTest.java` | modifié : `/me` + session inconnue → `200` anonyme et nouvelle session ; `/bff/**` + session inconnue → `401` sans redirection (corps de hururaa-api, plus celui de la stratégie) ; nouveau cas : `/logout` + session inconnue → `401` de la stratégie ; Javadoc (0002) | résultat attendu |
| `backend/pom.xml` | `com.c4-soft.springaddons.version` 9.5.0 → **9.5.1** (vérifiée sur Maven Central) | résultat attendu |
| `backend/gateway/src/main/resources/application.yml` | `invalid-session.permit-all-pass-through: true` explicite (le comportement voulu ne dépend pas d'une valeur par défaut) ; commentaire mis à jour (0001 → 0002) | résultat attendu |
| `deploy/smoke.sh` | contrôle « `/me`, session inconnue » : `200` anonyme (au lieu de `401`) ; commentaire | critère de réussite |

## Ordre de travail

1. **Tests d'abord** : `InvalidSessionTest` décrit le comportement visé ; il doit **échouer** en
   9.5.0 sur `/me` (`401` au lieu de `200`) et passer sur les autres cas — skill : aucun —
   vérification : `cd backend && ./mvnw -pl gateway test -Dtest=InvalidSessionTest` (JDK 26) :
   échec attendu sur le seul cas `/me` + session inconnue, constaté et noté dans le commit
   `test(0002): …`.
2. **spring-addons 9.5.1 + propriété explicite** (`pom.xml`, `application.yml`) — skill : aucun —
   vérification : `./mvnw -pl gateway verify` vert ; **contre-épreuve** :
   `SPRING_APPLICATION_JSON='{"com.c4-soft.springaddons.oidc.client.invalid-session.permit-all-pass-through":false}'`
   → le cas `/me` échoue (`401`).
3. **Test de fumée** (`deploy/smoke.sh`) — skill : aucun — vérification : `bash -n` ; en dev, gateway
   relancée sur cette branche : `deploy/smoke.sh https://host.docker.internal -k` → `SMOKE OK`.
4. **Preuve complète** : `./verify.sh` → `VERIFY OK`, puis agent `verifier`.

## Risques

- 9.5.1 change d'autres comportements (hururaa-api, autre utilisateur de spring-addons) → `./verify.sh`
  complet (232 tests de l'API) et notes de version (#313) relues : seul l'invalid-session change.
- `/bff/**` en `permit-all` : une requête relayée sans jeton atteint hururaa-api → elle répond déjà
  `401` sans jeton (`InvalidSessionTest` via WireMock ; démo par `smoke.sh` « API through the BFF »).
- La démo affiche encore 9.5.0 tant que la PR n'est pas fusionnée et déployée → `smoke.sh` y
  échouerait si lancé avant : le contrôle n'est pris en compte qu'après le déploiement (étape 5 ;
  la métrique `demo_smoke_ko` de l'étape 6 pourrait signaler `1` entre la fusion et la fin du
  déploiement, ce qui est normal et court).

## Preuve

| Critère (résultat attendu de l'intention) | Comment | Test ou commande |
| --- | --- | --- |
| `/me` + cookie inconnu → `200` anonyme, sans redirection | test automatisé + contre-épreuve | `InvalidSessionTest` ; échoue avec `permit-all-pass-through: false` |
| Autres routes : `401` sans redirection | test automatisé | `InvalidSessionTest` (`/bff/**`, `/logout`) |
| Front : état déconnecté sur une `401`, sonde de session gardée | tests existants inchangés | `session-expired.interceptor.spec.ts`, `user.service.spec.ts` (via `./verify.sh`) |
| Rien de 0001 perdu (aucun toast, message gateway arrêtée) | tests existants + manuel | `./verify.sh` ; dev : cookie périmé → aucun toast (navigateur) |
| Réussite : test de fumée de la démo | après déploiement (étape 5) | `deploy/smoke.sh` → `SMOKE OK` avec `/me` → `200` |

## Écarts

<!-- rempli pendant l'implémentation -->
