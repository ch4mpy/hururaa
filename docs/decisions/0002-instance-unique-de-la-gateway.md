# 0002. Instance unique de la gateway et cache local

Statut : acceptée.

## Contexte

Trois états vivent en mémoire de la gateway : les sessions HTTP (aucune dépendance Spring
Session), le registre des flux SSE (`SseEmitterRegistry`, une liste parcourue à chaque événement)
et l'`InMemoryOidcSessionRegistry` dont dépend le back-channel logout. `HururaaSecurityJacksonModule`
existe pour sérialiser la session en JSON mais n'est branché sur aucun `ObjectMapper`.

Côté API, le cache Keycloak est un Caffeine local (`maximumSize=1000,expireAfterWrite=300s`) :
une éviction sur une instance n'atteint pas les autres, qui servent des rôles obsolètes pendant
au plus cinq minutes.

Tant que rien ne change, tout projet dérivé est contraint à une instance de gateway et une
instance d'API, ou doit accepter l'affinité de session (et perdre le back-channel logout sur les
autres instances).

## Options

1. Assumer l'instance unique. Documenter que la haute disponibilité passe par un redémarrage
   rapide (`restart: unless-stopped`, healthchecks) et non par la redondance. Zéro coût.
2. Externaliser les sessions avec Spring Session (Redis ou JDBC), brancher
   `HururaaSecurityJacksonModule`, remplacer l'`InMemoryOidcSessionRegistry` par une
   implémentation partagée, et sortir le registre SSE du processus (pub/sub Redis, ou une file
   RabbitMQ par instance abonnée à l'exchange, chaque instance ne servant que ses propres
   navigateurs). Pour l'API, un cache Redis ou une éviction diffusée. Deux à quatre jours.
3. Chemin intermédiaire : Spring Session JDBC sur la base PostgreSQL déjà présente (pas de
   nouveau composant), registre SSE inchangé mais indexé par tenant, et une file RabbitMQ par
   instance de gateway pour que chaque instance reçoive tous les événements et n'en relaie qu'à
   ses abonnés. Le cache API reste local avec un TTL raccourci. Une à deux journées.

## Décision

Option 1 : l'instance unique est assumée pour la gateway comme pour l'API. La disponibilité
repose sur le redémarrage rapide (`restart: unless-stopped`, healthchecks, `--wait` au
déploiement), pas sur la redondance. Le registre SSE est indexé par tenant, ce qui ne préjuge de
rien mais retire le parcours linéaire. Un projet dérivé qui exige plusieurs instances part de
l'option 3.

## Conséquences

Quelle que soit l'option, le `SseEmitterRegistry` gagnerait à être indexé par tenant plutôt que
parcouru linéairement, indépendamment de la réplication.
