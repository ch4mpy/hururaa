# Intention : toast d'erreur à l'ouverture de l'application

- Auteur : ch4mpy (PO et développeur)
- Nature : bug
- Source : conversation
- Spec nécessaire : oui (décision du PO du 2026-10-12, à la place du « non » de l'acceptation)
- Statut : livrée (2026-10-12, PR #20, démo déployée : `SMOKE OK`, CA-6 vérifié)
- Ouverte le : 2026-10-11
- Décidée le : 2026-10-11
- Remplace : —

## Problème

À l'ouverture de l'application, un toast d'erreur « Requête refusée par le serveur » s'affiche
alors que l'application fonctionne normalement. Tout visiteur le voit, connecté ou non, à la
première ouverture après le démarrage du navigateur. Le message n'empêche rien mais inquiète
l'utilisateur, l'habitue à ignorer les erreurs et peut masquer une vraie erreur.

Pour reproduire :

1. Démarrer l'environnement de dev complet (infrastructure, gateway, hururaa-api, front).
2. Fermer le navigateur, le rouvrir, puis ouvrir https://host.docker.internal/fr/.

- Attendu : la page s'affiche sans message d'erreur.
- Observé : le toast « Requête refusée par le serveur ». Ce libellé correspond à une réponse en
  erreur client (4xx autre que 401, 403 et 404).
- Environnement : dev local et démo (https://hururaa.c4-soft.com), Chrome, 2026-10-11.

Sur la démo, `GET /gateway/me` répond par une redirection 302 vers `/me`, qui redirige à son tour
(302) vers `/fr/`.

En dev, non reproduit dans un navigateur vierge (contexte isolé), ni anonyme, ni connecté
(`dpam.manager`), ni après redémarrage de ce navigateur : toutes les requêtes y répondent 200.
Avec la gateway arrêtée, `GET /gateway/me` reçoit un 502 de nginx et le toast affiché est un
autre message, « Erreur du serveur, veuillez réessayer plus tard ».

## Résultat attendu

À l'ouverture de l'application, connecté ou non, aucun toast d'erreur n'est affiché quand rien
n'est réellement en panne. Un message clair reste affiché quand la gateway ou l'API est
réellement injoignable.

Critères de réussite :

- le scénario ci-dessus n'affiche plus de toast ;
- gateway arrêtée, un message d'erreur explicite reste affiché.

## Utilisateurs et systèmes concernés

- Tous les personas : administrateurs Hurura'a, administrateurs de direction, gestionnaires
  d'application, membres d'une direction.
- Visiteur non connecté (cas d'usage, pas un persona du projet : décision du PO).
- Frontend Angular (affichage des erreurs). La requête en cause, non identifiée, vise la gateway
  ou hururaa-api.

## Contraintes

- Les vraies erreurs doivent rester signalées à l'utilisateur.

## Questions ouvertes

- Les redirections de `/gateway/me` observées sur la démo sont-elles la cause du toast, et quelle
  requête reçoit finalement une réponse en erreur ? — développeur, à l'étape suivante
- Quelles sont les conditions exactes : session ouverte avant la fermeture du navigateur ?
  onglets restaurés ? première ouverture du jour seulement ? — ch4mpy
- Solution évoquée : identifier la requête en erreur, puis soit l'empêcher de partir, soit faire
  en sorte qu'elle aboutisse. — développeur, au plan (pas de spec pour ce bug)
