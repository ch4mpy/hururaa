# Evals de la configuration de Claude

De vraies tâches, chacune avec un résultat attendu vérifié par une commande, pour vérifier que la
configuration qui guide Claude (`CLAUDE.md`, `.claude/**`, politiques, modèles, skills `sdlc-*`)
produit toujours le bon comportement. Étape 4 du cycle AI-native : voir
[CONTRIBUTING.md](../CONTRIBUTING.md#étape-4--tester).

## Quand les lancer

- **Obligatoire** pour une PR qui modifie `CLAUDE.md`, `.claude/**`, `docs/intent/_modele-*` ou un
  skill `sdlc-*` : le rapport va dans la PR.
- Après une erreur de Claude constatée en session ou en revue : ajouter une eval qui la reproduit
  (`/sdlc-evals add`).
- Jamais en tâche planifiée : chaque lancement consomme l'abonnement claude.ai (pas de clé d'API).

## Comment

Dans Claude Code : `/sdlc-evals run` (ou `run E3-test-gele`). Dans un terminal, depuis le dépôt :

```bash
bash ~/.claude/scripts/run-evals.sh            # toutes, sur HEAD
bash ~/.claude/scripts/run-evals.sh E1-politiques-api --base master
```

Chaque eval tourne dans un worktree jetable (les hooks et permissions du dépôt s'appliquent), puis
sa commande `check` décide (code 0 = réussie). Le rapport est écrit dans `evals/reports/` (ignoré par
git) ; la dernière ligne est `EVALS n/m OK` ou `EVALS n/m KO`. Le format d'une eval est décrit en
tête de `run-evals.sh`.

## Les evals

| Eval | Ce qu'elle garde |
| --- | --- |
| [E1-politiques-api](E1-politiques-api.json) | une demande d'endpoint fait appliquer les politiques `SEC` et `TRA`, avant tout code |
| [E2-changeset-applique](E2-changeset-applique.json) | un changeset Liquibase appliqué n'est pas modifié : un nouveau est ajouté |
| [E3-test-gele](E3-test-gele.json) | pendant un correctif, le test gelé n'est pas touché : le code est corrigé |
| [E4-i18n](E4-i18n.json) | un texte ajouté à l'interface est traduit avec un identifiant `@@` (`UX-1`) |
