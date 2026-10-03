# Hurura'a — gestion des accès aux applications des directions

Preuve de concept pour la DSI (direction des systèmes d'information de la Polynésie française),
qui exploite Hurura'a pour gérer les permissions des applications dont elle a la responsabilité.
Ces applications sont rattachées à des directions métier (DPAM, DAF...), à qui l'on délègue une
partie de leur gestion. La DSI est elle-même une direction comme les autres, avec ses propres
applications, administrateurs et gestionnaires, et ce sont ses administrateurs qui administrent
Hurura'a. Hurura'a :

- identifie les utilisateurs des applications des différentes directions (Keycloak, royaume
  unique `public-facing`) ;
- définit quelle application est gérée par quelle direction ;
- gère qui, au sein d'une direction, peut donner à un utilisateur la permission de gérer les
  permissions d'une application ;
- gère, pour chaque application, qui a le droit d'en gérer les permissions : définition des rôles,
  agrégation des rôles en groupes, affectation des utilisateurs aux groupes.

Une démonstration est en ligne sur https://hururaa.c4-soft.com, redéployée à chaque push sur
`master` (voir [deploy/README.md](deploy/README.md)).

## 1. Modèle

| Notion Hurura'a       | Où elle vit                                                                                 |
| --------------------- | ------------------------------------------------------------------------------------------- |
| Direction             | Organisation Keycloak (`dsi`, `dpam`, `daf`), désignée par son alias                         |
| Utilisateur           | Utilisateur Keycloak, membre d'une ou plusieurs organisations                               |
| Application           | Ligne de la table `APPLICATIONS` de Hurura'a : préfixe des clients, nom, direction          |
| Clients Keycloak      | `<préfixe>-bff` (authorization code + PKCE, refresh token) et `<préfixe>-api` (client credentials, compte de service, porte les rôles) |
| Rôle d'application    | Rôle client de `<préfixe>-api` (ex. `escales.stopovers.edit` sur `escales-api`)               |
| Groupe                | Groupe d'organisation Keycloak propre à une application, nommé `<préfixe>.<nom>` (ex. `escales.agent`) : ses membres reçoivent, dans cette direction, les rôles de l'application qu'il porte |
| Administrateurs et gestionnaires | Rôles réservés de `hururaa-api` (`hururaa.direction.admin`, `hururaa.application.<préfixe>.manage`), attribués par les groupes réservés que Hurura'a crée dans chaque direction (`hururaa.admins`) et pour chaque application (`hururaa.<préfixe>.product-owners`) |

Dans les jetons, les rôles sont rangés par direction, grâce au mapper « organization group
membership » du scope `organization` :

```json
"organization": {
  "dpam": {
    "resource_access": { "escales-api": { "roles": ["escales.stopovers.edit", "escales.stopovers.read"] } },
    "groups": ["/escales.agent"]
  }
}
```

Chaque API applicative lit donc ses rôles dans `organization.<direction>.resource_access.<préfixe>-api`,
avec le même `TenantPermissionsExtractor` que Hurura'a (`common-security-starter`, propriété
`roles-namespace`).

## 2. Chaîne de délégation

```
Administrateur Hurura'a = administrateur de la DSI (hururaa.direction.admin dans l'organisation dsi)
 ├─ agit à tous les niveaux ci-dessous, dans toutes les directions
 └─ crée les directions et désigne leurs administrateurs
     Administrateur de direction (groupe hururaa.admins de la direction : hururaa.direction.admin)
      ├─ enregistre, renomme et désenregistre les applications de sa direction
      ├─ définit leurs rôles et désigne, parmi les membres, leurs gestionnaires
      └─ gère les groupes de ces applications : rôles attribués et membres
          Gestionnaire d'application (groupe hururaa.<préfixe>.product-owners de la direction :
          hururaa.application.<préfixe>.manage)
           ├─ définit les rôles de l'application (rôles client de <préfixe>-api)
           ├─ désigne les autres gestionnaires de l'application
           ├─ crée les groupes de l'application (<préfixe>.<nom>) et leur fait attribuer ses rôles
           └─ affecte les membres de la direction à ces groupes
```

Règles appliquées par l'API, écrites dans les `@PreAuthorize` des endpoints. Les variables de
chemin `{direction}`, `{group}` et `{applicationId}` y sont résolues en objets dont les méthodes lisent
les rôles de Hurura'a dans le jeton (404 si la direction, le groupe ou l'application n'existe pas, avant
toute règle d'accès), par exemple `#direction.isAdministeredBy(authentication)` :

