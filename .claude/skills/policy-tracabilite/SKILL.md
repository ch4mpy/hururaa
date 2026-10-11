---
name: policy-tracabilite
description: Hurura'a traceability policy (rules TRA-n). Use whenever creating or modifying code that changes permissions in Keycloak (directions, administrators, managers, application roles, groups, group roles, group members), the permission journal or history, or the events the API publishes, and when writing a spec, plan or review that touches them.
---

# Politique : traçabilité des changements de permissions

- Responsable : ch4mpy
- Périmètre : tout changement de permissions que Hurura'a fait dans Keycloak, son journal, l'historique des permissions et les événements publiés.
- Sources : `README.md` §2 (historique et journal), `CLAUDE.md` (`history`, `journal`, `events.DirectionEvents`, `common-events-starter`).

Citer les règles par leur identifiant (`TRA-n`) dans les specs, les plans et les revues. Une
règle ne change que par une PR sur ce fichier, revue par son responsable ; une exception exige une
décision (`docs/decisions/`).

## Règles

1. **TRA-1 Tout changement effectif est journalisé.** Chaque changement de permissions fait dans Keycloak (direction créée, administrateurs et gestionnaires désignés ou révoqués, rôles d'application, groupes, rôles de groupe, membres de groupe) est consigné par `PermissionJournal` dans `PERMISSION_EVENTS`, avec son auteur et son horodatage. *Pourquoi :* Keycloak attribue ces changements au compte de service de l'API, pas à la personne.
2. **TRA-2 Et seulement s'il est effectif.** Les méthodes d'écriture des services Keycloak (`GroupService.addMember`… `ClientRoleService.save`/`delete`) renvoient si elles ont changé quelque chose et n'appellent pas Keycloak sinon ; le contrôleur ne journalise que dans ce cas. *Pourquoi :* les opérations sont idempotentes ; le journal ne contient que des changements réels.
3. **TRA-3 Journal et log.** À côté de l'appel au journal, le contrôleur garde son `log.info` du changement. *Pourquoi :* les journaux applicatifs (Loki) sont une seconde piste d'audit.
4. **TRA-4 Journal en ajout seul.** `PERMISSION_EVENTS` n'est jamais modifiée ni purgée par l'application ; l'état courant se lit toujours dans Keycloak. *Pourquoi :* c'est une trace, pas un état.
5. **TRA-5 Historique complet.** L'historique d'une direction (et de ses applications et groupes) fusionne l'audit Envers des applications et le journal ; une nouvelle catégorie de changement y apparaît, avec sa `PermissionChangeCategory`. *Pourquoi :* « qui a changé quoi, et quand » se lit au même endroit.
6. **TRA-6 Événements à toute la direction.** Chaque événement publié par l'API est adressé à tous les membres de la direction concernée (`DirectionEvents`, audience `ResourceEvent.ALL_MEMBERS`), publié après le commit. *Pourquoi :* la gateway ne vérifie pas les rôles de Hurura'a ; un écran ouvert se met à jour.

## Vérifier

- TRA-1, TRA-3 : chaque appel d'écriture d'un service Keycloak dans un contrôleur est suivi d'un appel à `PermissionJournal` et d'un `log.info`, sous la même condition.
- TRA-2 : les tests des contrôleurs vérifient qu'un changement sans effet n'est pas journalisé.
- TRA-5 : `./mvnw -pl hururaa-api test -Dtest=PermissionHistoryServiceTest`.

## Exceptions

Aucune.
