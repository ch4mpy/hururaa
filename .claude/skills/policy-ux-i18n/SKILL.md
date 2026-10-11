---
name: policy-ux-i18n
description: Hurura'a user interface and i18n policy (rules UX-n). Use whenever creating or modifying the Angular frontend — pages, components, user-facing texts, error or success messages, navigation, styles — and when writing a spec, plan or review that changes what users see.
---

# Politique : interface et internationalisation

- Responsable : ch4mpy
- Périmètre : le frontend Angular (`frontend/angular/projects/hururaa`), tout ce que voit l'utilisateur.
- Sources : `CLAUDE.md` (*Frontend*), décision 0006.

Citer les règles par leur identifiant (`UX-n`) dans les specs, les plans et les revues. Une
règle ne change que par une PR sur ce fichier, revue par son responsable ; une exception exige une
décision (`docs/decisions/`).

## Règles

1. **UX-1 Tout texte est traduit.** Chaque texte visible passe par `@angular/localize` (`i18n`, `$localize`, avec un identifiant explicite), en français (`fr-PF`) et en anglais (`en-US`) ; la règle ESLint `@angular-eslint/template/i18n` est respectée. Après tout ajout, lancer le skill `angular-fix-i18n`. *Pourquoi :* l'application est livrée en deux langues.
2. **UX-2 Erreurs traduites depuis leur type.** Une erreur de l'API s'affiche selon son type de problème (`core/problem-messages.ts`, registre exhaustif), jamais telle qu'envoyée par le serveur ; un nouveau type de problème y reçoit son message. *Pourquoi :* le serveur ne parle pas la langue de l'utilisateur.
3. **UX-3 Succès en toast, échecs en bannière.** Un succès se confirme par un toast (`injectNotifier`) ; un appel en échec s'affiche par `httpErrorInterceptor` et `ErrorBanner` ; une action destructive se confirme par `confirm()`. *Pourquoi :* l'utilisateur reconnaît toujours le même signal.
4. **UX-4 Composants de la charte.** L'interface utilise pf-ui (`pf-app`, `pf-page`), PrimeNG 21, les utilitaires PrimeFlex et les icônes Remix Icon ; pas de Tailwind ni d'autre bibliothèque de composants. *Pourquoi :* charte du Gouvernement de la Polynésie française (décision 0006).
5. **UX-5 Une fonction, un seul endroit.** Une fonctionnalité vit à un seul endroit de la navigation (applications par direction, utilisateurs par direction) ; ailleurs, on y renvoie par un lien. *Pourquoi :* pas de double maintenance ni d'écarts de comportement.
6. **UX-6 Actions selon les droits.** Menus et actions n'apparaissent qu'à qui peut les faire, d'après `DelegationsService` (voir `DEL-8`). *Pourquoi :* pas de bouton qui mène à un refus.
7. **UX-7 Pages à jour.** Une page charge ses données avec `rxResource` et se recharge sur les `ResourceTypes` qui la concernent (événements de la direction). *Pourquoi :* plusieurs délégués travaillent en même temps.

## Vérifier

- UX-1 : `npm run lint` et `npm run extract-i18n` sans erreur ; aucune unité de traduction sans cible dans `messages.en-US.xlf`.
- UX-2 : `npm run api` puis `npm run build` : `problem-messages.ts` ne compile que s'il couvre tous les types.
- UX-4 : `grep -rn tailwind frontend/angular/projects/hururaa/src` ne renvoie rien.

## Exceptions

Aucune.
