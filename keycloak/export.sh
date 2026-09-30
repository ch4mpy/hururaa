#!/usr/bin/env bash

cd "$(dirname "$0")/.."

source ./.env

export MSYS_NO_PATHCONV=1

echo "Stopping ${REPO_NAME}.keycloak-server to release the H2 database lock for export..."
docker stop ${REPO_NAME}.keycloak-server > /dev/null

trap 'docker start ${REPO_NAME}.keycloak-server > /dev/null' EXIT

docker create \
  --name ${REPO_NAME}.keycloak-export \
  --network ${REPO_NAME}_default \
  --volumes-from ${REPO_NAME}.keycloak-server \
  --entrypoint /bin/bash \
  ${REPO_GROUP_ID}.${REPO_NAME}/keycloak:${KC_VERSION} \
  -c "mkdir -p /tmp/keycloak/ && /opt/keycloak/bin/kc.sh export --dir /tmp/keycloak/ --users realm_file" > /dev/null

docker start -a ${REPO_NAME}.keycloak-export

docker cp ${REPO_NAME}.keycloak-export:/tmp/keycloak/. ./keycloak/import/

# master only holds the admin user, which KC_BOOTSTRAP_ADMIN_* recreates
rm -f ./keycloak/import/master-realm.json

docker rm ${REPO_NAME}.keycloak-export > /dev/null
