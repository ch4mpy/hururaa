# 0005. Délégations stockées par Hurura'a plutôt que dans les jetons

Statut : abandonnée en exploration : administrateurs et gestionnaires sont devenus des rôles de `hururaa-api` portés par des groupes Keycloak (`hururaa.admins`, `hururaa.<préfixe>.product-owners`), lus dans les jetons (voir `pf.hururaa.uaa`).

## Contexte

Hurura'a organise une chaîne de délégation à trois niveaux :

1. les administrateurs Hurura'a de la DSI agissent à tous les niveaux, et seuls désignent les
   administrateurs de chaque direction et changent une application de direction ;
2. les administrateurs d'une direction enregistrent, renomment et désenregistrent ses
   applications, définissent leurs rôles et désignent leurs gestionnaires ;
3. les gestionnaires d'une application définissent ses rôles et ses autres gestionnaires,
   regroupent les rôles dans des groupes de la direction et y affectent les utilisateurs.

Keycloak ne sait pas rattacher un client à une organisation, ni exprimer « gestionnaire de
l'application X » autrement que par un rôle. Un rôle par application sur `hururaa-api`
(`escales.manage`...) serait créé et supprimé avec chaque application, et n'arriverait dans le
jeton qu'au travers d'un groupe de l'organisation : il faudrait un groupe technique par
application et par direction.

## Options

1. Tout en rôles Keycloak (`hururaa-api`) : les trois niveaux sont lus dans le jeton. Aucune
   base à maintenir, mais des rôles dynamiques, des groupes techniques, et une délégation ne prend
   effet qu'au renouvellement du jeton du délégataire.
2. Tout en base Hurura'a, y compris les administrateurs Hurura'a. Plus de rôle Hurura'a du tout :
   le premier administrateur Hurura'a doit être amorcé en base.
3. Administrateurs Hurura'a en rôle Keycloak (`hururaa.admin`, porté par le groupe
   `hururaa.admins` de la DSI, effectif uniquement dans la DSI, `uaa.platform-organization`),
   niveaux direction et application en base Hurura'a (tables `DIRECTION_ADMINS` et
   `APPLICATION_MANAGERS`, auditées par Envers).

## Décision

Option 3, mise en œuvre dans le PoC. Les administrateurs Hurura'a se désignent avec Hurura'a
lui-même : la DSI, qui exploite Hurura'a, en est aussi une direction comme les autres. Hurura'a est
l'une de ses applications, dont les administrateurs et gestionnaires affectent les utilisateurs au
groupe `hururaa.admins` qui porte son rôle `hururaa.admin`.

## Conséquences

- Les délégations de niveaux 2 et 3 prennent effet immédiatement (lues en base à chaque requête,
  à la résolution des variables de chemin sur lesquelles portent les règles d'accès), sans
  reconnexion.
- La gateway ne connaît pas ces délégations : les événements de Hurura'a sont adressés à tous les
  membres de la direction (`ResourceEvent.ALL_MEMBERS`), le frontend relit ensuite par l'API avec
  ses propres droits. Les événements ne portant aucune donnée, seule l'existence d'un changement
  est divulguée aux membres de la direction.
- Les identifiants des délégataires (ids Keycloak) sont en base Hurura'a : un utilisateur supprimé
  de Keycloak ou sorti de la direction y reste listé (par son id) jusqu'à ce qu'on le retire.
- Le rôle `hururaa.admin` porté par un groupe d'une autre direction que la DSI ne donne rien
  (couvert par les tests).
