#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

echo "Installing (if required) and activating the Java version from .sdkmanrc..."
export SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
if [ -s "${SDKMAN_DIR}/bin/sdkman-init.sh" ]; then
  source "${SDKMAN_DIR}/bin/sdkman-init.sh"

  JAVA_CANDIDATE_VERSION=$(grep '^java=' .sdkmanrc | cut -d'=' -f2)
  JAVA_CANDIDATE_ALREADY_INSTALLED=false
  if [ -n "$JAVA_CANDIDATE_VERSION" ] && [ -d "${SDKMAN_DIR}/candidates/java/${JAVA_CANDIDATE_VERSION}" ]; then
    JAVA_CANDIDATE_ALREADY_INSTALLED=true
  fi

  sdk env install

  # A freshly installed JDK ships its own default cacerts, without our self-signed
  # certificate. A mere switch to an already-installed version keeps its existing cacerts.
  if [ "$JAVA_CANDIDATE_ALREADY_INSTALLED" = false ] && [ -f ./.env ]; then
    source ./.env
    if [ -n "$CN" ] && [ -f ~/.ssh/${CN}.jks ] && [ -f ./secrets/ssl/password.txt ]; then
      echo "A new JDK was installed, adding the self-signed SSL certificate for ${CN} to its cacerts..."
      bash ./update-cacerts.sh
    fi
  fi
else
  echo "SDKMAN not found at ${SDKMAN_DIR}, skipping Java version setup (see https://sdkman.io/)."
fi

echo "Installing (if required) and activating the Node version from .nvmrc..."
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ -s "${NVM_DIR}/nvm.sh" ]; then
  source "${NVM_DIR}/nvm.sh"
  nvm install
  nvm use
else
  echo "nvm not found at ${NVM_DIR}, skipping Node version setup (see https://github.com/nvm-sh/nvm)."
fi

COMPOSE_FILES=(
  -f compose-rest-api-db.yml
  -f compose-mailpit.yml
  -f compose-keycloak.yml
  -f compose-rabbitmq.yml
  -f compose-observability.yml
  -f compose-reverse-proxy.yml
)

if [ -f ./.env ]; then
  source ./.env

  echo "Destroying Hururaa Docker containers and volumes..."
  docker compose "${COMPOSE_FILES[@]}" down -v --remove-orphans

  if [ -n "$REPO_GROUP_ID" ] && [ -n "$REPO_NAME" ]; then
    echo "Destroying Hururaa Docker images..."
    docker rmi -f "${REPO_GROUP_ID}.${REPO_NAME}/keycloak:${KC_VERSION}" 2>/dev/null || true
    docker rmi -f "${REPO_GROUP_ID}.${REPO_NAME}/reverse-proxy" 2>/dev/null || true
  fi
else
  echo "No .env file found, skipping teardown of an existing dev environment."
fi

echo "Recreating Hururaa dev infra..."
bash ./deploy-dev.sh

echo "Regenerating OpenAPI specs..."
bash ./build-openapi.sh

echo "Regenerating Angular API client libs..."
(cd frontend/angular && npm i && npm run api)
