# 0008. Hurura'a, application transverse gérée comme les autres

Statut : proposée. Amende [0007](0007-groupes-propres-a-une-application.md) pour Hurura'a.

## Contexte

Hurura'a est une application de la DSI. Ses groupes, `hururaa.admins` dans chaque direction et
`hururaa.<préfixe>.product-owners` par application, portent la délégation. Ils étaient réservés :
non listés, absents des utilisateurs et des filtres, et leurs membres ne changeaient que par des
endpoints de désignation dédiés. Hors de la DSI, ces groupes n'appartenaient à aucune
application, car Hurura'a n'y est pas enregistrée.

Les administrateurs ne voyaient donc pas, dans Hurura'a, qui administre ou gère quoi, alors que
la console Keycloak le montre et permet déjà de changer ces membres (intention 0004).

## Options

1. Garder les groupes réservés à part, et seulement les montrer.
2. Gérer les membres des groupes de Hurura'a avec les règles de la désignation, qui interdisent
   toute élévation.
3. Gérer Hurura'a comme toute application : ses groupes dans toutes les directions, leurs membres
   avec la règle de tout groupe, et la co-gestion des gestionnaires d'application.

## Décision

Option 3 (spec 0004, PA-1 à PA-5) :

- Hurura'a est enregistrée dans la DSI dans tout environnement (changeset Liquibase sans
  contexte). Elle est la seule application **transverse** : elle se consulte depuis toutes les
  directions, et ses groupes `hururaa.*` y appartiennent dans chacune.
- Les membres de ses groupes se changent par l'API des groupes, avec la règle de tout groupe
  (administrateurs de la direction, gestionnaires de Hurura'a). `hururaa.<préfixe>.product-owners`
  est aussi géré par les gestionnaires de l'application `<préfixe>`.
- Ce qu'attribuent ses groupes reste figé : pas de création ni de suppression de groupe ou de rôle
  `hururaa.*`, pas de changement des rôles qu'ils attribuent (`RESERVED_NAME`).
- Le dernier administrateur Hurura'a (`hururaa.admins` de la DSI) ne peut être retiré
  (`LAST_HURURAA_ADMIN`). Ce garde-fou ne vaut pas pour les autres directions, qu'un administrateur
  Hurura'a peut toujours rattraper.
- Les endpoints de désignation (`PUT/DELETE .../admins/{userId}`, `.../managers/{userId}`) sont
  supprimés : la désignation passe par le groupe.

## Conséquences

- **Écart accepté avec l'intention 0004.** Sa contrainte « pas d'élévation » ne tient plus : un
  administrateur de direction nomme d'autres administrateurs de sa direction, et un gestionnaire
  de Hurura'a nomme des administrateurs dans toutes les directions, administrateurs Hurura'a
  compris. Le PO l'a accepté en connaissance de cause (PA-3), parce que c'est ce que permet déjà la
  console Keycloak et que cela correspond aux attributions réelles. Être gestionnaire de Hurura'a
  revient donc à pouvoir devenir administrateur Hurura'a.
- Un gestionnaire de Hurura'a est délégué de toutes les directions : il en consulte les membres,
  les groupes et l'historique.
- Un changement des membres d'un groupe de Hurura'a est journalisé comme une désignation
  (catégorie `DELEGATION`), avec le nom du groupe.
- Les politiques SEC-5, DEL-1, DEL-4, DEL-5 et DEL-6 sont réécrites en conséquence.
