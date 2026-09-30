# 0004. Actuator sur un port de management dédié

Statut : acceptée.

## Contexte

L'audit a montré que la route BFF `/bff/api/v1/**` expose tout endpoint de l'API, actuator
compris. La correction appliquée restreint `management.endpoints.web.exposure.include` à
`health,info` dans les deux applications : les endpoints dangereux n'existent plus, quel que soit
le chemin. Reste que `health` et `info` passent encore par la route BFF, et qu'une future
extension de l'exposition (par exemple `prometheus` pour un scraping) rouvrirait le chemin.

La défense en profondeur consiste à servir l'actuator sur un port de management distinct
(`management.server.port`), jamais routé par Caddy ni par nginx, et à y adresser les healthchecks
Docker.

## Options

1. En rester à la restriction d'exposition.
2. Port de management dédié dans les deux applications, healthchecks de `deploy/compose.yml`
   réadressés, exposition élargie possible (`prometheus`) sans risque. À valider en démarrant la
   pile : le port de management a sa propre chaîne de sécurité, à vérifier avec spring-addons.

## Décision

Option 2 : l'actuator des deux applications est servi sur un port de management dédié, que
ni Caddy ni nginx ne routent. Les healthchecks Docker s'y adressent.

## Conséquences

Option 2 : `management.server.port` dans `application.yml` des deux applications, healthchecks
et éventuels scrapes sur ce port, `nginx-reverse-proxy` et `Caddyfile` inchangés puisqu'ils ne le
routent pas.
