# Intention : Hurura'a, une application comme les autres

- Auteur : ch4mpy (PO et développeur)
- Nature : évolution
- Source : conversation
- Spec nécessaire : oui
- Statut : proposée
- Ouverte le : 2026-10-15
- Décidée le : —
- Remplace : —

## Problème

Hurura'a est une application de la DSI, mais ses groupes et ses rôles (`hururaa.admins`,
`hururaa.<préfixe>.product-owners`, `hururaa.direction.admin`,
`hururaa.application.<préfixe>.manage`) n'apparaissent nulle part dans l'interface : ni sur les
utilisateurs (recherche de membres, filtres), ni sur les applications (liste, groupes, rôles).

Ce que cela coûte :

- **administrateurs Hurura'a** : impossible de voir, depuis Hurura'a lui-même, qui administre ou
  gère quoi ; il faut passer par la console Keycloak ;
- **administrateurs de direction** : la recherche d'utilisateurs de leur direction ne montre pas
  qui en est administrateur ou gestionnaire d'une application ;
- **gestionnaires d'application** : leurs co-gestionnaires n'apparaissent pas comme membres d'un
  groupe de l'application ;
- **incohérence du modèle** : le README présente Hurura'a comme « une application de la DSI », la
  délégation comme « des rôles et des groupes Keycloak comme les permissions de toute
  application », mais l'interface la traite à part.

## Résultat attendu

- Hurura'a figure parmi les applications de la DSI, avec ses rôles et ses groupes, comme toute
  application.
- Les groupes et rôles de Hurura'a apparaissent sur les utilisateurs (recherche de membres,
  filtres par groupe, application et rôle), dans toutes les directions où ils existent
  (`hururaa.admins` et `hururaa.<préfixe>.product-owners` de chaque direction).
- Ses groupes et leurs membres se gèrent par les mêmes écrans que ceux des autres applications,
  avec les mêmes règles d'accès, sous les garde-fous listés ci-dessous.
- Réussite : un administrateur Hurura'a, un administrateur de direction et un gestionnaire
  retrouvent, chacun dans son périmètre, les administrateurs et gestionnaires par les écrans
  d'applications et d'utilisateurs, et peuvent les changer par les écrans de groupes, sans console
  Keycloak.

## Utilisateurs et systèmes concernés

- Administrateurs Hurura'a, administrateurs de direction, gestionnaires d'application.
- hururaa-api, frontend Angular, Keycloak (royaume `public-facing`, groupes et rôles réservés),
  PostgreSQL (données de dev des applications, si Hurura'a doit y figurer).

## Contraintes

- Pas d'auto-exclusion : on ne peut pas retirer le dernier administrateur Hurura'a de la DSI.
- Pas d'élévation : la gestion par les écrans de groupes ne permet à personne de s'octroyer un
  niveau de la chaîne de délégation qu'il n'a pas déjà le droit d'attribuer.
- Rôles réservés figés : les rôles `hururaa.*` ne se créent, ne se renomment et ne se suppriment
  pas à la main ; seuls les groupes et leurs membres se gèrent.
- Traçabilité identique : tout changement passe par le journal des permissions comme une
  désignation ou une révocation (politique TRA).
- Contredit les règles SEC-5 (noms réservés intouchables, groupes réservés non listés) et DEL-5
  (membres des groupes réservés changés seulement par désignation et révocation), ainsi que le
  README §2 : à réviser en spec, avec une décision au registre `docs/decisions/`.
- Risque élevé (règles d'accès, noms réservés, peut-être configuration Keycloak et données) : revue
  du tech lead.

## Questions ouvertes

- Nouvelle rédaction de SEC-5 et DEL-5, et décision au registre (nouvelle décision, ou
  amendement de 0007) — spec, PO.
- Dans les autres directions que la DSI, `hururaa.admins` et `hururaa.<préfixe>.product-owners`
  apparaissent-ils sous Hurura'a (application de la DSI) ou sous l'application concernée de la
  direction ? — spec, PO.
- Les écrans de désignation des administrateurs et gestionnaires restent-ils, ou deviennent-ils
  de simples liens vers les groupes correspondants (« une fonctionnalité vit à un seul endroit ») ?
  — spec, PO.
- Hurura'a doit-il devenir une application enregistrée (ligne en base, préfixe `hururaa`
  aujourd'hui réservé) ou rester un cas particulier présenté comme une application ? — spec.
