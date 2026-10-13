# Runbook : revenir à une version précédente de la démo

Le chemin de retour arrière est le plus répété du cycle : il sert en cas d'incident, et il est
répété chaque mois (issue `rollback-drill`) pour rester sûr et rapide.

## Quand

- Une livraison casse la démo (test de fumée KO, issue `band-breach` en palier *propose*, erreurs
  constatées) et le correctif n'est pas immédiat.
- La répétition mensuelle.

## Avant : le schéma de la base

Le rollback redéploie les **images** d'une version précédente ; il ne défait **pas** les changesets
Liquibase déjà appliqués à la base de la démo. Si la livraison fautive a modifié le schéma
(`backend/hururaa-api/src/main/resources/db/changelog/changes/` dans son diff), l'ancienne version
peut ne pas démarrer : il faut alors un **nouveau** changeset qui défait la modification (livré
normalement), pas un rollback seul. Sans changeset dans le diff : le rollback suffit.

## Comment

1. Trouver la version à redéployer : le commit du dernier *Deploy demo* réussi **avant** la
   livraison fautive.

   ```bash
   gh run list --workflow deploy-demo.yml --status success --limit 5 --json headSha,displayTitle,updatedAt
   ```

2. Lancer le rollback (commande humaine : Claude ne lance jamais un workflow de déploiement ;
   dans Claude Code, la préfixer de `!`) :

   ```text
   ! gh workflow run rollback-demo.yml -f image_tag=<SHA complet>
   ```

3. Approuver le déploiement dans GitHub : run *Rollback demo to <SHA>*, bouton *Review
   deployments*, environnement `demo`.
4. Vérifier :

   ```bash
   deploy/smoke.sh
   ```

   Dernière ligne attendue : `SMOKE OK`, puis un contrôle à la main de ce qui était cassé.
5. Remettre `master` d'accord avec la démo : une PR de revert du commit de fusion fautif
   (`git revert -m 1 <commit de fusion>`), puis une intention `bug` (source : la PR fautive).

## Répétition mensuelle

Rollback vers la version précédente, `deploy/smoke.sh`, puis retour à la version courante (même
commande avec le SHA courant), `deploy/smoke.sh`. Noter dans l'issue `rollback-drill` la durée
totale (de la commande au second `SMOKE OK`) et tout accroc, puis la fermer.
