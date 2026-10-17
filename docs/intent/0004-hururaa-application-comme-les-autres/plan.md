# Plan : Hurura'a, une application comme les autres (0004)

- Spec : [v1](spec.md) (approuvée le 2026-10-15, risque élevé : règles d'accès, noms réservés, Liquibase)
- Statut : approuvé
- Branche : `0004-hururaa-application-comme-les-autres` (PR #32)
- Approuvé : 2026-10-15, ch4mpy (développeur et tech lead : risque élevé, règles d'accès, noms réservés, Liquibase)

## Contexte

La spec rend Hurura'a visible et gérable comme toute application. Il s'agit de ses groupes
`hururaa.admins` et `hururaa.<préfixe>.product-owners`, et de ses rôles, dans toutes les
directions. Les membres se gèrent par l'API des groupes (règle générale, plus la co-gestion des
gestionnaires d'application). Les écrans et endpoints de désignation disparaissent. Les politiques
SEC-5, DEL-1, DEL-4, DEL-5 et DEL-6 sont réécrites, avec une décision 0008.

Constats de lecture qui orientent le plan :

- **Rattachement d'un groupe.** `Application.ownsGroup` est un test de préfixe : `hururaa.*`
  appartient déjà à Hurura'a, mais seulement là où on la cherche. Le résolveur
  (`DelegationResolver.group`, `:57-69`), `GroupController.getGroups` (`:131`),
  `DirectionController.getDirectionUserGroups` (`:340`) et `MemberSearchService.matchingGroups`
  (`:86`) ne passent que les applications de la direction (`findByDirectionOrderByNameAsc`). Il
  suffit qu'ils voient Hurura'a dans toute direction.
- **Règles de direction.** `#application.direction == #direction.alias` exclut Hurura'a hors de la
  DSI. C'est le cas des groupes, rôles et historique d'application, et du filtre `applicationId`
  de la recherche de membres.
- **Délégués de la direction.** `DelegatedDirection.isManagedBy` ne lit que les rôles détenus dans
  la direction. Un gestionnaire de Hurura'a (rôle détenu dans la DSI) n'y est donc pas un délégué.
- **Écritures des groupes réservés.** Elles sont toutes refusées par `requireNotReserved`
  (`GroupController:195, 256, 321, 355, 421, 453`). Les lectures par nom ne le sont pas.
- **Journal.** Les désignations (`directionAdmin*`, `applicationManager*`) n'ont pas de
  `group_name` : l'historique d'un groupe de Hurura'a ne les montre pas. Choix du développeur : les
  écrire avec le groupe.
- **Enregistrement.** Aucun mécanisme n'enregistre Hurura'a hors du changeset `dev`. Choix du
  développeur : un changeset Liquibase sans contexte, paramétré par `uaa.platform-organization`.
- **Lecture de Hurura'a hors de la DSI.** Choix du développeur : elle est ouverte à tout délégué de
  la direction (`#direction.hasDelegate`). Un gestionnaire d'Escales voit donc Hurura'a dans la
  DPAM et y co-gère `hururaa.escales.product-owners`, ce qui rend CA-10 atteignable.
- **Frontend.**
  - `group-detail` affiche toutes ses actions sans vérifier les droits, et rien ne reflète
    `isManageableBy` dans `DelegationsService`.
  - La vue d'ensemble de la direction n'est montrée qu'aux administrateurs.
  - Les désignations ne sont utilisées que dans `direction-overview.ts:113,122` et
    `application-managers.ts:88,100`.
- **Données de test.** `HururaaFixtures` n'a pas d'application Hurura'a (id 1 dans les données de
  dev).

## Fichiers

| Fichier | Changement | Pour |
| --- | --- | --- |
| `docs/intent/0004-…/plan.md`, `…/intent.md`, `docs/intent/README.md` | plan ; statut *planifiée* puis index | — |
| `.claude/skills/policy-securite-api/SKILL.md` | SEC-5 réécrite : rôles et groupes `hururaa.*` intouchables en tant que tels (création, suppression, rôles attribués, rôles de Hurura'a) ; groupes réservés listés ; membres selon la règle générale des groupes et la co-gestion | EX-11, CA-12 |
| `.claude/skills/policy-delegation/SKILL.md` | DEL-1 (désignation des administrateurs par les administrateurs de direction et les gestionnaires de Hurura'a), DEL-4 (les groupes `hururaa.*` de toute direction appartiennent à Hurura'a), DEL-5 (membres par l'API des groupes, co-gestion), DEL-6 (Hurura'a, seule application transverse) ; sources : décision 0008 | EX-11, CA-12 |
| `docs/decisions/0008-hururaa-application-transverse.md`, `docs/decisions/README.md` | nouvelle décision qui amende 0007 : Hurura'a transverse, groupes gérés par l'API des groupes, écart accepté avec la contrainte « pas d'élévation » de 0004 (PA-3), garde-fou DSI seule, désignation supprimée | EX-11, CA-12 |
| `backend/hururaa-problem/…/ProblemType.java` | + `LAST_HURURAA_ADMIN(CONFLICT, "last-hururaa-admin")`, Javadoc des paramètres (`direction`, `userId`) | EX-6, CA-7 |
| `backend/hururaa-api/…/uaa/DelegationGroups.java` | + `HURURAA_CLIENT_PREFIX` ; + `isAdmins(name)`, `productOwnersPrefix(name)` (le préfixe d'un `hururaa.<p>.product-owners`) ; Javadoc | EX-2, EX-5 |
| `…/application/domain/Application.java` | + `isHururaa()`, `isAvailableIn(String direction)` (sa direction, ou toute direction pour Hurura'a), `isReadableBy(DelegatedDirection, Authentication)` (Hurura'a : `direction.hasDelegate` ; sinon administrateur de la direction ou gestionnaire), `isEditableIn(…)` inchangé côté règle | EX-2, EX-4, SEC-2 |
| `…/application/jpa/ApplicationRepository.java` | + `findAvailableIn(direction)` (`@Query` : direction = :d ou préfixe `hururaa`, tri par nom), qui remplace `findByDirectionOrderByNameAsc` chez les appelants | EX-2, EX-3, EX-4 |
| `…/direction/domain/DelegatedDirection.java` | `isManagedBy` : + gestionnaire de Hurura'a (rôle détenu dans `platformOrganization`) | EX-5 (persona) |
| `…/direction/domain/DelegatedGroup.java` | + `@Nullable Application coManaged` (l'application d'un `product-owners`) ; `isManageableBy` : + `coManaged.isManagedBy` | EX-5, CA-6b |
| `…/direction/DelegationResolver.java` | applications via `findAvailableIn` ; renseigne `coManaged` | EX-2, EX-5 |
| `…/direction/web/GroupController.java` | lectures : retire les filtres réservés (`:129`, `:162`), règle d'`getApplicationGroups` par `isAvailableIn` ; écritures de membres d'un groupe réservé passées par `DelegationService` (journal de désignation avec groupe, événements DIRECTION ou APPLICATION, plus GROUP) ; `requireNotReserved` gardé pour la création, la suppression et les rôles | EX-2, EX-5, EX-7, EX-8 |
| `…/direction/web/DirectionController.java` | supprime `PUT/DELETE /admins/{userId}` ; groupes d'un utilisateur sans filtre réservé ; règle de `getDirectionUsers` par `isReadableBy` | EX-3, EX-4, PA-5 |
| `…/direction/MemberSearchService.java` | `findAvailableIn` ; plus d'exclusion ni de `RESERVED_NAME` des groupes réservés | EX-4, CA-4 |
| `…/application/web/ApplicationManagerController.java` | supprime `PUT/DELETE` ; `GET` gardé | PA-5 |
| `…/application/web/ApplicationRoleController.java` | `GET` par `isReadableBy` et filtré pour Hurura'a (`hururaa.direction.admin` et les `manage` des applications de la direction) ; `POST/DELETE` refusés (`RESERVED_NAME`) pour tout rôle de Hurura'a | EX-2, EX-7, CA-8 |
| `…/application/web/ApplicationController.java` | `getApplications(direction, manageable)` : `findAvailableIn`, Hurura'a gardée si l'utilisateur est délégué de la direction ; historique de Hurura'a hors de la DSI : catégorie `DELEGATION` de la direction | EX-2, EX-9 |
| `…/uaa/DelegationService.java` | `removeAdmin` : `LAST_HURURAA_ADMIN` si DSI et dernier membre (`UaaProperties`) ; signatures `throws HururaaProblemException` | EX-6, CA-7 |
| `…/journal/PermissionJournal.java` | `directionAdmin*`, `applicationManager*` écrivent `group_name` | EX-8, CA-5 |
| `…/uaa/package-info.java` | Javadoc : nouvelle chaîne de délégation | EX-11 |
| `backend/hururaa-api/src/main/resources/db/changelog/changes/1791000000000-1-register-hururaa.xml`, `db.changelog-master.xml` | changeset sans contexte : insère Hurura'a (`${platformOrganization}`) si aucune ligne `hururaa` (précondition `sqlCheck`, `MARK_RAN`) | EX-1, CA-1 |
| `backend/hururaa-api/src/main/resources/application.yml` | `spring.liquibase.parameters.platformOrganization: ${uaa.platform-organization}` (document `demo` : rien à ajouter, même propriété) | EX-1 |
| `backend/hururaa-api/src/test/java/pf/hururaa/HururaaFixtures.java` | + `HURURAA_ID = 1L`, `hururaa()` ; `stubDevDelegations` sur `findAvailableIn` (Hurura'a dans toute direction) | tests |
| `…/test/…/direction/web/GroupControllerTest.java` | tests réservés réécrits (`:142`, `:443-475`) ; nouveaux cas CA-2, CA-5, CA-6, CA-6b, CA-7, CA-8, CA-9 | preuve |
| `…/test/…/direction/web/DirectionControllerTest.java`, `…/application/web/ApplicationRoleAndManagerControllersTest.java`, `…/application/web/ApplicationControllerTest.java` | désignation supprimée (tests retirés) ; CA-3, rôles de Hurura'a filtrés et figés, Hurura'a listée dans la DPAM, historique | preuve |
| `…/test/…/direction/MemberSearchServiceTest.java` | `givenReservedGroup_whenSearch_thenRefused` remplacé ; CA-4 | preuve |
| `…/test/…/uaa/DelegationServiceTest.java`, `…/journal/PermissionJournalTest.java` | dernier administrateur ; `group_name` des désignations | CA-7, CA-5 |
| `…/test/…/application/jpa/RegisterHururaaTest.java` | nouveau `@DataJpaTest` (contexte `test`) : Hurura'a enregistrée dans `dsi` | CA-1 |
| `frontend/angular/projects/hururaa/src/app/core/delegations.service.ts` (+ spec) | + `isHururaaManager`, `directions` = toutes pour lui ; + `canManageGroup(group, direction)`, miroir de `isManageableBy` et de la co-gestion ; `canReadApplication` pour Hurura'a | EX-10, DEL-8 |
| `…/core/problem-messages.ts` | message de `LAST_HURURAA_ADMIN` | EX-6, UX-2 |
| `…/pages/applications/direction-overview.ts` (+ spec) | administrateurs visibles des délégués, sans désignation ; lien vers `hururaa.admins` de Hurura'a | EX-9, CA-10 |
| `…/pages/applications/application-managers.ts` | sans désignation ; lien vers `hururaa.<préfixe>.product-owners` | EX-9 |
| `…/pages/applications/application-context.ts`, `application-detail.ts` | `canManage` de Hurura'a = délégué de la direction ; `canEdit` seulement dans sa direction | EX-9 |
| `…/pages/applications/group-detail.ts` (+ spec), `application-groups.ts`, `application-roles.ts` | actions selon `canManageGroup` ; groupe réservé : ni suppression ni rôles modifiables ; Hurura'a : ni création de groupe ni de rôle | EX-7, EX-10, CA-10 |
| `…/pages/users/users.ts` (+ spec), `user-groups.ts` | groupes de Hurura'a ouvrables par les délégués ; filtre Hurura'a | EX-3, EX-4, CA-11 |
| `…/pages/home.ts` | textes `home.rules.reserved`, `home.rules.groupManagers`, niveaux de délégation revus | EX-9 |
| `…/src/locale/messages*.xlf` | régénérés (skill) | UX-1 |
| `README.md` (§2, données de dev), `CLAUDE.md` (`application`, `direction`, `uaa`, réservés) | modèle à jour | EX-11 |

## Ordre de travail

1. Politiques et décision 0008 (SEC-5, DEL-1, DEL-4, DEL-5, DEL-6, registre) — skill : aucun —
   vérification : `git diff --check`. Commit `docs(0004): …`.
2. Hurura'a dans toute direction (côté domaine et lecture) : `DelegationGroups`, `Application`,
   `ApplicationRepository.findAvailableIn`, `DelegatedDirection`, `DelegatedGroup`,
   `DelegationResolver`, les appelants, les filtres réservés retirés, les règles de lecture,
   `HururaaFixtures`, les tests de lecture (CA-2, CA-3, CA-4, CA-11 côté API) — skill :
   `create-spring-rest-controller-test` pour les nouveaux cas — vérification :
   `./mvnw -pl hururaa-api test`.
3. Écritures : membres des groupes réservés par `DelegationService`, `LAST_HURURAA_ADMIN`, journal
   avec groupe, rôles de Hurura'a figés, désignation supprimée et ses tests, tests CA-5 à CA-9 —
   skill : `create-spring-rest-controller-test` — vérification :
   `./mvnw -pl hururaa-api,hururaa-problem test`.
4. Enregistrement Liquibase + `RegisterHururaaTest` + paramètre — skill : aucun (pas de nouvelle
   entité) — vérification : `./mvnw -pl hururaa-api test -Dtest='RegisterHururaaTest,DevDataTest,ApplicationRepositoryTest,PermissionHistoryServiceTest'`.
5. Contrats régénérés : `./build-openapi.sh` puis `npm run api` — vérification : `npm run build`
   échoue tant que `problem-messages.ts` n'a pas `LAST_HURURAA_ADMIN` ; ajouter le message, puis
   `npm run build` passe.
6. Frontend : `DelegationsService`, vue d'ensemble, gestionnaires, contexte, détail du groupe,
   groupes, rôles, utilisateurs, accueil, et leurs specs — skill : aucun — vérification :
   `npm run test:ci && npm run lint`.
7. i18n, après le dernier texte — skill : `angular-fix-i18n` — vérification :
   `npm run extract-i18n` sans unité manquante.
8. Documentation : `README.md`, `CLAUDE.md`, `package-info` — skill : aucun — vérification :
   `git diff --check`.
9. Evals (changement de `CLAUDE.md` et `.claude/**`) — skill : `sdlc-evals` (`run`) —
   vérification : `EVALS n/n OK`, rapport collé dans la PR.
10. Vérification finale : `./verify.sh` (`VERIFY OK`), lignes de *Preuve*, sous-agent `verifier`.

## Risques

- **Élévation acceptée, plus large qu'on ne le voit.** Un gestionnaire de Hurura'a devient
  délégué de toutes les directions (lecture des membres et de l'historique) et peut nommer des
  administrateurs Hurura'a. → C'est la décision de PA-3, consignée dans 0008 ; des tests d'accès
  prouvent chaque cas, y compris les refus restants (`hururaa.admins` d'une autre direction pour un
  administrateur de direction).
- **La co-gestion ouvre trop.** `coManaged` mal calculé donnerait `hururaa.admins` à un
  gestionnaire. → `coManaged` n'est renseigné que pour un nom `hururaa.<p>.product-owners` dont
  `p` est une application de la direction ; test CA-6b sur le refus.
- **Dernier administrateur contourné.** Le retrait par la console Keycloak, ou une course entre
  deux retraits simultanés, échappe au garde-fou. → Hors périmètre (spec) ; compter les membres
  juste avant le retrait limite la fenêtre.
- **Changeset sans contexte en test.** Il insère Hurura'a dans la base de test, ce qui peut changer
  des comptes d'applications attendus. → Les tests de dépôt et d'historique sont lancés à l'étape
  4 ; un attendu qui change est un écart à consigner, pas un test affaibli.
- **Paramètre Liquibase.** Si `spring.liquibase.parameters` n'interpole pas
  `uaa.platform-organization`, l'insertion porterait `${platformOrganization}`. → Lecture de la
  documentation Spring Boot (Context7) avant l'étape 4 ; `RegisterHururaaTest` vérifie la direction
  `dsi`.
- **Suppression des endpoints de désignation.** Elle casse tout client externe. → Aucun client connu
  hors du frontend (PA-5) ; `npm run api` fait échouer la compilation de tout usage restant.
- **Droits de lecture du frontend en avance sur l'API.** → `canManageGroup` et `canReadApplication`
  sont testés contre les mêmes jetons que les règles de l'API (DEL-8).

## Preuve

| Critère | Comment | Test, commande ou vérification |
| --- | --- | --- |
| CA-1 | tests | `RegisterHururaaTest` (contexte `test`, Hurura'a dans `dsi`) ; `ApplicationControllerTest.givenHururaa_whenDelete_thenApplicationHasGroups` |
| CA-2 | test | `GroupControllerTest.givenDpamAdmin_whenGetGroups_thenHururaaGroupsAttachedToHururaa` |
| CA-3 | test | `DirectionControllerTest.givenHururaaAdmin_whenGetDpamManagerGroups_thenProductOwnersListed` |
| CA-4 | test | `MemberSearchServiceTest.givenDirectionAdminRole_whenSearchDpam_thenDpamAdminFound` |
| CA-5 | tests | `GroupControllerTest.givenDpamAdmin_whenAddMemberToEscalesProductOwnersOrDpamAdmins_thenJournaledAsDelegation`, `…givenDpamAdmin_whenAddMemberToDafAdmins_thenForbidden` |
| CA-6 | test | `GroupControllerTest.givenHururaaManager_whenAddMemberToDsiOrDpamAdmins_thenAccepted` |
| CA-6b | test | `GroupControllerTest.givenEscalesManager_whenAddMemberToItsProductOwners_thenAccepted_andToDpamAdmins_thenForbidden` |
| CA-7 | tests + vérification manuelle | `GroupControllerTest.givenLastHururaaAdmin_whenRemoved_thenLastHururaaAdmin`, `DelegationServiceTest`, `problem-messages.spec.ts` ; en dev, `dsi.admin` seul membre se retire : bannière traduite |
| CA-8 | tests | `GroupControllerTest.givenHururaaAdmin_whenChangeReservedGroupItself_thenReservedName` ; `ApplicationRoleAndManagerControllersTest.givenHururaaAdmin_whenCreateOrDeleteHururaaRole_thenReservedName` ; attribution de `hururaa.direction.admin` à `escales.agent` refusée |
| CA-9 | test | `GroupControllerTest.givenAlreadyMember_whenAddToReservedGroup_thenNotJournaled` |
| CA-10 | specs + vérification manuelle | `direction-overview.spec.ts`, `group-detail.spec.ts` (`dpam.manager` sans action sur `hururaa.admins`, `dpam.admin` avec) ; en dev : `dpam.manager` ouvre `/applications/dpam`, voit les administrateurs et le lien, sans action |
| CA-11 | spec + test + vérification manuelle | `users.spec.ts` (filtre Hurura'a), `DirectionControllerTest` (filtre `applicationId` Hurura'a dans la DPAM) ; en dev, filtrer la DPAM par Hurura'a : `dpam.admin`, `dpam.manager` |
| CA-12 | commande | `git diff master...HEAD --stat -- .claude/skills/policy-* docs/decisions/0008-*` non vide, revu à la revue |

## Écarts

<!-- rempli pendant l'implémentation : date — quoi — pourquoi — effet sur les fichiers ou la preuve -->

- 2026-10-15 — Étapes 2 et 3 en un seul commit — la co-gestion (étape 2, `DelegatedGroup`) change
  la règle d'accès des écritures de membres (étape 3) dans les mêmes fichiers (`GroupController`,
  `GroupControllerTest`) : séparées, l'étape 2 laisserait un test d'écriture faux entre les deux —
  aucun fichier en plus ; la preuve ne change pas.
- 2026-10-15 — Règle de lecture des rôles et de l'historique portée par
  `DelegatedDirection.canReadApplication(application, authentication)`, pas par
  `Application.isReadableBy(direction, authentication)` — `application.domain` dépendrait de
  `direction.domain`, qui dépend déjà de lui — mêmes fichiers (`DelegatedDirection` est listé),
  même règle, même preuve (SEC-2 tenu : méthode de l'objet résolu prenant l'`Authentication`).
- 2026-10-15 — Recherche de membres : la règle garde `#direction.hasDelegate(authentication)` et
  remplace l'égalité de direction par `#application.isAvailableIn(#direction.alias)`, au lieu de
  `isReadableBy` — c'était déjà la règle de la recherche (délégués de la direction), seule
  l'exclusion de Hurura'a tombe — aucun effet sur les fichiers ni la preuve.
- 2026-10-15 — Un groupe `hururaa.*` qui ne porte aucune délégation connue (ni `hururaa.admins`, ni
  le `product-owners` d'une application de la direction) refuse l'ajout ou le retrait de membres
  (`RESERVED_NAME`) — Hurura'a ne saurait ni le journaliser ni ce qu'il attribue (EX-7) — test
  `GroupControllerTest.givenReservedGroupOfNoKnownDelegation_whenAddMember_thenReservedName`.
- 2026-10-15 — `ApplicationRepositoryTest` (fichier non listé) n'enregistre plus `hururaa` lui-même —
  le changeset sans contexte l'insère déjà dans la base de test, et un second enregistrement viole
  l'unicité du préfixe (risque prévu) — l'attendu ne change pas ; le test gagne un cas
  `findAvailableIn`.
- 2026-10-15 — Étapes 5 et 6 en un seul commit — les clients générés ne sont pas versionnés, et
  `npm run build` échoue aussi tant que les pages appellent les endpoints de désignation supprimés :
  l'étape 5 seule ne compilerait pas — aucun fichier en plus.
- 2026-10-15 — Les liens vers `hururaa.admins` et `hururaa.<préfixe>.product-owners` lisent l'id de
  Hurura'a dans `GET /applications?direction=` plutôt que dans le groupe — un groupe absent
  (direction créée hors de Hurura'a) répondrait `404` et afficherait la bannière d'erreur — aucun
  fichier en plus.
- 2026-10-15 — Vérification manuelle de CA-10 : la garde `skipApplicationChoice` mène `dpam.manager`,
  gestionnaire d'une seule application de la DPAM, droit à Escales, sans la vue d'ensemble — la
  vérification passe par Hurura'a > Groupes > `hururaa.admins` (administrateurs listés, aucune
  action) ; la vue d'ensemble et son lien sont prouvés par `direction-overview.spec.ts` et vérifiés
  avec `dpam.admin`.
- 2026-10-15 — Le miroir frontend `canReadApplication` est fondu dans
  `DelegationsService.canManageApplication` (Hurura'a : `canReadDirection` de la direction), qui
  sert déjà de garde à la fiche d'application — une seule méthode à tenir en phase avec l'API.
- 2026-10-15 — Suites du sous-agent `verifier` : `ApplicationService.delete` (fichier non listé)
  refuse explicitement de désenregistrer Hurura'a (`RESERVED_NAME`) quand la DSI n'a plus aucun de
  ses groupes, cas que `APPLICATION_HAS_GROUPS` ne couvrait pas (EX-1, test
  `givenHururaaWithoutGroups_whenDelete_thenReservedName`) ; `MemberSearchServiceTest` prouve aussi
  `dpam.manager` sous le filtre Hurura'a (CA-11) ; `GroupControllerTest` vérifie le type
  `RESERVED_NAME` des trois premiers refus de CA-8 ; `canManageGroup` ne montre aucune action sur un
  groupe `hururaa.*` sans délégation connue, que l'API refuse.
- 2026-10-17 — Revue (I-1) : DEL-2 réécrite aussi — `hururaa.application.hururaa.manage`, détenu
  dans la DSI, vaut dans toutes les directions (EX-5, tableau des personas, décision 0008), ce que
  DEL-2 interdisait et que la ligne DEL-2 du tableau des politiques de la spec omettait ; la spec
  approuvée n'est pas modifiée, la politique suit la décision. Evals relancées (`.claude/**`).