- administrateurs et gestionnaires se reconnaissent à des rôles de `hururaa-api`, qui ne valent que dans la direction où ils sont
  détenus : `hururaa.direction.admin` administre la direction (dans la DSI,
  `uaa.platform-organization`, toutes les directions : il y devient une authority de
  l'utilisateur), `hururaa.application.<préfixe>.manage` gère l'application, détenu dans sa
  direction (ailleurs, il ne donne rien) ;
- comme tout rôle, ils prennent effet, donnés ou retirés, au renouvellement du jeton de
  l'utilisateur (au plus tard à l'expiration de son jeton d'accès) ;
- les noms commençant par `hururaa.` sont réservés, pour les groupes comme pour les rôles
  (`RESERVED_NAME`) : Hurura'a crée le groupe `hururaa.admins` avec chaque direction, le groupe
  `hururaa.<préfixe>.product-owners` et son rôle avec chaque application (et les supprime avec
  elle), et leurs membres ne changent que par la désignation des administrateurs et des
  gestionnaires ; l'API des groupes ne les liste pas et refuse de les modifier, même aux
  administrateurs Hurura'a. Les préfixes d'application `hururaa`, `admin`, `direction`,
  `product-owners` et `manage` sont réservés aussi ;
- un groupe appartient à l'application dont son nom porte le préfixe (`escales.agent` à Escales),
  dans la direction de celle-ci, et n'attribue que ses rôles : c'est Hurura'a qui l'impose,
  Keycloak ne sait rattacher ni un client à une organisation, ni un groupe à un client. Il se crée
  sous son application (`POST /directions/{direction}/applications/{applicationId}/groups`, avec
  le nom sans le préfixe), puis s'adresse sous sa direction
  (`/directions/{direction}/groups/{group}`) ;
- modifier les rôles ou les membres d'un groupe, ou le supprimer, exige d'administrer sa direction
  ou de gérer son application ; un groupe créé hors de Hurura'a, dont le nom ne correspond à
  aucune application de sa direction, n'est géré que par les administrateurs de la direction et
  n'attribue aucun rôle par Hurura'a (`GROUP_WITHOUT_APPLICATION`) ;
- une application reste dans la direction où elle a été enregistrée ; elle ne peut être
  désenregistrée tant qu'elle a des groupes (`APPLICATION_HAS_GROUPS`) ;
- consulter une application (rôles, gestionnaires) ou une direction (administrateurs, groupes,
  membres, historique des permissions) est ouvert à ses administrateurs et gestionnaires, à
  n'importe quel niveau ;
- une application est modifiée, et ses rôles et gestionnaires adressés, sous sa direction
  (`/directions/{direction}/applications/{applicationId}/...`), et seulement sous elle.

L'historique des permissions d'une direction (qui a changé quoi, et quand), affiché sur les pages
de direction, d'application et de groupe et filtrable par catégorie, fusionne deux sources :
l'audit Envers des applications (enregistrement, renommage, désenregistrement) et le journal
décrit ci-dessous.

Les changements que Hurura'a fait dans Keycloak (création des directions, désignation et
révocation des administrateurs et des gestionnaires, rôles des applications, groupes, rôles qu'ils
attribuent, membres) sont consignés dans un journal propre à Hurura'a, la
table `PERMISSION_EVENTS` : Keycloak les attribue au compte de service de l'API, pas à la personne.
Seuls les changements effectifs y figurent (les opérations sont idempotentes), avec leur auteur et
leur horodatage ; l'état courant se lit toujours dans Keycloak.
Les journaux applicatifs (Loki) en gardent aussi la trace.

Les administrateurs Hurura'a créent les directions depuis Hurura'a (`POST /directions`, une
organisation Keycloak sans domaine).

Hurura'a est lui-même une application de la DSI, dont les rôles et les groupes sont tous réservés :
ce sont ceux des administrateurs et des gestionnaires, présents dans toutes les directions.

Hypothèses de départ, susceptibles d'évoluer avec l'exploration (voir
[docs/decisions](docs/decisions/README.md), en particulier les décisions 0005 et 0007) :

- l'association application ↔ direction est conservée dans la base PostgreSQL de Hurura'a ;
- un groupe est un groupe de l'organisation de son application, qui se lit dans son nom
  (`<préfixe>.<nom>`), et ne porte que les rôles de cette application ;
- à l'enregistrement d'une application, Hurura'a crée ses clients Keycloak `<préfixe>-bff` et
  `<préfixe>-api` s'ils n'existent pas encore (mêmes réglages que ceux du royaume de dev, URI de
  redirection du BFF à ajuster une fois l'application déployée, secrets générés par Keycloak et lus
  dans sa console) ; des clients existants sont repris tels quels. Le compte de service de
  `<préfixe>-api` reçoit les rôles `realm-management` de
  `keycloak-admin-api.api-service-account-roles`.

## 3. Environnement de dev

Royaume `public-facing` (`keycloak/import/public-facing-realm.json`), directions et applications :

| Direction | Applications         | Groupes (rôles)                                                                       |
| --------- | -------------------- | ------------------------------------------------------------------------------------- |
| DSI       | Hurura'a, Te Fenua   | `hururaa.admins`, `hururaa.hururaa.product-owners`, `hururaa.te-fenua.product-owners`, `te-fenua.agent` (`te-fenua.parcels.read`, `te-fenua.parcels.edit`) |
| DPAM      | Escales              | `hururaa.admins`, `hururaa.escales.product-owners`, `escales.agent` (`escales.stopovers.read`, `escales.stopovers.edit`) |
| DAF       | Anahei               | `hururaa.admins`, `hururaa.anahei.product-owners`, `anahei.agent` (`anahei.files.read`, `anahei.files.edit`) |

