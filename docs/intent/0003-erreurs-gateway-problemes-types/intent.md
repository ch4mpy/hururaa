# Intention : erreurs de la gateway en problèmes RFC 9457 typés

- Auteur : ch4mpy (PO et développeur)
- Nature : évolution
- Source : conversation ; décision PA-4 de la spec de [0001](../0001-toast-erreur-ouverture-application/spec.md) (« une nouvelle intention »)
- Spec nécessaire : oui
- Statut : spécifiée
- Ouverte le : 2026-10-13
- Décidée le : 2026-10-13
- Remplace : —

## Problème

La règle SEC-6 (politique de sécurité de l'API) veut que toute erreur renvoyée par hururaa-api
**et par la gateway** soit un problème RFC 9457 d'un type de `ProblemType`, que le front traduit
par type. Ce n'est vrai que des erreurs métier de hururaa-api. Constaté en dev le 2026-10-13 :

| Erreur | Réponse aujourd'hui |
| --- | --- |
| `/gateway/bff/events/{direction}` sans session (flux d'événements) | `401`, corps en texte brut « Unauthorized. Please authenticate at https://…/login » |
| `/gateway/bff/api/v1/…` sans session (relayée sans jeton à hururaa-api) | `401`, corps vide |
| route inconnue de la gateway | `401` vide, au lieu d'une `404` |
| accès refusé, erreur 5xx de la gateway, hururaa-api injoignable | non observé (non provoqué) ; aucun type prévu |

Ce que cela coûte :

- **messages vagues** : faute de type, le front n'affiche que le message générique du code HTTP ;
- **écart avec la politique** : SEC-6 énonce une règle que la gateway ne suit pas, relevée à chaque
  revue (PA-4 de 0001) ;
- **fuite de détails internes** : des corps en texte brut (URL de connexion, libellés de
  spring-addons) atteignent le client.

## Résultat attendu

- Toute erreur que la SPA peut recevoir, de la gateway comme de hururaa-api (y compris la `401`
  sans jeton, l'accès refusé, une erreur 5xx, l'API injoignable, une route inconnue), est un
  **problème RFC 9457** d'un type de `ProblemType` ; plus aucun corps en texte brut ni vide.
- Une route inconnue de la gateway répond `404` (problème typé), avec ou sans session.
- Quand hururaa-api est injoignable, l'utilisateur voit un **message dédié** (service
  momentanément indisponible), pas le message générique du code HTTP.
- Ailleurs, le front peut garder le message générique du code HTTP là où un message propre
  n'apporte rien.
- Réussite : un contrôle (test, test de fumée) prouve, pour chacun de ces cas, une réponse
  `application/problem+json` d'un type connu.

## Utilisateurs et systèmes concernés

- Tous les personas déclarés (administrateurs Hurura'a, administrateurs de direction,
  gestionnaires d'application, membres d'une direction) : quiconque rencontre une erreur.
- Les applications des directions qui appellent hururaa-api ou reposent sur les mêmes starters
  (*non déclarées dans le contexte du projet*) : un corps typé à la place d'un corps vide ou en
  texte est accepté, elles s'en tiennent au code HTTP.
- gateway (BFF OAuth2), hururaa-api, frontend Angular, starters communs.

## Contraintes

- SEC-6 : le détail d'une erreur 5xx est journalisé, jamais envoyé ; rien d'interne ne fuit.
- UX-2 : une erreur s'affiche selon son type, jamais telle qu'envoyée par le serveur.
- Ne rien perdre de 0001 et 0002 (pas de redirection, `401` → état déconnecté, `/me` toujours `200`).
- Risque élevé (authentification, règles d'accès) : revue du tech lead.
- Passer à spring-addons 9.5.2 (publiée, avec un contrôle sur
  `invalid-session.pass-through-patterns`).

## Questions ouvertes

- Où vit la correction : la gateway, spring-addons (point d'entrée configurable, voir
  [spring-addons#314](https://github.com/ch4mpy/spring-addons/issues/314)), ou les deux, compte
  tenu de ce qu'apporte la 9.5.2 ? — spec
