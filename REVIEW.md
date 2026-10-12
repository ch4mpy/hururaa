# Consignes de revue

Lues par Claude (`/sdlc-review`) et par tout relecteur humain. Une revue juge le **comportement et
le risque** d'une PR, pas sa mise en forme : la CI s'en charge.

## Passes

Faire trois passes et étiqueter chaque constat avec la sienne :

- **Bugs** : erreurs de logique, cas limites cassés, régressions, concurrence, ressources non
  libérées, erreurs avalées.
- **Sécurité** : règles des politiques [`SEC`](.claude/skills/policy-securite-api/SKILL.md),
  [`TRA`](.claude/skills/policy-tracabilite/SKILL.md) et [`DEL`](.claude/skills/policy-delegation/SKILL.md),
  injection, secret ou jeton exposé, contrôle d'accès contourné côté front ou API.
- **Conformité** : la PR fait ce que disent sa spec (`EX-n`, chaque `CA-n` a sa preuve) et son plan
  (fichiers prévus, écarts consignés), respecte [`UX`](.claude/skills/policy-ux-i18n/SKILL.md) et les
  conventions de `CLAUDE.md`.

Citer la règle en cause (`SEC-4`, `EX-2`, `UX-1`…) dans chaque constat.

## Ce que « Important » veut dire ici

Réserver **Important** à ce qui casserait un comportement, exposerait des données, enfreindrait une
politique, ou laisserait un critère d'acceptation sans preuve. Le reste est un détail (nommage,
style, lisibilité).

## Limiter les détails

Au plus cinq détails par revue ; résumer les autres par leur nombre.

## Ne pas signaler

- Le code et les specs générés : `frontend/angular/projects/api/**`, `backend/openapi/out/**`.
- Les numéros de ligne et l'ordre des entrées dans `src/locale/*.xlf`.
- Ce que la CI vérifie déjà : formatage et lint, versions de dépendances
  (`check-version-overrides.sh`, Dependabot), secrets (gitleaks), vulnérabilités (Trivy).
- Le contenu de `docs/intent/**` autre que la cohérence avec le code (il a eu sa propre revue).

## Ensuite

Un constat Important qui révèle une erreur récurrente de Claude devient une eval
(`/sdlc-evals add`) ou une ligne de `CLAUDE.md`, dans une PR à part. Claude n'approuve jamais une
PR : l'approbation est humaine.