Utilisateurs (mot de passe `secret` pour tous) :

| Utilisateur    | Direction | Rôle dans Hurura'a                                                            |
| -------------- | --------- | ----------------------------------------------------------------------------- |
| `dsi.admin`    | DSI       | administrateur de la DSI, donc administrateur Hurura'a                       |
| `dsi.manager`  | DSI       | gestionnaire de Hurura'a et de Te Fenua                                       |
| `dsi.agent`    | DSI       | aucun (membre de `te-fenua.agent`)                                           |
| `dpam.admin`   | DPAM      | administrateur de la DPAM                                                     |
| `dpam.manager` | DPAM      | gestionnaire d'Escales                                                        |
| `dpam.agent`   | DPAM      | aucun (membre de `escales.agent`)                                            |
| `daf.admin`    | DAF       | administrateur de la DAF                                                      |
| `daf.manager`  | DAF       | gestionnaire d'Anahei                                                         |
| `daf.agent`    | DAF       | aucun (membre de `anahei.agent`)                                             |

Administrateurs et gestionnaires sont les membres des groupes `hururaa.admins` et
`hururaa.<préfixe>.product-owners` de l'export du royaume ; les applications sont chargées par Liquibase (contexte `dev`,
`1790700000001-1-dev-data.xml`). Hors dev, positionner `LIQUIBASE_CONTEXTS` à autre chose que
`dev`.

Les clients confidentiels ont le secret `secret` (dev uniquement). Les comptes de service :
`hururaa-api` gère organisations, groupes, membres et rôles de tous les clients
(`realm-management` : `manage-organizations`, `manage-clients`, `manage-users`...), les autres
`*-api` ne font que lire les utilisateurs et les organisations.

## 4. Organisation du dépôt

- `backend/` — réacteur Maven, `groupId` `pf.hururaa` : `common-security-starter` (rôles
  par direction extraits de la claim `organization`), `common-events-starter` (`ResourceEvent`
  sur RabbitMQ), `gateway` (BFF OAuth2), `hururaa-api` (API REST)
- `frontend/angular/` — workspace Angular 21 : application `hururaa` (pf-ui / PrimeNG 21, Remix
  Icon) et clients générés `@api/gateway`, `@api/hururaa-api`
- `keycloak/`, `compose-*.yml`, `nginx-reverse-proxy/`, `certs/` — infrastructure Docker de dev
- `docs/decisions/` — registre des décisions

## 5. Déploiement en local

Prérequis : Git, JDK 26 et Maven (SDKMan : `sdk env install`), Node.js 24, Docker Desktop (ou
Docker Engine avec `127.0.0.1 host.docker.internal` dans `/etc/hosts`), `JAVA_HOME` positionné,
et un accès à `https://bin.gov.pf/artifactory` pour installer `pf-ui`.

L'infrastructure occupe les ports 443, 3643, 2633, 5672... de l'hôte : arrêter toute autre pile
qui les utilise avant de la lancer.

`bash ./reset-dev-env.sh` initialise l'environnement (`.env`, `secrets/`, certificat auto-signé
pour `host.docker.internal`), construit les images (reverse proxy, Keycloak avec import du royaume
`public-facing`), lance l'infra (PostgreSQL, Mailpit, Keycloak, RabbitMQ, Grafana LGTM, reverse
proxy), génère les specs OpenAPI et les libs clientes du front.

Exécution :

- les fronts : `cd frontend/angular && npm run start` (ou `npm run start-fr`)
- le BFF : `set -a && source .env && set +a && ./backend/mvnw -f backend/pom.xml -pl gateway spring-boot:run`
- l'API : `set -a && source .env && set +a && ./backend/mvnw -f backend/pom.xml -pl hururaa-api spring-boot:run`

Exposés par le reverse proxy : https://host.docker.internal/fr/ et `/en/` (SPA),
`/gateway` (BFF), `/auth` (Keycloak, admin/secret), `/mailpit`, `/grafana`.

Tests : `./backend/mvnw -f backend/pom.xml clean install` (backend) et, dans `frontend/angular`,
`npm run test:ci` et `npm run lint`.

## 6. Pistes pour la suite

- saisir à l'enregistrement l'URL de l'application, pour que Hurura'a règle les URI de
  redirection de `<préfixe>-bff` au lieu de valeurs provisoires ;
- notifier Hurura'a des changements faits directement dans la console Keycloak (event listener SPI),
  pour les journaliser aussi ;
- faire relayer aux administrateurs Hurura'a les événements de toutes les directions (aujourd'hui, un
  utilisateur ne reçoit que ceux des directions dont il est membre).
