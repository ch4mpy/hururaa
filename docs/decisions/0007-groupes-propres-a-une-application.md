# 0007. Groupes propres à une application

Statut : proposée.

## Contexte

Un groupe était un groupe d'organisation Keycloak de nom libre, pouvant attribuer les rôles de
plusieurs applications de sa direction. Ses membres ne pouvaient alors être changés, ou le groupe
supprimé, que par un administrateur de la direction ou par un gestionnaire de **toutes** les
applications dont il attribuait des rôles.

Les utilisateurs ne s'y retrouvent pas : ils raisonnent application par application, et un
groupe mêlant plusieurs applications leur est étranger.

## Options

1. Garder des groupes transverses, en documentant mieux la règle.
2. Rattacher chaque groupe à une application, en le stockant dans la base Hurura'a (table
   `APPLICATION_GROUPS`), son nom restant libre.
3. Rattacher chaque groupe à une application par son nom : `<préfixe>.<nom>` (`escales.agent`,
   `hururaa.admin`), le préfixe étant celui des clients Keycloak de l'application.

## Décision

Option 3. Le groupe reste un groupe d'organisation Keycloak de la direction de l'application
(c'est ce qui range ses rôles sous la direction dans les jetons), et son application se lit dans
son nom, sans ambiguïté puisque les préfixes ne contiennent pas de point. Il se crée sous son
application (`POST /directions/{direction}/applications/{applicationId}/groups`, avec le nom sans
le préfixe) et reste adressé par son nom complet sous sa direction
(`/directions/{direction}/groups/{group}`).

## Conséquences

- Un groupe n'attribue que les rôles de son application ; ses rôles et ses membres sont gérés par
  les gestionnaires de l'application et par les administrateurs de la direction.
- Rien à stocker de plus : le nom du groupe, visible dans la console Keycloak et dans les jetons
  (`groups: ["/escales.agent"]`), dit à quelle application il appartient.
- Un groupe créé hors de Hurura'a dont le nom ne correspond à aucune application de sa direction
  reste listé, mais seuls les administrateurs de la direction le gèrent, et Hurura'a ne lui fait
  attribuer aucun rôle (`GROUP_WITHOUT_APPLICATION`).
- Une application ne change de direction, ni n'est désenregistrée, tant qu'elle y a des groupes
  (`APPLICATION_HAS_GROUPS`).
- Le journal rattache à l'application les événements de ses groupes (création, suppression,
  membres) : l'historique d'une application les montre.
- Les groupes existants doivent être renommés selon la convention (dans le royaume de dev :
  `hururaa.admin`, `te-fenua.agent`, `escales.agent`, `anahei.agent`).
