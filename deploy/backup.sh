#!/usr/bin/env bash
# Backup of what the demo accumulates: the REST API database (hot, pg_dump through the running
# container) and Keycloak's embedded H2 files (cold: H2 cannot be copied consistently while
# Keycloak runs, so the container is stopped for the few seconds the copy takes; the Spring apps
# fail their Keycloak calls meanwhile and recover on their own).
#
# Runs on the host as the hururaa user, from ~/hururaa/deploy (the deploy workflow installs it in
# that user's crontab). Restore procedures are in README.md.
set -euo pipefail
cd "$(dirname "$0")"

BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
stamp="$(date +%Y%m%d-%H%M%S)"
install -d -m 0700 "$BACKUP_DIR"

# the compose project name is fixed in compose.yml; the volume is resolved through its labels
# rather than by guessing "<project>_<volume>"
project="$(docker compose config --format json | python3 -c 'import json, sys; print(json.load(sys.stdin)["name"])')"
kc_volume="$(docker volume ls -q -f "label=com.docker.compose.project=$project" -f label=com.docker.compose.volume=keycloak_data)"
if [ -z "$kc_volume" ]; then
  echo "keycloak_data volume of project $project not found" >&2
  exit 1
fi

# REST API database: credentials come from the container's own environment
docker compose exec -T rest-api-db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "$BACKUP_DIR/rest-api-db-$stamp.dump"

# Keycloak H2: stop, tar the volume through a throw-away container, start again whatever happens
docker compose stop keycloak
trap 'docker compose start keycloak' EXIT
docker run --rm -v "$kc_volume:/data:ro" -v "$BACKUP_DIR:/backup" alpine:3 \
  tar czf "/backup/keycloak-h2-$stamp.tgz" -C /data .
docker compose start keycloak
trap - EXIT

find "$BACKUP_DIR" -name 'rest-api-db-*.dump' -mtime +"$KEEP_DAYS" -delete
find "$BACKUP_DIR" -name 'keycloak-h2-*.tgz' -mtime +"$KEEP_DAYS" -delete
echo "backup $stamp written to $BACKUP_DIR (keeping $KEEP_DAYS days)"
