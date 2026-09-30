#!/usr/bin/env bash
set -euo pipefail

source ./build-0-env.sh

# reverse-proxy
if [ "$CN" = "host.docker.internal" ] || [ "$CN" = "localhost" ] || [ "$CN" = `hostname` ] || [ "$CN" = "$HOSTNAME" ]; then
  echo "building reverse proxy image"
  docker build -t ${REPO_GROUP_ID}.${REPO_NAME}/reverse-proxy ./nginx-reverse-proxy
fi

# Keycloak
#
export KC_BOOTSTRAP_ADMIN_USERNAME=$(cat ./secrets/keycloak/admin_user.txt)
export KC_BOOTSTRAP_ADMIN_PASSWORD=$(cat ./secrets/keycloak/admin_password.txt)

docker build \
  --build-arg KC_VERSION=$KC_VERSION \
  --build-arg CN=$CN \
  --build-arg KC_HEALTH_ENABLED=true \
  --build-arg KC_METRICS_ENABLED=true \
  --build-arg KC_HTTP_ENABLED=$KC_HTTP_ENABLED \
  --build-arg KC_HTTPS_PORT=$KC_HTTPS_PORT \
  --build-arg KC_LOG_LEVEL=$KC_LOG_LEVEL \
  -t ${REPO_GROUP_ID}.${REPO_NAME}/keycloak:$KC_VERSION \
  -f ./keycloak/Dockerfile \
  .

