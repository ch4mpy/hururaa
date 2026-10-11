# Contribuer à Hurura'a

Hurura'a suit un cycle de développement « AI-native » inspiré de
[The AI-native SDLC playbook](https://claude.com/resources/articles/the-ai-native-sdlc-playbook) :
Claude rédige et vérifie, les humains décident. Ce document est l'aide-mémoire du contributeur ;
il s'enrichit à mesure que chaque étape est mise en place.

## En bref

Chaque changement produit, étape par étape, un document versionné que l'étape suivante lit. Le
journal git et les PR constituent la trace d'audit.

```text
idée ─► intention ─► spec    ─►  plan    ─► code + preuves ─► PR revue    ─► production
        intent.md    spec.md     plan.md    tests, build      REVIEW.md
        (PO)         (PO)        (dev)      (Claude)          (code owner)
```

1. **Planifier** : une idée ou un bug devient une intention (`intent.md`), acceptée ou refusée par le PO.
2. **Concevoir** : l'intention devient une spec (`spec.md`) qui applique les règles du projet et signale les points d'attention.
3. **Construire** : la spec devient un plan (`plan.md`) validé par un développeur avant toute ligne de code.
4. **Tester** : Claude vérifie son propre travail ; on ne voit que ce qui passe.
5. **Livrer** : revue de la PR (Claude puis un humain), puis mise en production derrière des garde-fous.
6. **Maintenir** : les anomalies détectées reviennent sous forme de correctifs ou de nouvelles intentions.

Les intentions et leurs suites vivent dans [docs/intent/](docs/intent/), les décisions
d'architecture dans [docs/decisions/](docs/decisions/).

## Qui fait quoi

| Rôle               | Décide                                                                                                       | Aujourd'hui |
| ------------------ | ------------------------------------------------------------------------------------------------------------ | ----------- |
| PO (product owner) | accepte ou refuse les intentions, tranche les points d'attention des specs, trie les constats de maintenance | ch4mpy      |
| Développeur        | valide les plans, pilote les sessions Claude Code, relit le résultat                                         | ch4mpy      |
| Code owner         | approuve les PR (Claude n'approuve jamais les siennes)                                                       | ch4mpy      |
| Astreinte          | trie les alertes et les propositions automatiques                                                            | ch4mpy      |

## Prérequis

- [Claude Code](https://claude.com/claude-code) avec un abonnement claude.ai (aucune clé d'API n'est nécessaire).
- Git, avec un accès SSH à `github.com:ch4mpy/hururaa`.
- [GitHub CLI](https://cli.github.com/) (`winget install GitHub.cli`), connecté : `gh auth login` (portée `repo`).
- [jq](https://jqlang.org/) (`winget install jqlang.jq`), utilisé par les hooks Claude Code.
- Sous Windows, `gh` et `jq` doivent répondre dans Git Bash comme dans PowerShell (`gh --version`, `jq --version`).
- Les skills Claude Code `sdlc-*` dans `~/.claude/skills/` (mode de distribution en cours de définition).
- Pour développer : l'environnement décrit dans le [README §5](README.md#5-déploiement-en-local).

## Étape 1 — Planifier

### Signaler un bug ou demander une évolution

Décrivez le **problème**, pas la solution : qui est gêné, comment, à quelle fréquence. La solution
se décide aux étapes suivantes. Trois façons de faire, au choix.

#### Dans Claude Code, sur le dépôt cloné

Terminal, extension VS Code ou onglet *Code* de Claude Desktop, ouvert à la racine du dépôt :

```text
/sdlc-intent draft Avant de modifier un groupe, un gestionnaire d'application ne voit pas qui l'a modifié en dernier ni quand
```

```text
/sdlc-intent draft bug : en anglais, le bouton de confirmation de la suppression d'un groupe reste libellé en français
```

Claude lit les intentions et décisions existantes, vous pose quelques questions (trois tours au
plus), puis montre le brouillon `docs/intent/NNNN-<titre>/intent.md`. Une fois le brouillon
satisfaisant et après votre confirmation, il le pousse sur une branche `intent/NNNN-<titre>` et
ouvre une PR étiquetée `intent`. Le PO décide ensuite.

#### Sans Claude Code : Claude Desktop (discussion) ou claude.ai dans un navigateur

Collez cette invite dans une nouvelle conversation, en remplaçant la dernière ligne :

```text
Aide-moi à rédiger une demande d'évolution ou un signalement de bug pour Hurura'a, une application
qui gère les permissions des applications (Keycloak) en déléguant cette gestion direction par
direction. Ses utilisateurs sont les administrateurs Hurura'a, les administrateurs de direction,
les gestionnaires d'application et les membres des directions.

Interroge-moi, quelques questions à la fois et en trois tours au plus, pour remplir ces rubriques :
- Nature : évolution ou bug ;
- Problème : qui est gêné, comment cela se manifeste, à quelle fréquence, ce que cela coûte ;
  pour un bug : étapes pour reproduire, comportement attendu, comportement observé, environnement ;
- Résultat attendu : ce qui sera observable une fois livré, et comment on saura que c'est réussi ;
- Personnes et parties concernées ;
- Contraintes connues ;
- Questions ouvertes.

Décris le besoin, jamais la solution : si j'en propose une, garde-la comme « solution évoquée »
dans les questions ouvertes. N'invente aucune réponse : ce qui reste flou va dans les questions
ouvertes. À la fin, rends le résultat en Markdown, une rubrique par titre, prêt à être recopié
dans un formulaire.

Mon idée : <votre idée ou le bug constaté, en une ou deux phrases>
```

Recopiez ensuite le résultat dans le formulaire
[Nouvelle intention](https://github.com/ch4mpy/hururaa/issues/new?template=intention.yml) (un
compte GitHub suffit). Un développeur ou le PO la transforme en intention avec
`/sdlc-intent draft #<numéro de l'issue>` ; l'issue sera fermée par la PR.

#### Sans Claude

Remplissez directement le formulaire
[Nouvelle intention](https://github.com/ch4mpy/hururaa/issues/new?template=intention.yml).

### Décider d'une intention (PO)

1. Lisez la PR étiquetée `intent` ([liste](https://github.com/ch4mpy/hururaa/pulls?q=is%3Apr+label%3Aintent)) ;
   commentez-la si quelque chose manque.
2. Dans Claude Code, sur le dépôt cloné :

   ```text
   /sdlc-intent accept 0001
   ```

   Claude demande quelles questions ouvertes vous tranchez maintenant et, pour un bug, si une spec
   est nécessaire, puis fusionne la PR après confirmation. Pour refuser :

   ```text
   /sdlc-intent reject 0001 Déjà couvert par l'historique des permissions de l'application
   ```

   La PR est fermée avec ce motif ; l'intention n'arrive jamais sur `master`.
3. Pour suivre les délais et le taux d'acceptation : `/sdlc-intent stats`.

## Aide-mémoire

| Commande                                 | Étape     | Qui                     |
| ---------------------------------------- | --------- | ----------------------- |
| `/sdlc-intent draft <idée>`              | Planifier | tout contributeur       |
| `/sdlc-intent draft #<issue>`            | Planifier | développeur ou PO       |
| `/sdlc-intent accept <NNNN>`             | Planifier | PO                      |
| `/sdlc-intent reject <NNNN> <motif>`     | Planifier | PO                      |
| `/sdlc-intent stats`                     | Planifier | PO                      |
