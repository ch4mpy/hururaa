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
- Git, avec un accès SSH à `github.com:ch4mpy/hururaa` (clé chargée dans un agent SSH), ou en
  HTTPS via `gh auth setup-git`.
- [GitHub CLI](https://cli.github.com/) (`winget install GitHub.cli`), connecté : `gh auth login` (portée `repo`).
- [jq](https://jqlang.org/) (`winget install jqlang.jq`), utilisé par les hooks Claude Code.
- Sous Windows, `gh` et `jq` doivent répondre dans Git Bash comme dans PowerShell (`gh --version`, `jq --version`).
- Les skills Claude Code `sdlc-*` dans `~/.claude/skills/`, l'agent `~/.claude/agents/verifier.md`,
  les hooks `~/.claude/hooks/protect-paths.sh`, `freeze-tests.sh`, `format-on-edit.sh` et
  `release-gate.sh`, et le
  lanceur d'evals `~/.claude/scripts/run-evals.sh` (mode de distribution en cours de définition).
- Pour les evals : la commande `claude` (Claude Code en ligne de commande, connecté à votre
  abonnement) ; le lanceur la trouve aussi dans `~/.local/bin`.
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

## Étape 2 — Concevoir

### Comment une intention devient une spec ?

Une intention acceptée devient une spec (`spec.md`, à côté de `intent.md`) : ce qui change et les
contrats observables (comportement par rôle, API, erreurs, écrans, données, critères
d'acceptation), jamais la façon de le coder. Pour un bug, le PO peut s'en passer à l'acceptation.

Dans Claude Code, sur le dépôt cloné :

```text
/sdlc-spec draft 0001
```

Claude lit l'intention, les décisions, toutes les politiques et le code concerné, répond aux
questions de l'intention que le code tranche (preuves à l'appui), vous pose au plus deux séries de
questions sur les choix produit, puis montre le brouillon. Après votre confirmation, il ouvre une
PR étiquetée `spec`.

### Comprendre une spec sans Claude Code (PO)

Ouvrez la PR `spec` sur GitHub ([liste](https://github.com/ch4mpy/hururaa/pulls?q=is%3Apr+label%3Aspec)),
copiez le contenu de `spec.md`, puis dans Claude Desktop ou claude.ai :

```text
Je suis le PO de Hurura'a, une application qui gère les permissions des applications (Keycloak)
en déléguant cette gestion direction par direction. Explique-moi la spec ci-dessous sans jargon
technique :
- ce qui change pour chaque rôle (administrateurs Hurura'a, administrateurs de direction,
  gestionnaires d'application, membres d'une direction) ;
- pour chaque point d'attention (PA-n) : ce que je dois décider, les conséquences de chaque
  option, et ce que tu me poserais comme question avant de trancher ;
- les critères d'acceptation que je pourrai vérifier moi-même une fois le changement livré.

<collez ici le contenu de spec.md>
```

### Que faire d'un point d'attention ?

Un point d'attention (`PA-n`) signale un conflit entre règles, une règle impossible à respecter,
un choix produit ou un risque. Claude ne le tranche jamais. Chacun indique qui décide :

- le **PO** pour un choix produit ;
- le **responsable de la politique** pour une règle à adapter (l'exception passe par une décision
  dans `docs/decisions/`) ;
- le **tech lead** pour un risque technique. Une spec à risque élevé (authentification, règles
  d'accès, noms réservés, modèle de données, Keycloak, déploiement) exige sa revue.

Répondez dans la PR, puis approuvez :

```text
/sdlc-spec approve 0001
```

Claude reprend chaque point encore ouvert, consigne les décisions et refuse d'approuver tant
qu'il en reste un. Une fois la PR fusionnée, l'intention passe à *spécifiée*. Délais et
retouches : `/sdlc-spec stats`.

### Où sont les règles ?

Dans les skills du dépôt `.claude/skills/policy-*`, chaque règle avec son identifiant, cité dans
les specs, les plans et les revues :

| Politique | Préfixe | Porte sur |
| --- | --- | --- |
| [Sécurité de l'API](.claude/skills/policy-securite-api/SKILL.md) | `SEC` | règles d'accès, 404, noms réservés, erreurs, tests d'accès |
| [Traçabilité](.claude/skills/policy-tracabilite/SKILL.md) | `TRA` | journal des permissions, logs, historique, événements |
| [Délégation](.claude/skills/policy-delegation/SKILL.md) | `DEL` | les trois niveaux, groupes, effet des droits |
| [Interface et i18n](.claude/skills/policy-ux-i18n/SKILL.md) | `UX` | traductions, messages, charte, navigation |

Claude les applique de lui-même quand un travail les concerne. Pour changer une règle : une PR sur
le fichier de la politique, revue par son responsable.

## Étape 3 — Construire

Rien ne se code sans plan approuvé. Le plan (`plan.md`, à côté de la spec) dit comment : fichiers,
ordre de travail, risques, et la preuve de chaque critère d'acceptation. Un changement = une branche
(`fix/NNNN-…` ou `feat/NNNN-…`) = une PR : le plan en premier commit, puis un commit par étape.

### Comment démarrer un développement ?

Dans Claude Code, sur le dépôt cloné, passez en *plan mode* (`Shift+Tab` jusqu'à « plan mode »),
puis :

```text
/sdlc-plan draft 0001
```

Claude lit la spec, les politiques qu'elle cite, le code et la documentation des bibliothèques en
jeu, vous pose au plus deux séries de questions techniques, puis vous présente le plan. Approuvez-le
ou demandez des corrections : c'est votre validation. Claude crée alors la branche, y commite
`plan.md` et, après confirmation, ouvre une PR brouillon étiquetée `plan`.

Passez ensuite en mode automatique (`Shift+Tab`) et lancez :

```text
/sdlc-plan implement 0001
```

Claude suit le plan étape par étape (vérification, puis commit), consigne tout écart dans
`plan.md` et s'arrête pour vous demander quand un écart change la portée, un risque ou une preuve.
À la fin, l'agent `verifier` contrôle chaque critère d'acceptation dans un contexte neuf et son
rapport est déposé dans la PR, qui reste en brouillon jusqu'à la revue (étape 5).

### Valider un plan (développeur ; tech lead si le risque de la spec est élevé)

Vérifiez que chaque exigence de la spec a ses fichiers, que chaque critère d'acceptation a sa
preuve, que l'ordre permet de vérifier chaque étape, et que les risques ont une parade. Pour un
regard extérieur, dans Claude Desktop ou claude.ai, avec le contenu de `spec.md` et de `plan.md` :

```text
Voici la spec et le plan d'implémentation d'un changement de Hurura'a (Spring Boot, Angular,
Keycloak). Critique le plan sans le réécrire :
- exigences (EX-n) de la spec sans fichier ni étape dans le plan ;
- critères d'acceptation (CA-n) sans preuve, ou avec une preuve qui ne prouve pas vraiment ;
- étapes trop grosses pour être vérifiées seules, ou dans un ordre qui casse le build ;
- risques oubliés ou sous-estimés, en particulier sur la sécurité.

<spec.md>

<plan.md>
```

### Fichiers protégés

Un hook refuse à Claude les modifications de ces chemins (`.claude/protected-paths`) :

| Chemin | Règle | Pourquoi |
| --- | --- | --- |
| `frontend/angular/projects/api/**` | jamais | code généré : `npm run api` |
| `backend/openapi/out/**` | jamais | specs générées : `./build-openapi.sh` |
| `backend/hururaa-api/src/main/resources/db/changelog/changes/**` | pas de modification d'un fichier existant | changeset déjà appliqué : en ajouter un nouveau |
| `.env`, `secrets/**`, `certs/**` | jamais | secrets et certificats générés (Claude ne peut pas non plus lire `.env` ni `secrets/`) |

Un second hook formate avec Prettier les fichiers du front que Claude modifie
(`.claude/formatters`). Pour changer ces listes : une PR sur le fichier concerné.

## Étape 4 — Tester

Chaque session vérifie son propre travail : on ne voit que ce qui passe.

### Vérifier son travail

Une seule commande, depuis la racine du dépôt (Git Bash sous Windows) :

```bash
./verify.sh            # backend et front, complet (≈ quelques minutes)
./verify.sh --quick    # sans clean ni build localisé, pour les vérifications intermédiaires
```

Elle choisit le JDK de `.sdkmanrc` et se termine par `VERIFY OK`, ou par `VERIFY KO: <étape>`.
Claude la lance avant de déclarer un travail fini et colle sa dernière ligne dans la PR. C'est
l'équivalent local de la CI.

### Comment corriger un bug (test d'abord) ?

Avec `/sdlc-plan` (voir l'étape 3), le plan d'un bug commence toujours par un test qui le
reproduit et échoue. Une fois ce test commité, il est **gelé** : Claude doit corriger le code, pas
le test. S'il essaie, le hook refuse :

```text
Frozen test: frontend/angular/projects/hururaa/src/app/core/http-error.interceptor.spec.ts
reproduces the bug being fixed; fix the code, not the test.
```

La liste des tests gelés est locale (`.claude/frozen-tests.local`, ignoré par git) ; Claude la
supprime à la fin. Modifier un test gelé exige un écart consigné dans le plan.

### Je modifie CLAUDE.md, une politique ou un skill : lancer les evals

Les evals rejouent de vraies tâches pour vérifier que Claude se comporte toujours comme attendu
(politiques citées, chemins protégés respectés, test gelé intact, textes traduits). Elles sont
**obligatoires** pour une PR qui touche `CLAUDE.md`, `.claude/**`, `docs/intent/_modele-*` ou un
skill `sdlc-*`. Dans Claude Code :

```text
/sdlc-evals run
```

Hors de Claude Code (terminal, ou onglet *Code* de Claude Desktop), depuis le dépôt :

```bash
bash ~/.claude/scripts/run-evals.sh
```

Collez la dernière ligne (`EVALS 4/4 OK`) et le tableau du rapport dans la PR. Une erreur de
Claude constatée en session ou en revue devient une nouvelle eval : `/sdlc-evals add <ce qu'il
faut garder>`. Détails : [evals/README.md](evals/README.md).

## Étape 5 — Livrer

La revue se fait dans les deux sens : Claude relit chaque PR de la même façon et corrige ce qu'on
lui signale ; un humain juge l'intention et le risque, et lui seul approuve. Claude n'approuve
jamais une PR.

### Revoir une PR

Dans Claude Code, sur le dépôt cloné :

```text
/sdlc-review review 18
```

Claude applique les passes de [REVIEW.md](REVIEW.md) (bugs, sécurité, conformité à la spec, au plan
et aux politiques) et dépose **un** commentaire : les constats classés, « Important » d'abord, chacun
avec sa règle (`SEC-4`, `EX-2`…). Pour faire corriger les constats et les commentaires ouverts :

```text
/sdlc-review address 18
```

**Votre part de relecteur** : l'intention (la PR résout-elle le problème de l'intention ?), le
risque, et les vérifications manuelles que le plan renvoie à la revue. Hors de Claude Code, pour un
regard sur l'intention, dans Claude Desktop ou claude.ai, avec le contenu de `intent.md` et la sortie
de `gh pr diff 18` :

```text
Voici une intention (le besoin) et le diff de la PR censée y répondre, pour Hurura'a (Spring Boot,
Angular, Keycloak). Sans relire le style : la PR résout-elle le problème décrit ? Que pourrait-elle
casser pour les utilisateurs (administrateurs Hurura'a, administrateurs de direction, gestionnaires
d'application, membres) ? Que faudrait-il vérifier à la main avant de livrer ?

<intent.md>

<diff>
```

### Mettre en production (démo)

```text
/sdlc-ship 0001
```

Claude vérifie les préalables (PR prête, CI verte, `VERIFY OK`, rapport du `verifier`, evals si la
configuration de Claude change, aucun constat Important ouvert), vous demande les vérifications
manuelles et votre approbation, les consigne dans la PR et passe l'intention à *livrée*. Deux actions
humaines, toujours :

1. **la fusion, c'est vous qui la lancez.** Claude ne lance jamais une commande de livraison
   (fusion, poussée sur `master`, approbation de PR, lancement d'un workflow) : un hook la refuse
   dans tous les modes (`.claude/release-commands`) et Claude vous donne la commande exacte. Lancez-la
   dans le prompt de Claude Code en la préfixant de `!`, par exemple :

   ```text
   ! gh pr merge 18 --merge --delete-branch
   ```

   puis dites à Claude de continuer (suivi du déploiement, test de fumée) ;
2. **le déploiement**, dans GitHub : le workflow *Deploy demo* attend votre approbation de
   l'environnement `demo` (bouton *Review deployments* du run).

Après le déploiement, Claude lance le test de fumée et les vérifications « après déploiement » du
plan :

```bash
deploy/smoke.sh                                   # la démo
deploy/smoke.sh https://host.docker.internal -k   # l'environnement de dev
```

Dernière ligne : `SMOKE OK` ou `SMOKE KO: <n> vérification(s)`.

### Revenir en arrière

Une livraison qui casse quelque chose se défait par une **PR de revert** du commit de fusion
(`git revert -m 1 <commit de fusion>`), revue et fusionnée comme les autres : la démo est redéployée
dans sa version précédente. Puis une intention `bug` (source : la PR fautive) reprend le cycle.

**Attention aux changesets Liquibase** : un revert retire le fichier du changeset, pas ce qu'il a
déjà fait à la base de la démo. Si la livraison fautive a modifié le schéma, le revert doit être
accompagné d'un **nouveau** changeset qui défait ce changement (jamais la modification de l'ancien),
sinon la version précédente démarre sur un schéma qu'elle ne connaît pas.

`master` est protégée : PR obligatoire, CI verte exigée, pas de poussée forcée. Approbation exigée
dès qu'il y aura un second contributeur ([CODEOWNERS](.github/CODEOWNERS)).

## Aide-mémoire

| Commande                                 | Étape     | Qui                     |
| ---------------------------------------- | --------- | ----------------------- |
| `/sdlc-intent draft <idée>`              | Planifier | tout contributeur       |
| `/sdlc-intent draft #<issue>`            | Planifier | développeur ou PO       |
| `/sdlc-intent accept <NNNN>`             | Planifier | PO                      |
| `/sdlc-intent reject <NNNN> <motif>`     | Planifier | PO                      |
| `/sdlc-intent stats`                     | Planifier | PO                      |
| `/sdlc-spec draft <NNNN>`                | Concevoir | développeur ou PO       |
| `/sdlc-spec approve <NNNN>`              | Concevoir | PO (tech lead si risque élevé) |
| `/sdlc-spec stats`                       | Concevoir | PO                      |
| `/sdlc-plan draft <NNNN>` (plan mode)    | Construire | développeur            |
| `/sdlc-plan implement <NNNN>` (mode auto) | Construire | développeur           |
| `/sdlc-plan stats`                       | Construire | développeur ou tech lead |
| `./verify.sh` (`--quick`)                | Tester     | tout contributeur, et Claude |
| `/sdlc-evals run`                        | Tester     | qui modifie la configuration de Claude |
| `/sdlc-evals add <comportement>`         | Tester     | développeur                |
| `/sdlc-review review <PR>`               | Livrer     | tout relecteur, et Claude  |
| `/sdlc-review address <PR>`              | Livrer     | auteur de la PR            |
| `/sdlc-ship <NNNN>`                      | Livrer     | relecteur (humain) qui approuve |
| `deploy/smoke.sh`                        | Livrer     | après chaque déploiement   |
