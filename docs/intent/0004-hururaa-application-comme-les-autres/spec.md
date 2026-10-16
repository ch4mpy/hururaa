# Spec : Hurura'a, une application comme les autres

- Intention : [0004](intent.md)
- Statut : brouillon
- Version : 1
- Risque : élevé : le changement touche les règles d'accès, les noms réservés et le modèle de
  données (Liquibase)
- Politiques appliquées : policy-delegation @229653c, policy-securite-api @229653c,
  policy-tracabilite @229653c, policy-ux-i18n @229653c
- Approuvée : —

## Résumé

Hurura'a devient une application de la DSI enregistrée dans tout environnement. Ses groupes
(`hururaa.admins`, `hururaa.<préfixe>.product-owners`) et ses rôles apparaissent dans toutes les
directions : dans les applications, sur les utilisateurs et dans leurs filtres. Les membres de ces
groupes se gèrent par les écrans de groupes, qui remplacent les écrans de désignation, avec la
règle générale des groupes, comme dans la console Keycloak, et la co-gestion des gestionnaires
d'application. Ses rôles, ses groupes et ce qu'ils
attribuent restent figés.

## Exigences

- EX-1 Hurura'a est une application enregistrée de la DSI (`uaa.platform-organization`, préfixe
  `hururaa`) dans tout environnement, pas seulement avec les données de dev ; elle ne peut être ni
  désenregistrée ni changée de direction. (intention : Hurura'a, application enregistrée)
- EX-2 Les listes de groupes d'une direction et d'une application incluent les groupes de
  Hurura'a de cette direction, rattachés à Hurura'a, dans toutes les directions. (intention :
  groupes visibles dans toutes les directions, sous Hurura'a)
- EX-3 Les groupes d'un utilisateur dans une direction incluent les groupes de Hurura'a.
  (intention : visibles sur les utilisateurs)
- EX-4 La recherche des membres d'une direction, filtrée par groupe, par application (Hurura'a) ou
  par rôle (`hururaa.direction.admin`, `hururaa.application.<préfixe>.manage`), inclut les groupes
  de Hurura'a. (intention : filtres par groupe, application et rôle)
- EX-5 Les membres d'un groupe de Hurura'a s'ajoutent et se retirent par les endpoints des membres
  de groupe, avec la règle générale des groupes : administrateurs de la direction du groupe et
  gestionnaires de son application, Hurura'a. S'y ajoute une seule exception :
  `hururaa.<préfixe>.product-owners` est aussi géré par les gestionnaires de l'application de ce
  préfixe, qui gardent la co-gestion qu'ils ont aujourd'hui. (intention : gestion par les mêmes
  écrans, avec les mêmes règles d'accès ; demande du PO sur la spec v1 ; écart avec la contrainte
  « pas d'élévation » → PA-3)
- EX-6 Retirer le dernier membre de `hururaa.admins` de la DSI est refusé par un nouveau type de
  problème `last-hururaa-admin`. (contrainte : pas d'auto-exclusion)
- EX-7 Restent refusés avec `RESERVED_NAME`, même pour un administrateur Hurura'a : créer ou
  supprimer un groupe ou un rôle `hururaa.*`, changer les rôles qu'attribue un groupe `hururaa.*`,
  attribuer un rôle `hururaa.*` à un autre groupe, enregistrer une application d'un préfixe
  réservé. (contrainte : rôles réservés figés, pas d'élévation)
- EX-8 Un changement effectif des membres d'un groupe de Hurura'a est journalisé comme une
  désignation ou une révocation (catégorie `DELEGATION`), avec son `log.info` et les événements de
  la désignation actuelle. (contrainte : traçabilité identique)
- EX-9 Dans l'interface, Hurura'a apparaît avec ses onglets groupes et rôles, et les écrans de
  désignation des administrateurs et des gestionnaires deviennent des liens vers le groupe
  correspondant. (intention : écrans de désignation → liens)
- EX-10 Les actions sur les membres d'un groupe de Hurura'a n'apparaissent qu'à qui EX-5 les
  permet. (DEL-8, UX-6)
- EX-11 SEC-5, DEL-1 et DEL-5 (et DEL-4, DEL-6 selon PA-2) sont réécrites et une décision est ajoutée au
  registre, dans la même PR. (intention : question ouverte sur SEC-5 et DEL-5)

## Hors périmètre

- Créer de nouveaux groupes de Hurura'a, ou changer leurs rôles (voir EX-7).
- Garde-fou contre la suppression du dernier administrateur d'une autre direction que la DSI
  (voir PA-4).
- Les changements faits directement dans la console Keycloak, qui échappent toujours aux
  garde-fous et au journal.

## Conception

### Comportement actuel

- Hurura'a n'est enregistrée que par le changeset du contexte `dev`
  (`backend/hururaa-api/src/main/resources/db/changelog/changes/1790700000001-1-dev-data.xml:28`).
  La démo démarre avec ce contexte (`deploy/compose.yml:184`), mais un déploiement sans `dev` n'a
  pas Hurura'a. Le préfixe `hururaa` est refusé à l'enregistrement
  (`ApplicationService.java:53`, `DelegationGroups.RESERVED_CLIENT_PREFIXES`).
- Les groupes `hururaa.*` sont retirés des listes de groupes de direction et d'application
  (`GroupController.java:129`, `:162`), des groupes d'un utilisateur
  (`DirectionController.java:338`) et de la recherche de membres (`MemberSearchService.java:110`,
  `RESERVED_NAME` si on filtre sur l'un d'eux, `:113`). Tous les endpoints d'écriture de groupe
  les refusent (`GroupController.java:195`, `:256`, `:321`, `:355`, `:421`, `:453`).
- Un groupe appartient à l'application de son préfixe **dans sa direction**
  (`Application.owningGroup`, parmi les applications de la direction). Dans la DSI,
  `hururaa.admins` se rattache donc à Hurura'a. Ailleurs, aucune application n'a le préfixe
  `hururaa` : le groupe n'a pas d'application.
- L'onglet rôles d'une application liste tous les rôles clients de `<préfixe>-api`, sans filtre
  (`ApplicationRoleController.java:86`). Pour Hurura'a, ce sont `hururaa.direction.admin` et les
  `hururaa.application.<préfixe>.manage` de toutes les applications, toutes directions confondues
  (export du royaume de dev). Seuls la création et la suppression sont refusées (`:183`).
  Non observé dans l'application lancée.
- La désignation passe par `PUT/DELETE /directions/{d}/admins/{userId}` (administrateurs Hurura'a
  seuls, `DirectionController.java:218`, `:247`) et
  `PUT/DELETE /directions/{d}/applications/{id}/managers/{userId}` (administrateurs de la direction
  ou gestionnaires de l'application, `ApplicationManagerController.java:108`, `:143`). Aucun
  garde-fou n'empêche aujourd'hui de retirer le dernier administrateur Hurura'a.
- La règle générale d'un groupe (`DelegatedGroup.isManageableBy` : administrateurs de la
  direction ou gestionnaires de son application) ne s'applique pas aux groupes de Hurura'a,
  refusés en écriture. Dans la console Keycloak, les mêmes personnes peuvent déjà, selon leurs
  droits d'administration du royaume, changer les membres de ces groupes : c'est la situation que
  le PO veut refléter.

### Comportement par persona

| Persona | Voit (EX-2 à EX-4) | Change les membres de (EX-5) |
| --- | --- | --- |
| Administrateur Hurura'a | groupes et rôles de Hurura'a de toutes les directions | tous les groupes de Hurura'a, dans toutes les directions |
| Administrateur de direction | ceux de sa direction | tous les groupes de Hurura'a de sa direction, `hururaa.admins` compris : il nomme d'autres administrateurs de sa direction |
| Gestionnaire d'application | ceux de la direction de l'application | le `product-owners` de ses applications (exception de co-gestion) |
| Gestionnaire de Hurura'a | ceux de toutes les directions (selon PA-2) | tous les groupes de Hurura'a, dans toutes les directions : il nomme des administrateurs, y compris des administrateurs Hurura'a (`hururaa.admins` de la DSI) |
| Membre sans rôle | rien (DEL-7) | rien |

Les droits donnés ou retirés prennent effet au renouvellement du jeton du délégataire (DEL-3).
L'interface ne promet pas d'effet immédiat.

### API et contrats

- Listes de groupes (`GET /directions/{d}/groups`, `.../applications/{id}/groups`,
  `.../users/{userId}/groups`) : les groupes `hururaa.*` y figurent, avec `applicationId` et
  `applicationName` de Hurura'a, dans toutes les directions (EX-2, EX-3).
- `GET /directions/{d}/users` : les filtres `group`, `applicationId` (Hurura'a) et `role`
  (rôle `hururaa.*`) portent aussi sur les groupes de Hurura'a. Un filtre sur un groupe réservé
  n'est plus refusé (EX-4).
- `GET /directions/{d}/groups/{group}`, `.../roles`, `.../members` et `.../history` : accessibles
  pour un groupe `hururaa.*`, avec les règles actuelles de consultation (EX-2).
- `PUT/DELETE /directions/{d}/groups/{group}/members/{userId}` sur un groupe `hururaa.*` : règles
  d'accès d'EX-5, celles de tout groupe (`#group.isManageableBy(authentication)`). L'exception de
  co-gestion se lit dans cette même méthode du groupe résolu (SEC-2). Journal, log et événements
  sont ceux de la désignation (EX-8).
- `DELETE /directions/{dsi}/groups/hururaa.admins/members/{userId}` pour son dernier membre :
  `409` `urn:hururaa:problem:last-hururaa-admin` (EX-6). Ce nouveau type reçoit son message dans
  `problem-messages.ts` (UX-2).
- `POST .../groups`, `DELETE .../groups/{group}`, `PUT/DELETE .../groups/{group}/roles/{role}` sur
  un nom `hururaa.*`, et `POST/DELETE` de rôle `hururaa.*` : `RESERVED_NAME`, inchangé (EX-7).
- Hurura'a : `DELETE` de l'application refusé (`APPLICATION_HAS_GROUPS`, elle a toujours ses
  groupes) ; son préfixe reste refusé à l'enregistrement par l'API (EX-1).
- Endpoints de désignation actuels : voir PA-5.

### Interface

- Applications : Hurura'a apparaît dans le navigateur d'applications selon PA-2, avec ses onglets
  groupes, rôles et historique. Le détail d'un de ses groupes montre ses membres, modifiables
  selon EX-5/EX-10, et ses rôles, en lecture seule.
- `DirectionOverview` (administrateurs de la direction) et l'onglet gestionnaires d'une
  application : la liste reste visible, et l'action de désigner ou révoquer devient un lien vers
  le groupe `hururaa.admins` ou `hururaa.<préfixe>.product-owners` (EX-9, UX-5).
- Utilisateurs : les groupes de Hurura'a apparaissent sur chaque membre et dans les filtres
  groupe, application et rôle (EX-3, EX-4).
- Nouveau texte : le message de `last-hururaa-admin`, du type « Impossible de retirer le dernier
  administrateur Hurura'a ». Le texte de la page d'accueil sur les noms réservés
  (`home.rules.reserved`) est revu pour refléter EX-5 et EX-7.

### Données

- Un changeset Liquibase hors contexte `dev` enregistre Hurura'a dans la direction
  `uaa.platform-organization` s'il n'y est pas encore (EX-1). Le changeset `dev` actuel reste
  compatible : pas de doublon.
- Le royaume de dev et sa version de démo ne changent pas : les groupes et rôles existent déjà.

## Politiques appliquées

| Règle | Comment elle est respectée |
| --- | --- |
| DEL-1 | → PA-1 : les administrateurs de direction et les gestionnaires de Hurura'a désignent aussi des administrateurs (EX-5), alors que DEL-1 réserve cette désignation aux administrateurs Hurura'a |
| DEL-2 | Les rôles de Hurura'a ne valent toujours que dans leur direction ; seule leur visibilité change |
| DEL-3 | Pas de promesse d'effet immédiat dans l'interface |
| DEL-4 | → PA-2 (un groupe hors de la DSI rattaché à une application de la DSI) |
| DEL-5 | → PA-1 (membres changés hors des endpoints de désignation) |
| DEL-6 | Direction de Hurura'a immuable, désenregistrement refusé (EX-1) ; → PA-2 pour sa présence ailleurs |
| DEL-7 | Consultation inchangée : délégués seulement |
| DEL-8 | `DelegationsService` reflète EX-5 (EX-10) |
| SEC-1 | Aucun endpoint sans `@PreAuthorize` ; nouveaux cas couverts par les règles existantes ou une méthode de l'objet résolu |
| SEC-2 | Règle d'EX-5 portée par une méthode du groupe résolu prenant l'`Authentication` |
| SEC-3 | Rôles cités par les constantes de `HururaaPermission` |
| SEC-4 | `{group}` d'un groupe `hururaa.*` hors de la DSI résolu avec Hurura'a avant toute règle (PA-2) |
| SEC-5 | → PA-1 (groupes réservés listés, membres modifiables) ; le reste tient (EX-7) |
| SEC-6 | Nouveau type `last-hururaa-admin` dans `ProblemType` |
| SEC-7 | Tests d'accès des endpoints de membres pour chaque niveau, sur `hururaa.admins` et `product-owners`, dans la DSI et hors de la DSI, co-gestion comprise |
| TRA-1, TRA-2, TRA-3 | EX-8 : journal seulement si le changement est effectif, avec `log.info` |
| TRA-4 | Inchangé |
| TRA-5 | Catégorie `DELEGATION` existante, rien de nouveau dans l'historique |
| TRA-6 | Événements de la désignation actuelle, et ceux du groupe pour les écrans ouverts sur lui |
| UX-1 | Nouveaux textes traduits, puis le skill `angular-fix-i18n` |
| UX-2 | Message de `last-hururaa-admin` |
| UX-3 | Retrait d'un administrateur ou d'un gestionnaire confirmé par `confirm()` |
| UX-4 | Composants existants |
| UX-5 | Désignation à un seul endroit, le groupe ; liens ailleurs (EX-9) |
| UX-6 | EX-10 |
| UX-7 | Pages des groupes de Hurura'a rechargées sur les mêmes `ResourceTypes` |

## Points d'attention

### PA-1 Réécriture de SEC-5 et DEL-5

- Enjeu : l'intention contredit deux règles en vigueur. Une règle ne change que par une PR sur sa
  politique, revue par son responsable, avec une décision au registre.
- Options : A, dans cette PR, nouvelle rédaction et décision `0008` qui amende 0007 :
  - SEC-5 : les rôles `hururaa.*` et les groupes `hururaa.*` en tant que tels (création,
    suppression, rôles attribués) sont intouchables ; les groupes réservés sont listés ; leurs
    membres se changent avec la règle générale des groupes ;
  - DEL-5 : les membres des groupes réservés changent par les écrans de groupes, sous la règle
    générale des groupes et avec la co-gestion des gestionnaires d'application ;
  - DEL-1 : les administrateurs de direction et les gestionnaires de Hurura'a désignent aussi des
    administrateurs, de leur direction pour les premiers, de toutes les directions pour les
    seconds.

  B, une PR de politique séparée, fusionnée avant le plan de 0004.
- Recommandation : A. La règle et le code qui l'applique changent ensemble, et la revue du tech
  lead couvre les deux.
- Décide : responsable des politiques (ch4mpy)
- Décision : à décider

### PA-2 Hurura'a dans les autres directions

- Enjeu : Hurura'a est une application de la DSI (DEL-6), mais ses groupes existent dans chaque
  direction. Les rattacher à Hurura'a hors de la DSI contredit DEL-4 (« l'application de ce
  préfixe dans sa direction ») et la règle `#application.direction == #direction.alias` des
  chemins sous une application.
- Options :
  - A : Hurura'a apparaît dans le navigateur d'applications de **chaque** direction, comme une
    application transverse. Elle n'y montre que les groupes de cette direction et les rôles qui y
    ont un sens (`hururaa.direction.admin` et les `manage` des applications de la direction).
    Réglages et gestionnaires de Hurura'a restent dans la DSI.
  - B : Hurura'a n'apparaît que dans la DSI. Ailleurs, ses groupes sont visibles sur les
    utilisateurs et dans les filtres, étiquetés « Hurura'a », et gérés depuis la vue d'ensemble de
    la direction (administrateurs) et l'onglet gestionnaires de chaque application.
- Recommandation : A. C'est le sens de « une application comme les autres » vu d'une direction :
  un administrateur de la DPAM y trouve où ses délégations se gèrent. Il faut alors amender DEL-4 et
  DEL-6 (PA-1) pour une application transverse, la seule.
- Décide : PO
- Décision : à décider

### PA-3 Écart avec la contrainte « pas d'élévation » de l'intention

- Enjeu : l'intention acceptée impose que personne ne puisse s'octroyer, par les écrans de groupes,
  un niveau qu'il n'a pas déjà le droit d'attribuer, par exemple devenir administrateur Hurura'a.
  À la demande du PO sur la v1, EX-5 applique la règle générale des groupes : un administrateur de
  direction nomme d'autres administrateurs de sa direction, et un gestionnaire de Hurura'a nomme
  des administrateurs Hurura'a, lui-même compris. Le PO justifie ce choix ainsi : c'est déjà
  possible par la console Keycloak, et cela correspond aux attributions réelles. Une intention
  acceptée ne se réécrit pas.
- Options :
  - A : le PO accepte l'écart en connaissance de cause à l'approbation de la spec. L'écart est
    consigné dans la décision 0008, et l'intention reste telle quelle.
  - B : une nouvelle intention remplace 0004 avec la contrainte corrigée, et 0004 passe
    « abandonnée ».
  - C : revenir aux règles de la désignation actuelle pour `hururaa.admins` (spec v1).
- Recommandation : A. Le besoin ne change pas, seule une contrainte est assouplie par celui qui
  l'avait posée. La décision la trace, et le garde-fou du dernier administrateur (EX-6) tient
  toujours. Conséquence à accepter : être gestionnaire de Hurura'a équivaut désormais à pouvoir
  devenir administrateur Hurura'a.
- Décide : PO
- Décision : à décider

### PA-4 Dernier administrateur des autres directions

- Enjeu : l'intention protège le dernier administrateur Hurura'a. Une direction sans
  administrateur reste rattrapable par un administrateur Hurura'a.
- Options : A, DSI seulement ; B, toutes les directions.
- Recommandation : A. C'est le besoin exprimé, et B bloquerait le départ d'un administrateur
  qu'on n'a pas encore remplacé.
- Décide : PO
- Décision : à décider

### PA-5 Endpoints de désignation actuels

- Enjeu : une fois les écrans de désignation remplacés par des liens, deux chemins de l'API font
  le même changement.
- Options : A, les supprimer (un seul chemin, une seule règle à tester) ; B, les garder (aucun
  client connu hors du frontend).
- Recommandation : A. Moins de surface, et SEC-7 n'a qu'un jeu de règles à prouver.
- Décide : tech lead
- Décision : à décider

## Critères d'acceptation

- CA-1 Étant donné une base démarrée sans le contexte `dev`, quand on liste les applications de
  la DSI, alors Hurura'a y figure, et son `DELETE` est refusé. (EX-1)
- CA-2 Étant donné `dpam.admin`, quand il liste les groupes de la DPAM, alors `hururaa.admins` et
  `hururaa.escales.product-owners` y figurent, rattachés à Hurura'a. (EX-2)
- CA-3 Étant donné `dsi.admin`, quand on lit les groupes de `dpam.manager` dans la DPAM, alors
  `hururaa.escales.product-owners` y figure. (EX-3)
- CA-4 Étant donné `dsi.admin`, quand il cherche les membres de la DPAM filtrés par le rôle
  `hururaa.direction.admin`, alors `dpam.admin` est trouvé. (EX-4)
- CA-5 Étant donné `dpam.admin`, quand il ajoute un membre à `hururaa.escales.product-owners` ou à
  `hururaa.admins` de la DPAM, alors c'est accepté, journalisé en `DELEGATION` et publié. Quand il
  ajoute un membre à `hururaa.admins` de la DAF, alors `403`. (EX-5, EX-8)
- CA-6 Étant donné `dsi.manager` (gestionnaire de Hurura'a), quand il ajoute un membre à
  `hururaa.admins` de la DSI ou de la DPAM, alors c'est accepté. (EX-5, PA-3)
- CA-6b Étant donné `dpam.manager` (gestionnaire d'Escales), quand il ajoute un membre à
  `hururaa.escales.product-owners`, alors c'est accepté. Quand il ajoute un membre à
  `hururaa.admins` de la DPAM, alors `403`. (EX-5)
- CA-7 Étant donné `dsi.admin`, seul membre de `hururaa.admins` de la DSI, quand il s'en retire,
  alors `409` `last-hururaa-admin`, et l'interface affiche le message traduit. (EX-6)
- CA-8 Étant donné `dsi.admin`, quand il crée un groupe `hururaa.x`, supprime `hururaa.admins`,
  lui ajoute ou retire un rôle, ou attribue `hururaa.direction.admin` à `escales.agent`, alors
  `RESERVED_NAME`. (EX-7)
- CA-9 Étant donné un ajout de membre déjà présent dans un groupe de Hurura'a, quand il est
  rejoué, alors rien n'est journalisé. (EX-8)
- CA-10 Étant donné `dpam.manager` dans l'interface, quand il ouvre la vue d'ensemble de la DPAM,
  alors il voit ses administrateurs sans action de désignation, avec un lien vers le groupe
  `hururaa.admins`, et sur ce groupe aucune action d'ajout ou de retrait. `dpam.admin` y trouve
  ces actions. (EX-9, EX-10)
- CA-11 Étant donné la page des utilisateurs de la DPAM, quand on filtre par l'application
  Hurura'a, alors `dpam.admin` et `dpam.manager` sont listés avec leur groupe de Hurura'a. (EX-3,
  EX-4, EX-9)
- CA-12 SEC-5, DEL-1, DEL-5 (et DEL-4, DEL-6 selon PA-2) réécrites et décision ajoutée au registre dans
  la PR. (EX-11)

## Questions de l'intention

- Nouvelle rédaction de SEC-5 et DEL-5, et décision au registre → PA-1 (et PA-2 pour DEL-4,
  DEL-6).

## Historique

- v1 — 2026-10-15 — première version
