---
name: policy-delegation
description: Hurura'a delegation model policy (rules DEL-n). Use whenever a change touches who can do what in Hurura'a — administrators, managers, directions, applications, groups, roles, their access in the API or the UI — and when writing a spec, plan or review about permissions or the delegation chain.
---

# Politique : modèle de délégation

- Responsable : ch4mpy
- Périmètre : qui peut faire quoi dans Hurura'a, côté API comme côté interface.
- Sources : `README.md` §1 et §2, `package-info` de `pf.hururaa.uaa`, décisions 0005 et 0007.

Citer les règles par leur identifiant (`DEL-n`) dans les specs, les plans et les revues. Une
règle ne change que par une PR sur ce fichier, revue par son responsable ; une exception exige une
décision (`docs/decisions/`).

## Règles

1. **DEL-1 Trois niveaux, pas d'autres.** Administrateurs Hurura'a (`hururaa.direction.admin` détenu dans la DSI, `uaa.platform-organization` : ils agissent dans toutes les directions et eux seuls créent les directions et désignent leurs administrateurs) ; administrateurs de direction (`hururaa.direction.admin` dans leur direction) ; gestionnaires d'application (`hururaa.application.<préfixe>.manage` dans la direction de l'application). Dire « administrateurs Hurura'a », jamais « plateforme ». *Pourquoi :* c'est le modèle que la preuve de concept démontre.
2. **DEL-2 Un rôle ne vaut que dans sa direction.** Les rôles de Hurura'a ne donnent rien hors de la direction où ils sont détenus (sauf `hururaa.direction.admin` dans la DSI). *Pourquoi :* la délégation se fait direction par direction.
3. **DEL-3 Effet au renouvellement du jeton.** Les droits se lisent dans le jeton : donnés ou retirés, ils prennent effet au renouvellement du jeton de l'utilisateur. Une spec ou une interface ne promet pas d'effet immédiat. *Pourquoi :* Hurura'a ne stocke pas les délégations (voir décision 0005).
4. **DEL-4 Un groupe appartient à une application.** Un groupe se nomme `<préfixe>.<nom>`, appartient à l'application de ce préfixe dans sa direction, n'attribue que ses rôles et est géré par ses gestionnaires et les administrateurs de la direction ; un groupe créé hors de Hurura'a n'est géré que par les administrateurs de la direction (`GROUP_WITHOUT_APPLICATION`). *Pourquoi :* Keycloak ne sait rattacher ni un client à une organisation ni un groupe à un client (décision 0007).
5. **DEL-5 Délégation par groupes réservés.** Administrateurs et gestionnaires sont les membres des groupes réservés `hururaa.admins` (par direction) et `hururaa.<préfixe>.product-owners` (par application), créés et supprimés par Hurura'a ; leurs membres ne changent que par la désignation et la révocation. *Pourquoi :* la délégation est faite de rôles et de groupes Keycloak comme les permissions de toute application.
6. **DEL-6 Une application reste dans sa direction.** La direction d'une application est immuable ; une application ne peut être désenregistrée tant qu'elle a des groupes (`APPLICATION_HAS_GROUPS`). *Pourquoi :* ses groupes et ses gestionnaires vivent dans cette direction.
7. **DEL-7 Consultation ouverte aux délégués.** Consulter une direction (administrateurs, groupes, membres, historique) ou une application (rôles, gestionnaires) est ouvert à ses administrateurs et gestionnaires, à tout niveau ; un simple membre sans rôle de Hurura'a ne voit pas l'administration. *Pourquoi :* chacun voit ce qu'il délègue ou ce qui lui est délégué.
8. **DEL-8 L'interface reflète les règles de l'API.** Menus et actions se règlent sur `DelegationsService` (`/me/delegations`), miroir des règles de l'API ; l'interface ne donne jamais plus que l'API et ne remplace jamais une règle de l'API. *Pourquoi :* une seule source de vérité, appliquée par l'API.

## Vérifier

- DEL-1 à DEL-7 : les tests d'accès des contrôleurs (voir `SEC-7`) couvrent les trois niveaux et un membre sans rôle.
- DEL-8 : toute nouvelle règle d'accès de l'API a son équivalent dans `DelegationsService` et ses tests.

## Exceptions

Aucune.
