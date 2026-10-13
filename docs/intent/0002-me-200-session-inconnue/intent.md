# Intention : /me répond 200 avec un cookie de session inconnu

- Auteur : ch4mpy (PO et développeur)
- Nature : évolution
- Source : conversation ; fait suite à l'intention [0001](../0001-toast-erreur-ouverture-application/intent.md) (spec v2, EX-1) et à [spring-addons#312](https://github.com/ch4mpy/spring-addons/issues/312)
- Spec nécessaire : oui
- Statut : proposée
- Ouverte le : 2026-10-13
- Décidée le : —
- Remplace : —

## Problème

Depuis l'intention 0001, quand le navigateur présente un cookie de session que la gateway ne
connaît plus (navigateur rouvert, session expirée, gateway redémarrée), `/gateway/me` répond `401`
au lieu de `200` anonyme. Aucun utilisateur ne le voit (le front traite cette `401` comme
l'anonyme, sans message), mais :

- **le contrat de `/me` n'est plus tenu** : « répond toujours `200`, connecté ou non » (CLAUDE.md) ;
  la `401` est une exception que tout client de la gateway doit connaître ;
- **bruit** : une `401` par navigateur rouvert dans les journaux, le panneau réseau et les métriques,
  qui masque les vrais échecs d'authentification ;
- l'en-tête `Location` de cette `401` pointe hors de la gateway (préfixe `/gateway` perdu) ; les
  autres BFF bâtis sur spring-addons (applications des directions) ont le même défaut.

Coût faible (dette technique), à traiter maintenant que spring-addons-starter-oidc **9.5.1**
apporte l'évolution demandée dans spring-addons#312.

## Résultat attendu

- Avec un cookie de session inconnu, `/gateway/me` répond `200` avec l'utilisateur anonyme, sans
  redirection, comme sans cookie ; aucun message, accueil en anonyme.
- Les autres routes de la gateway gardent le comportement livré par 0001 : `401` sans redirection
  pour une session inconnue, et passage à l'état déconnecté dans le front.
- Réussite : sur la démo, le test de fumée attend `200` pour `/me` avec une session inconnue et passe.

## Utilisateurs et systèmes concernés

- Tous les visiteurs, connectés ou non (aucun changement visible attendu).
- gateway (BFF OAuth2), frontend Angular, démo (`deploy/`).

## Contraintes

- Ne rien perdre de 0001 : aucun toast à l'ouverture, message explicite gateway arrêtée, état
  déconnecté sur une `401` des autres routes.
- Version de spring-addons vérifiée sur Maven Central (règle de CLAUDE.md).
- Risque élevé (authentification) : revue du tech lead.

## Questions ouvertes

- Le comportement de 9.5.1 (option pour les routes en accès libre, `Location` qui garde le context
  path) couvre-t-il tout le besoin, sans code propre à Hurura'a ? — spec (lecture de la version 9.5.1)
- Le traitement « sonde de session » du front (la `401` de `/me` vaut l'anonyme) reste-t-il utile,
  par prudence, ou doit-il disparaître ? — PO / tech lead
- Solution évoquée : monter spring-addons-starter-oidc en 9.5.1 et activer son option pour les
  routes en accès libre. — spec
