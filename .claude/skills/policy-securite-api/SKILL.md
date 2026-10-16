---
name: policy-securite-api
description: Hurura'a API security policy (rules SEC-n). Use whenever creating or modifying a REST endpoint, an access rule (@PreAuthorize), a path-variable converter, an error response or a controller test in the backend, and when writing a spec, plan or review that touches them.
---

# Politique : sécurité de l'API

- Responsable : ch4mpy
- Périmètre : les endpoints de `hururaa-api` et de la gateway, leurs règles d'accès, leurs erreurs, leurs tests d'accès.
- Sources : `CLAUDE.md` (*Security and the delegation chain*, *Error responses*), `README.md` §2.

Citer les règles par leur identifiant (`SEC-n`) dans les specs, les plans et les revues. Une
règle ne change que par une PR sur ce fichier, revue par son responsable ; une exception exige une
décision (`docs/decisions/`).

## Règles

1. **SEC-1 Une règle d'accès par endpoint.** Chaque méthode `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping` ou `@DeleteMapping` porte un `@PreAuthorize` ; `isAuthenticated()` seulement pour ce qui ne concerne que l'utilisateur courant ou une liste filtrée selon lui. *Pourquoi :* rien n'est ouvert par oubli.
2. **SEC-2 Des règles écrites sur les variables de chemin résolues.** Les règles appellent les méthodes des objets résolus (`#direction.hasDelegate(authentication)`, `#group.isManageableBy(authentication)`, `#application.isManagedBy(authentication) and #application.direction == #direction.alias`…), sans bean de règle. Un nouveau niveau de délégation se vérifie par une méthode de l'objet résolu prenant l'`Authentication` (voir `HururaaPermission.heldIn`). *Pourquoi :* les règles se lisent sur l'endpoint et se testent avec la vraie résolution.
3. **SEC-3 Rôles de Hurura'a par constantes.** Un administrateur Hurura'a se reconnaît à `hasAuthority('" + HururaaPermission.Names.DIRECTION_ADMIN + "')` ; les noms de rôles sont des constantes de `HururaaPermission.Names`, jamais des littéraux. *Pourquoi :* un renommage casse la compilation, pas la sécurité.
4. **SEC-4 404 avant toute règle d'accès.** `{direction}`, `{group}` et `{applicationId}` sont résolus par les convertisseurs (`DirectionWebMvcConfiguration`, `ApplicationWebMvcConfiguration`), qui répondent `DIRECTION_NOT_FOUND`, `GROUP_NOT_FOUND` ou `APPLICATION_NOT_FOUND` avant l'évaluation des règles. *Pourquoi :* une règle ne s'évalue que sur un objet qui existe.
5. **SEC-5 Noms réservés figés.** Aucun endpoint ne crée, ne supprime ni ne change les rôles qu'attribue un groupe dont le nom commence par `hururaa.`, ne crée ni ne supprime un rôle de Hurura'a (client `hururaa-api`), n'attribue un rôle `hururaa.*` à un autre groupe, ni n'accepte un préfixe d'application réservé (`hururaa`, `admin`, `direction`, `product-owners`, `manage`) : refus `RESERVED_NAME`, même pour un administrateur Hurura'a. Les groupes réservés sont listés comme les autres, et leurs **membres** se changent par l'API des groupes, avec la règle de tout groupe (`#group.isManageableBy(authentication)`, co-gestion des `hururaa.<préfixe>.product-owners` comprise) ; retirer le dernier administrateur Hurura'a est refusé (`LAST_HURURAA_ADMIN`). *Pourquoi :* la délégation est faite de groupes Keycloak gérés comme ceux de toute application, mais ce que ces groupes attribuent ne change pas (décision 0008).
6. **SEC-6 Erreurs en problèmes RFC 9457.** Toute erreur renvoyée est un type de l'énumération fermée `ProblemType` (URN `urn:hururaa:problem:<slug>`), levé par la `HururaaProblemException` déclarée sur l'endpoint et le service (`rollbackFor` sur les transactions d'écriture, `.unchecked()` dans les convertisseurs). Le détail d'une erreur 5xx est journalisé, jamais envoyé. *Pourquoi :* le client traduit chaque type ; rien d'interne ne fuit.
7. **SEC-7 Accès testé avec les vraies règles.** Chaque endpoint a ses tests `@WebMvcTest` (skill `create-spring-rest-controller-test`) : anonyme refusé, chaque niveau autorisé accepté, un niveau insuffisant refusé, via `HururaaFixtures.WebMvcTestConfiguration` (sécurité de méthode, convertisseur JWT et vrai `DelegationResolver`) et les jetons de `src/test/resources/jwt/`. *Pourquoi :* un test qui moque la règle ne prouve rien.

## Vérifier

- SEC-1 : pour chaque contrôleur, autant de `@PreAuthorize` que d'annotations de mapping :
  `for f in $(grep -rl @RestController backend/*/src/main/java); do echo "$(grep -cE '@(Get|Post|Put|Patch|Delete)Mapping' $f) $(grep -c @PreAuthorize $f) $f"; done`
- SEC-3 : aucun littéral de rôle de Hurura'a dans une règle : `grep -rn "PreAuthorize(\"[^\"]*'hururaa\." backend/*/src/main/java` ne renvoie rien.
- SEC-6 : aucun `ResponseStatusException` : `grep -rn ResponseStatusException backend/*/src/main/java` ne renvoie rien.
- SEC-7 : `./mvnw -pl hururaa-api test` ; chaque contrôleur a sa classe `*ControllerTest`.

## Exceptions

Aucune.
