# Bandes de contrôle (étape 6)

Détection **déterministe**, sans modèle : le workflow [Bands](../../.github/workflows/bands.yml)
mesure toutes les 6 heures les métriques de [bands.json](bands.json), compare chacune à sa bande de
contrôle ([detect.sh](detect.sh)) et ouvre une issue `band-breach` par métrique en dépassement
([report.sh](report.sh)), que l'astreinte trie avec `/sdlc-triage`. Voir
[CONTRIBUTING.md](../../CONTRIBUTING.md), étape 6.

## Règles

Moyenne et écart type des mesures des `windowDays` derniers jours (la mesure courante exclue),
règles de Western Electric unilatérales (plus haut = pire) :

| Règle | Condition | Palier |
| --- | --- | --- |
| 1 | la mesure dépasse moyenne + 3σ | `propose` |
| 2 | 2 des 3 dernières mesures dépassent moyenne + 2σ | `diagnose` |
| 4 | les 8 dernières mesures sont au-dessus de la moyenne (dérive) | `diagnose` |

Tant que l'historique compte moins de `minPoints` mesures (ou si σ est nul), les seuils fixes
`fixed.diagnose` / `fixed.propose` de la métrique s'appliquent.

## Métriques

| Métrique | Mesure |
| --- | --- |
| `ci_failure_rate` | part (%) des runs CI de PR en échec sur 24 h |
| `deploy_failures_7d` | déploiements et rollbacks de la démo en échec sur 7 jours |
| `demo_smoke_ko` | vérifications KO de `deploy/smoke.sh` sur la démo |
| `demo_image_cves` | CVE HIGH/CRITICAL corrigées en amont dans les images déployées (Trivy, en CI seulement) |

Ajouter une métrique : un script dans [metrics/](metrics/) qui imprime un nombre (ou `NA` quand rien
n'est mesurable), une entrée dans `bands.json`, puis une PR.

## Historique et essais

L'historique (`<métrique>.csv`) vit sur la branche `bands-data`, poussée par le workflow. En local :

```bash
ops/bands/detect.sh --data /tmp/bands                 # mesure, sans toucher à l'historique partagé
ops/bands/detect.sh --data /tmp/bands | ops/bands/report.sh --dry-run
```

Pour tester la chaîne issue → triage sans rien casser : lancer le workflow *Bands* à la main avec
`simulate=<métrique>` (dépassement simulé, palier `propose`).
