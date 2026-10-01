# 0006. Angular 21 pour s'aligner sur pf-ui 21 / PrimeNG 21

Statut : proposée.

## Contexte

Le frontend doit utiliser les composants du design system de la Polynésie française, `pf-ui`
(`21.0.14`, publié sur `bin.gov.pf`), basés sur PrimeNG 21.

PrimeNG 21 déclare `@angular/core ^21` en dépendance pair, PrimeNG 22 `^22`. `pf-ui` est publié
comme un module unique (`fesm2022/pf-ui.mjs`) qui importe toutes ses dépendances, y compris celles
de composants non utilisés (`@ngrx/signals`, `@ngx-translate/core`, `p-intl-input-tel`,
`google-libphonenumber`, `ngx-image-cropper`...) : elles doivent toutes être installées. Le
Storybook de développement du design system est, lui, déjà construit en Angular 22.

## Options

1. Angular 22 avec PrimeNG 22 et pf-ui 21, en forçant les dépendances pairs
   (`legacy-peer-deps`). Rien ne garantit que pf-ui 21 fonctionne avec PrimeNG 22.
2. Angular 21 (dernière 21.2), PrimeNG 21 et les versions 21 des dépendances de pf-ui.
3. Attendre une version 22 publiée de pf-ui.

## Décision

Option 2 pour le PoC : aucune dépendance forcée, `npm install` résout sans conflit. Le passage en
Angular 22 suivra la publication d'un pf-ui 22.

## Conséquences

- Pas de Tailwind : les utilitaires CSS sont ceux de PrimeFlex, embarqué par les styles de pf-ui.
- Les icônes fonctionnelles sont celles de Remix Icon (`<i class="ri-...">`, y compris dans
  l'attribut `icon` des composants PrimeNG) ; PrimeIcons reste installé pour les composants de
  pf-ui.
- Trois mécanismes de traduction coexistent : `@angular/localize` (une build par langue, `/fr/` et
  `/en/`) pour les textes de l'application, et ngx-translate pour les libellés internes de pf-ui
  (langue déduite de `LOCALE_ID`). Les libellés internes de PrimeNG (dont ses `aria-label`) viennent de
  `primelocale`, les traductions officielles de PrimeFaces, dans la même langue.
- pf-ui charge ses images par un chemin absolu (`/assets/img/...`) : le reverse proxy route
  `/assets/`.
- Le bundle initial dépasse 1,5 Mo (avertissement de budget), essentiellement PrimeNG et les
  dépendances de pf-ui.
