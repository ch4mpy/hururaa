# Spec : toast d'erreur à l'ouverture de l'application

- Intention : [0001](intent.md)
- Statut : approuvée
- Version : 1
- Risque : élevé — le changement touche l'authentification (gestion des sessions de la gateway)
- Politiques appliquées : policy-securite-api @229653c, policy-ux-i18n @229653c,
  policy-delegation @229653c, policy-tracabilite @229653c (non concernée)
- Approuvée : 2026-10-12, ch4mpy (PO) ; tech lead : ch4mpy, 2026-10-12 (PO et tech lead sont la
  même personne : pas de revue GitHub possible sur sa propre PR)

## Résumé

Quand le navigateur présente à la gateway un cookie de session qu'elle ne connaît plus (navigateur
rouvert, session expirée, gateway redémarrée), la gateway redirige au lieu de répondre : la SPA
reçoit une page HTML là où elle attend du JSON et affiche « Requête refusée par le serveur ». La
gateway doit traiter une session inconnue comme une absence de session, et le front doit nommer
justement une réponse inattendue.

## Exigences

- EX-1 Avec un cookie de session inconnu de la gateway, `GET /gateway/me` répond `200` avec
  l'utilisateur anonyme, sans redirection, comme sans cookie. (intention : critère « le scénario
  n'affiche plus de toast »)
- EX-2 Avec un cookie de session inconnu, les routes `/gateway/bff/**` répondent `401` sans
  redirection, comme sans cookie (portée : PA-1). (intention : résultat attendu, « aucun toast
  quand rien n'est en panne »)
- EX-3 Gateway arrêtée, l'ouverture de l'application affiche toujours un message d'erreur explicite.
  (intention : critère « gateway arrêtée »)
- EX-4 Une réponse de la gateway qui n'est pas le contenu attendu (page HTML au lieu de JSON) est
  signalée par « Réponse inattendue du serveur, veuillez recharger la page » (fr et en), pas par
  « Requête refusée par le serveur » (PA-2). (intention : contrainte « les vraies erreurs restent
  signalées »)
- EX-5 Connexion, déconnexion (y compris back-channel) et flux d'événements fonctionnent comme
  avant. (non-régression, PA-3)
- EX-6 Sur une `401` de la gateway, la SPA passe à l'état déconnecté (utilisateur anonyme,
  bouton *Connexion*, menus d'administration masqués) en plus du message « Session expirée,
  veuillez vous reconnecter ». (PA-3, condition du tech lead)
- EX-7 La `401` d'une session inconnue est obtenue par une option de spring-addons-starter-oidc,
  pas par du code propre à la gateway (PA-3, condition du tech lead).

## Hors périmètre

- Reconnecter sans action un utilisateur dont la session de la gateway a disparu alors que sa
  session Keycloak vit encore : il retrouve l'accueil anonyme et clique sur *Connexion* (sans
  ressaisir son mot de passe tant que sa session Keycloak est active).
- Les messages des erreurs de l'API autres que celles décrites ici.

## Conception

### Comportement actuel

Observé sur la démo le 2026-10-11 (`curl`, en-tête `Accept: application/json`) :

| Requête | Sans cookie | Cookie `JSESSIONID` inconnu |
| --- | --- | --- |
| `GET /gateway/me` | `200`, utilisateur anonyme | `302` → `https://hururaa.c4-soft.com/me` |
| `GET /gateway/bff/api/v1/me/delegations` | `401` | `302` → `…/bff/api/v1/me/delegations` |
| `GET /gateway/bff/events/dsi` | `401` | `302` → `…/bff/events/dsi` |

La redirection perd le préfixe `/gateway` ; `/me` redirige ensuite (`302`) vers `/fr/`, qui répond
`200 text/html`. Le navigateur suit les redirections, la SPA ne peut lire du HTML comme du JSON, et
l'erreur produite n'a aucun des statuts que distingue `httpErrorMessage` (0, 401, 403, 404, ≥ 500) :
c'est le message par défaut, « Requête refusée par le serveur »
(`frontend/angular/projects/hururaa/src/app/core/http-error.interceptor.ts:37`). `/me` est pourtant
ouvert à tous et répond l'anonyme plutôt qu'une `401` (`MeController.getMe`).

