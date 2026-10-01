# Hurura'a — gestion des accès aux applications des directions

Preuve de concept pour le SIPF (direction des systèmes d'information de la Polynésie française).
Hurura'a :

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
| Groupe                | Groupe d'organisation Keycloak : ses membres reçoivent, dans cette direction, les rôles qu'il porte |
| Délégations           | Tables `DIRECTION_ADMINS` et `APPLICATION_MANAGERS` de Hurura'a, plus deux rôles de `hururaa-api` pour la plateforme |

Dans les jetons, les rôles sont rangés par direction, grâce au mapper « organization group
membership » du scope `organization` :

```json
"organization": {
  "dpam": {
    "resource_access": { "escales-api": { "roles": ["escales.stopovers.edit", "escales.stopovers.read"] } },
    "groups": ["/escales-agents"]
  }
}
```

Chaque API applicative lit donc ses rôles dans `organization.<direction>.resource_access.<préfixe>-api`,
avec le même `TenantPermissionsExtractor` que Hurura'a (`common-security-starter`, propriété
`roles-namespace`).

## 2. Chaîne de délégation

```
Plateforme (rôles hururaa.* dans l'organisation dsi, groupe « sipf »)
 ├─ enregistre les applications et les rattache à une direction    hururaa.applications.manage
 └─ désigne les administrateurs de chaque direction                 hururaa.direction-admins.manage
     Administrateur de direction (DIRECTION_ADMINS)
      └─ désigne, parmi les membres, les gestionnaires de chaque application de la direction
          Gestionnaire d'application (APPLICATION_MANAGERS)
           ├─ définit les rôles de l'application (rôles client de <préfixe>-api)
           ├─ crée des groupes dans la direction et leur fait attribuer les rôles de ses applications
           └─ affecte les membres de la direction aux groupes
```

Règles appliquées par l'API (bean `@uaa`, `pf.hururaa.uaa.UaaAuthorization`) :

- les rôles `hururaa.*` ne valent que dans l'organisation plateforme (`uaa.platform-organization`,
  `dsi` en dev) : portés par un groupe d'une autre direction, ils ne donnent rien ;
- un groupe n'attribue que des rôles d'applications gérées par sa propre direction
  (`APPLICATION_NOT_IN_DIRECTION` sinon) : c'est Hurura'a qui l'impose, Keycloak ne sait pas
  rattacher un client à une organisation ;
- modifier les membres d'un groupe, ou le supprimer, exige de gérer **toutes** les applications
  dont il attribue des rôles ;
- une application ne peut changer de direction, ni être désenregistrée, tant que des groupes de sa
  direction attribuent ses rôles (`APPLICATION_ROLES_STILL_GRANTED`) ; en changer retire ses
  gestionnaires ;
- consulter une application (rôles, gestionnaires) ou une direction (administrateurs, groupes,
  membres, historique des délégations) est ouvert à quiconque a une délégation dessus, à n'importe
  quel niveau.

L'historique des délégations d'une direction (qui a désigné ou retiré qui, et quand) est rejoué à
partir de l'audit Envers des administrateurs de direction et des gestionnaires d'application. Ce
qui vit dans Keycloak (membres des groupes, rôles qu'ils attribuent) n'y figure pas encore.

Hurura'a est lui-même une application de la DSI : ses gestionnaires administrent le groupe `sipf`
qui porte les rôles de la plateforme.

Hypothèses de départ, susceptibles d'évoluer avec l'exploration (voir
[docs/decisions](docs/decisions/README.md), en particulier la décision 0005) :

- l'association application ↔ direction est conservée dans la base PostgreSQL de Hurura'a ;
- un groupe est rattaché à une organisation, et ne peut porter que les rôles des applications de
  cette organisation ;
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
| DSI       | Hurura'a, Te Fenua   | `sipf` (`hururaa.*`), `te-fenua-agents` (`te-fenua.parcels.read`, `te-fenua.parcels.edit`) |
| DPAM      | Escales              | `escales-agents` (`escales.stopovers.read`, `escales.stopovers.edit`)                  |
| DAF       | Anahei               | `anahei-agents` (`anahei.files.read`, `anahei.files.edit`)                             |

Utilisateurs (mot de passe `secret` pour tous) :

| Utilisateur    | Direction | Délégation                                        |
| -------------- | --------- | ------------------------------------------------- |
| `sipf.admin`   | DSI       | plateforme (groupe `sipf`)                         |
| `dsi.admin`    | DSI       | administrateur de la DSI                           |
| `dsi.manager`  | DSI       | gestionnaire de Hurura'a et de Te Fenua            |
| `dsi.agent`    | DSI       | aucune (membre de `te-fenua-agents`)               |
| `dpam.admin`   | DPAM      | administrateur de la DPAM                          |
| `dpam.manager` | DPAM      | gestionnaire d'Escales                             |
| `dpam.agent`   | DPAM      | aucune (membre de `escales-agents`)                |
| `daf.admin`    | DAF       | administrateur de la DAF                           |
| `daf.manager`  | DAF       | gestionnaire d'Anahei                              |
| `daf.agent`    | DAF       | aucune (membre de `anahei-agents`)                 |

Les délégations des niveaux direction et application sont chargées par Liquibase (contexte `dev`,
`1790700000001-1-dev-data.xml`) avec les identifiants fixés dans l'export du royaume. Hors dev,
positionner `LIQUIBASE_CONTEXTS` à autre chose que `dev`.

Les clients confidentiels ont le secret `secret` (dev uniquement). Les comptes de service :
`hururaa-api` gère organisations, groupes, membres et rôles de tous les clients
(`realm-management` : `manage-organizations`, `manage-clients`, `manage-users`...), les autres
`*-api` ne font que lire les utilisateurs et les organisations.

## 4. Organisation du dépôt

- `backend/` — réacteur Maven, `groupId` `pf.hururaa` : `common-security-starter` (permissions
  par direction extraites de la claim `organization`), `common-events-starter` (`ResourceEvent`
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
- historiser aussi les changements faits dans Keycloak via Hurura'a (membres des groupes, rôles
  qu'ils attribuent), dans un journal propre à Hurura'a : Keycloak attribue ces changements au
  compte de service de l'API, pas à la personne ;
- décider si les administrateurs de direction doivent aussi pouvoir gérer les groupes ;
- faire relayer à la plateforme les événements de toutes les directions (aujourd'hui, un
  utilisateur ne reçoit que ceux des directions dont il est membre).
