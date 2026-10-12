# Intentions

Étape 1 (Planifier) du [cycle de développement AI-native](https://claude.com/resources/articles/the-ai-native-sdlc-playbook) :
tout changement commence par une **intention**, l'énoncé court d'un besoin que le PO accepte ou
refuse avant que quiconque ne le spécifie, le planifie ou le code. Mode d'emploi :
[CONTRIBUTING.md](../../CONTRIBUTING.md).

Un dossier numéroté par changement : `NNNN-<titre>/` contient `intent.md`, puis `spec.md`
(étape 2) et `plan.md` (étape 3). Une intention acceptée n'est jamais réécrite : un besoin qui
change donne une nouvelle intention qui la remplace.

## Contexte du projet

<!-- Lu par les skills sdlc-*. À tenir court et à jour. -->

- Langue : français
- Personas et rôles : administrateurs Hurura'a, administrateurs de direction, gestionnaires
  d'application, membres d'une direction — décrits dans le [README §2](../../README.md#2-chaîne-de-délégation)
- Modules / systèmes : gateway (BFF OAuth2), hururaa-api, frontend Angular, Keycloak (royaume
  `public-facing`), RabbitMQ, PostgreSQL, starters communs (security, events), démo (`deploy/`)
- Contraintes permanentes : [docs/decisions/](../decisions/) (registre des décisions) et les
  politiques
- Politiques : skills du dépôt [.claude/skills/policy-*](../../.claude/skills/) — `SEC` sécurité de
  l'API, `TRA` traçabilité, `DEL` délégation, `UX` interface et i18n
- Branche de base : `master`
- PO : ch4mpy
- Tech lead : ch4mpy
- Modèle d'intention : [_modele-intention.md](_modele-intention.md)
- Modèle de spec : [_modele-spec.md](_modele-spec.md)
- Modèle de plan : [_modele-plan.md](_modele-plan.md)
- Branches : `fix/NNNN-<titre>` pour un bug, `feat/NNNN-<titre>` sinon ; une PR par changement
  (plan puis code, un commit par étape du plan)
- Risque élevé (revue du tech lead requise) : le changement touche l'authentification, les règles
  d'accès, les noms réservés, le modèle de données (Liquibase), la configuration Keycloak ou le
  déploiement (`deploy/`)
- Formulaire d'issue : [.github/ISSUE_TEMPLATE/intention.yml](../../.github/ISSUE_TEMPLATE/intention.yml)
- Statuts :

  | Clé | Libellé | Positionné par |
  | --- | --- | --- |
  | proposed | proposée | étape 1, à l'ouverture de la PR de l'intention |
  | accepted | acceptée | le PO (PR fusionnée) |
  | rejected | refusée | le PO (PR fermée, jamais sur `master`) |
  | specified | spécifiée | étape 2, une fois `spec.md` approuvée |
  | planned | planifiée | étape 3, une fois `plan.md` approuvé |
  | delivered | livrée | étape 5, une fois le changement fusionné et déployé |
  | abandoned | abandonnée | remplacée par une autre intention, ou abandonnée |

## Proposer et décider

- **Proposer** : ouvrir une issue avec le formulaire *Nouvelle intention*, ou lancer
  `/sdlc-intent draft <idée>` (ou `draft #<issue>`). L'intention arrive sous forme de PR
  étiquetée `intent`.
- **Décider** (PO) : `/sdlc-intent accept <NNNN>` fusionne la PR ; `/sdlc-intent reject <NNNN> <motif>`
  la ferme avec le motif.
- **Mesurer** : `/sdlc-intent stats`.

## Index

| N° | Intention | Nature | Statut | Ouverte le | Décidée le |
| --- | --- | --- | --- | --- | --- |
| [0001](0001-toast-erreur-ouverture-application/intent.md) | Toast d'erreur à l'ouverture de l'application | bug | spécifiée | 2026-10-11 | 2026-10-11 |
