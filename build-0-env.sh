#!/usr/bin/env bash
set -euo pipefail

HOST_NAME=host.docker.internal
# Declared before ENV_DEFAULTS, which expands it: as an array entry only, it was still unset when
# the array was built and RABBITMQ_USER ended up as "-api".
REPO_NAME=hururaa

# Default values for .env variables. Comments removed on purpose: entries are
# appended one by one below, existing lines are left untouched.
ENV_DEFAULTS=(
  "REPO_NAME=${REPO_NAME}"
  "REPO_GROUP_ID=pf.hururaa"
  "CN=${HOST_NAME}"
  "HOST_NAME=${HOST_NAME}"
  "MAIL_HOST=${HOST_NAME}"
  "MAIL_PORT=1025"
  "KC_VERSION=26.7"
  "KC_HTTP_ENABLED=false"
  "KC_HTTPS_PORT=3643"
  "KC_LOG_LEVEL=INFO"
  "REST_API_DB_PORT=2633"
  "RABBITMQ_PORT=5672"
  "RABBITMQ_MANAGEMENT_PORT=15672"
  "RABBITMQ_USER=${REPO_NAME}-api"
)

# Appends "KEY=value" to .env only if KEY is not already defined there.
add_env_var_if_missing() {
  local key="${1%%=*}"
  if ! grep -q "^${key}=" ./.env; then
    echo "$1" >> ./.env
  fi
}

if [ ! -f ./.env ]; then
  echo ".env file not found, creating one."
  touch ./.env
fi

for entry in "${ENV_DEFAULTS[@]}"; do
  add_env_var_if_missing "$entry"
done

source ./.env

if [ ! -d ./secrets ]; then
  mkdir ./secrets
fi

if [ ! -d ./secrets/ssl ]; then
  mkdir ./secrets/ssl
fi
if [ ! -f ./secrets/ssl/password.txt ]; then
  if [ -f ~/.ssh/${CN}.password.txt ]; then
    cp ~/.ssh/${CN}.password.txt ./secrets/ssl/password.txt
  else
    echo $(openssl rand -hex 16) > ./secrets/ssl/password.txt
    chmod 600 ./secrets/ssl/password.txt
    cp ./secrets/ssl/password.txt ~/.ssh/${CN}.password.txt
  fi
fi

if [ ! -d ./secrets/keycloak ]; then
  mkdir ./secrets/keycloak
fi
if [ ! -f ./secrets/keycloak/admin_user.txt ]; then
  echo admin > ./secrets/keycloak/admin_user.txt
  chmod 600 ./secrets/keycloak/admin_user.txt
fi
if [ ! -f ./secrets/keycloak/admin_password.txt ]; then
  echo secret > ./secrets/keycloak/admin_password.txt
  chmod 600 ./secrets/keycloak/admin_password.txt
fi

if [ ! -d ./secrets/mail ]; then
  mkdir ./secrets/mail

fi
if [ ! -f ./secrets/mail/username.txt ]; then
  echo "mailpit" > ./secrets/mail/username.txt
  chmod 600 ./secrets/mail/username.txt
fi
if [ ! -f ./secrets/mail/password.txt ]; then
  echo "secret" > ./secrets/mail/password.txt
  chmod 600 ./secrets/mail/password.txt
fi
if [ -f ./secrets/mail/auth.txt ]; then
  rm -f ./secrets/mail/auth.txt
fi
echo "$(cat ./secrets/mail/username.txt):$(cat ./secrets/mail/password.txt)" > ./secrets/mail/auth.txt
chmod 600 ./secrets/mail/auth.txt

if [ ! -d ./secrets/rest-api ]; then
  mkdir ./secrets/rest-api
fi
if [ ! -f ./secrets/rest-api/postgres_password.txt ]; then
  echo $(openssl rand -hex 16) > ./secrets/rest-api/postgres_password.txt
  chmod 600 ./secrets/rest-api/postgres_password.txt
fi
add_env_var_if_missing "SPRING_DATASOURCE_PASSWORD=$(cat ./secrets/rest-api/postgres_password.txt)"

if [ ! -d ./secrets/rabbitmq ]; then
  mkdir ./secrets/rabbitmq
fi
if [ ! -f ./secrets/rabbitmq/password.txt ]; then
  echo $(openssl rand -hex 16) > ./secrets/rabbitmq/password.txt
  chmod 600 ./secrets/rabbitmq/password.txt
fi
add_env_var_if_missing "RABBITMQ_PASSWORD=$(cat ./secrets/rabbitmq/password.txt)"



if [ ! -f ./certs/$CN.crt ]; then
  if [ ! -f ~/.ssh/${CN}.crt ]; then
    export SSL_PASSWORD=$(cat ./secrets/ssl/password.txt)
    echo "Generating self-signed SSL certificates for ${CN} in ~/.ssh"
    bash ./certs/self-signed.sh $CN $SSL_PASSWORD `echo ~/.ssh`
  fi

  echo "Copying self-signed SSL certificates for ${CN} from ~/.ssh to ./certs"
  cp ~/.ssh/${CN}.* ./certs/

  echo "Adding self-signed SSL certificates for ${CN} to JRE cacerts"
  bash ./update-cacerts.sh
fi
