#!/usr/bin/env bash
set -euo pipefail

source ./build-1-images.sh

export KC_BOOTSTRAP_ADMIN_USERNAME=$(cat ./secrets/keycloak/admin_user.txt)
export KC_BOOTSTRAP_ADMIN_PASSWORD=$(cat ./secrets/keycloak/admin_password.txt)

docker compose \
  -f compose-rest-api-db.yml \
  -f compose-mailpit.yml \
  -f compose-keycloak.yml \
  -f compose-rabbitmq.yml \
  -f compose-observability.yml \
  -f compose-reverse-proxy.yml \
  up -d
