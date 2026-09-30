# 0005. Délégations stockées par Hurura'a plutôt que dans les jetons

Statut : proposée.

## Contexte

Hurura'a organise une chaîne de délégation à trois niveaux :

1. la plateforme (le SIPF) enregistre les applications, les rattache à une direction et désigne
   les administrateurs de chaque direction ;
2. les administrateurs d'une direction désignent les gestionnaires de chacune de ses
   applications ;
3. les gestionnaires d'une application définissent ses rôles, les regroupent dans des groupes de la
   direction et y affectent les utilisateurs.

Keycloak ne sait pas rattacher un client à une organisation, ni exprimer « gestionnaire de
l'application X » autrement que par un rôle. Un rôle par application sur `hururaa-api`
(`escales.manage`...) serait créé et supprimé avec chaque application, et n'arriverait dans le
jeton qu'au travers d'un groupe de l'organisation : il faudrait un groupe technique par
application et par direction.

## Options

1. Tout en rôles Keycloak (`hururaa-api`) : les trois niveaux sont lus dans le jeton. Aucune
   base à maintenir, mais des rôles dynamiques, des groupes techniques, et une délégation ne prend
   effet qu'au renouvellement du jeton du délégataire.
2. Tout en base Hurura'a, y compris le niveau plateforme. Plus de rôle Hurura'a du tout : le premier
   administrateur de la plateforme doit être amorcé en base.
3. Niveau plateforme en rôles Keycloak (`hururaa.applications.manage`,
   `hururaa.direction-admins.manage`, effectifs uniquement dans l'organisation plateforme
   `uaa.platform-organization`), niveaux direction et application en base Hurura'a (tables
   `DIRECTION_ADMINS` et `APPLICATION_MANAGERS`, auditées par Envers).

## Décision

Option 3, mise en œuvre dans le PoC. Le niveau plateforme reste administrable avec les outils de
Hurura'a lui-même : Hurura'a est une application de la DSI, dont les gestionnaires affectent les
utilisateurs au groupe `sipf` qui porte ses rôles.

## Conséquences

- Les délégations de niveaux 2 et 3 prennent effet immédiatement (lues en base à chaque requête,
  bean `@uaa`), sans reconnexion.
- La gateway ne connaît pas ces délégations : les événements de Hurura'a sont adressés à tous les
  membres de la direction (`ResourceEvent.ALL_MEMBERS`), le frontend relit ensuite par l'API avec
  ses propres droits. Les événements ne portant aucune donnée, seule l'existence d'un changement
  est divulguée aux membres de la direction.
- Les identifiants des délégataires (ids Keycloak) sont en base Hurura'a : un utilisateur supprimé
  de Keycloak ou sorti de la direction y reste listé (par son id) jusqu'à ce qu'on le retire.
- Les rôles `hururaa.*` portés par un groupe d'une autre direction que la plateforme ne donnent
  rien (couvert par les tests).