En dev, un navigateur neuf n'a pas de cookie périmé : le bug n'apparaît qu'avec une session déjà
ouverte que la gateway a perdue (par exemple, se connecter, redémarrer la gateway, recharger).

### Comportement par persona

Identique pour tous les personas et pour le visiteur non connecté : à l'ouverture, une session
inconnue de la gateway vaut absence de session ; l'accueil s'affiche sans message, en anonyme.
Un utilisateur qui était connecté voit le bouton *Connexion* (DEL-3 : rien d'autre ne change de ses
droits).

### API et contrats

- `GET /gateway/me` : contrat inchangé (`200`, utilisateur courant ou anonyme), désormais aussi
  avec un cookie de session inconnu.
- `/gateway/bff/**` : `401` pour toute requête sans session valide, cookie inconnu compris.
- Aucune route de la gateway appelée par la SPA ne répond par une redirection vers une page de
  l'application.

### Interface

- Aucun toast à l'ouverture dans les cas ci-dessus.
- Gateway injoignable : message actuel inchangé, « Erreur du serveur, veuillez réessayer plus
  tard » (`502` de nginx).
- Réponse inattendue : selon PA-2.

### Données

Aucune.

## Politiques appliquées

| Règle | Comment elle est respectée |
| --- | --- |
| SEC-1 | Aucun endpoint ajouté ; `/me` reste ouvert à tous par conception (anonyme plutôt que `401`). |
| SEC-6 | Pas de nouvelle erreur côté API ; la `401` de la gateway n'est pas un problème typé → PA-4 |
| SEC-7 | Le comportement « cookie de session inconnu » est couvert par des tests de la gateway (CA-1, CA-2). |
| UX-1 | Tout nouveau message passe par `@angular/localize`, en français et en anglais (PA-2). |
| UX-2 | Une réponse non typée garde un message générique, mais exact (PA-2). |
| UX-3 | Les échecs réels restent affichés dans la bannière d'erreur. |
| UX-6 | Sur une `401`, l'état déconnecté masque les actions d'administration (EX-6). |
| DEL-3 | Une session perdue ne retire ni ne donne aucun droit ; l'utilisateur se reconnecte. |

## Points d'attention

### PA-1 Portée : `/me` seulement, ou toutes les routes de la gateway

- Enjeu : la même cause redirige aussi `/gateway/bff/**`. Pendant l'utilisation (gateway
  redémarrée, session expirée), le même faux message apparaîtrait au premier appel à l'API.
- Options : A, toutes les routes (`/me` anonyme, `/bff/**` en `401` → « Session expirée, veuillez
  vous reconnecter ») / B, `/me` seulement, comme l'intention le décrit.
- Recommandation : A. Même cause, même correction, et le message qui en résulte est juste.
- Décide : PO
- Décision : A, toutes les routes (PO, 2026-10-12).

### PA-2 Message pour une réponse inattendue

- Enjeu : une réponse illisible (HTML au lieu de JSON) n'est ni un refus ni une panne identifiée.
- Options : A, garder « Requête refusée par le serveur » / B, un message dédié, par exemple
  « Réponse inattendue du serveur, veuillez recharger la page » (fr et en).
- Recommandation : B. Le message décrit la situation, et la contrainte « les vraies erreurs restent
  signalées » est tenue.
- Décide : PO
- Décision : B, message dédié (PO, 2026-10-12).

### PA-3 Risque sur la chaîne d'authentification

- Enjeu : changer le traitement des sessions inconnues touche la chaîne de sécurité de la gateway
  (connexion OAuth2, CSRF, déconnexion back-channel).
- Options : A, accepter, avec la non-régression couverte par CA-5 et une vérification sur la démo /
  B, différer.
