# Static frontend for the demo: one Angular build localized under fr/ and en/ (Angular's --localize
# output), served by nginx behind Caddy. Build from the repository root after
# `ng build hururaa --configuration production --base-href /`:
#   docker build -f deploy/frontend/Dockerfile .
FROM nginx:alpine
ENV TZ="Pacific/Tahiti"
# Alpine publishes package fixes before nginx rebuilds its image: take them without waiting.
RUN apk upgrade --no-cache
COPY deploy/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/angular/dist/hururaa/browser/ /usr/share/nginx/html/