- Recommandation : A.
- Décide : tech lead
- Décision : acceptée sous conditions (tech lead, 2026-10-12) :
  - la `401` pour une session inconnue vient d'une option de spring-addons-starter-oidc
    (vraisemblablement déjà offerte) ; à défaut, ouvrir un ticket chez spring-addons et y ajouter
    l'option (le tech lead la maintient), puis monter de version dans Hurura'a — pas de
    contournement local (EX-7) ;
  - le front traite correctement une `401` : il passe à l'état déconnecté s'il n'y est pas déjà
    (EX-6).

### PA-4 SEC-6 et les erreurs de la gateway

- Enjeu : SEC-6 couvre « les endpoints de `hururaa-api` et de la gateway », mais la `401` de la
  gateway vient du point d'entrée de spring-addons, pas d'un `ProblemType`. Cet écart existe déjà ;
  ce changement ne l'aggrave pas.
- Options : A, restreindre le périmètre de SEC-6 à `hururaa-api` / B, une intention pour typer les
  erreurs de la gateway / C, accepter l'écart en l'état.
- Recommandation : A ou B, hors de cette spec.
- Décide : responsable de la politique
- Décision : B, une nouvelle intention pour typer les erreurs de la gateway (responsable de la
  politique, 2026-10-12) ; l'écart reste en l'état dans cette spec.

## Critères d'acceptation

- CA-1 Étant donné un navigateur dont le cookie `JSESSIONID` est inconnu de la gateway, quand on
  ouvre `/fr/`, alors `GET /gateway/me` répond `200` anonyme sans redirection, et aucun toast
  n'apparaît. (EX-1)
- CA-2 Étant donné le même cookie, quand la SPA appelle `/gateway/bff/api/v1/me/delegations`,
  alors la réponse est `401` sans redirection. (EX-2, si PA-1 = A)
- CA-3 Étant donné la gateway arrêtée, quand on ouvre `/fr/`, alors « Erreur du serveur, veuillez
  réessayer plus tard » s'affiche. (EX-3)
- CA-4 Étant donné une réponse `200` en HTML à un appel qui attend du JSON, alors le message
  retenu en PA-2 s'affiche. (EX-4)
- CA-5 Étant donné un utilisateur de dev (`dpam.manager`), connexion, affichage de ses directions,
  réception d'un événement et déconnexion fonctionnent ; une déconnexion faite dans Keycloak ferme
  la session de la gateway (back-channel). (EX-5)
- CA-7 Étant donné un utilisateur connecté dont la session de la gateway disparaît (gateway
  redémarrée), quand la SPA reçoit une `401` de `/gateway/bff/**`, alors « Session expirée,
  veuillez vous reconnecter » s'affiche et l'en-tête montre *Connexion*, sans menus
  d'administration. (EX-6)
- CA-8 La configuration de la gateway n'active la `401` des sessions inconnues que par une
  propriété de spring-addons-starter-oidc. (EX-7)
- CA-6 Sur la démo, après redéploiement : se connecter, fermer et rouvrir le navigateur, ouvrir
  l'application → aucun toast. (EX-1)

## Questions de l'intention

- Les redirections de `/gateway/me` sont-elles la cause du toast, et quelle requête reçoit
  finalement une réponse en erreur ? → Oui : `/gateway/me` avec un cookie de session inconnu,
  redirigé jusqu'à `/fr/` (HTML), illisible comme JSON. Ce n'est pas une réponse 4xx : c'est le
  message par défaut de l'intercepteur. Source : `curl` sur la démo, tableau ci-dessus ;
  `http-error.interceptor.ts:37`.
- Conditions exactes ? → Un cookie `JSESSIONID` que la gateway ne connaît plus : navigateur rouvert
  avec ses cookies, session expirée ou gateway redémarrée. Un navigateur neuf ne déclenche rien.
- Solution évoquée (identifier la requête, l'empêcher de partir ou la faire aboutir) → la requête
  est identifiée ; elle doit partir (elle dit si l'utilisateur est connecté) et aboutir : EX-1.

## Historique

- v1 — 2026-10-12 — première version ; à l'approbation : décisions PA-1 à PA-4, ajout de EX-6,
  EX-7, CA-7 et CA-8 (conditions du tech lead).
