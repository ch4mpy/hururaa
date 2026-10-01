#!/usr/bin/env bash
# Renders the dev realm export (keycloak/import/public-facing-realm.json, written for
# host.docker.internal with "secret" everywhere) into deploy/keycloak/import/ for the demo
# deployment: public host name, Mailpit as SMTP server, and every default secret replaced.
# Run from the repository root by .github/workflows/deploy-demo.yml. Needs jq and openssl.
set -euo pipefail

cd "$(dirname "$0")/../.."

required=(DEMO_HOST HURURAA_BFF_CLIENT_SECRET HURURAA_API_CLIENT_SECRET MAILPIT_SMTP_PASSWORD)
for name in "${required[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "$name is not set" >&2
    exit 1
  fi
done

realm=keycloak/import/public-facing-realm.json
out=deploy/keycloak/import
mkdir -p "$out"

# hururaa-bff and hururaa-api get the secrets the gateway and the API are configured with. Every
# other confidential client (the sample applications' <prefix>-bff / <prefix>-api, broker,
# realm-management) gets a fresh random one: nothing in the demo authenticates as them, and their
# exported values are public in git. Realms are only imported when absent (see README.md), so
# regenerating these on each deployment changes nothing on a running Keycloak.
others='{}'
while read -r client_id; do
  others=$(jq --arg id "$client_id" --arg secret "$(openssl rand -hex 32)" '. + {($id): $secret}' <<< "$others")
done < <(jq -r '.clients[] | select(.secret and .clientId != "hururaa-bff" and .clientId != "hururaa-api") | .clientId' "$realm")

jq --arg host "$DEMO_HOST" --arg smtp "$MAILPIT_SMTP_PASSWORD" \
  --arg bff "$HURURAA_BFF_CLIENT_SECRET" --arg api "$HURURAA_API_CLIENT_SECRET" --argjson others "$others" '
  walk(if type == "string" then gsub("https://host[.]docker[.]internal"; "https://" + $host) else . end)
  | .smtpServer.host = "mailpit"
  | .smtpServer.starttls = "false"
  | .smtpServer.password = $smtp
  | .clients |= map(
      if .clientId == "hururaa-bff" then .secret = $bff
      elif .clientId == "hururaa-api" then .secret = $api
      elif .secret then .secret = ($others[.clientId] // error("no secret generated for " + .clientId))
      else . end)
' "$realm" > "$out/public-facing-realm.json"

# Every "secret" must have been replaced, except the demo users' password credentials ("value":
# "secret", see the notice below): those are public by design. No `grep -l` / `grep -q` in a
# pipeline here: they stop at the first match, the upstream grep dies of SIGPIPE and pipefail
# would turn a detected leak into a silently passing check.
leaks="$(grep -v '"value" *: *"secret"' "$out"/*.json | grep 'host\.docker\.internal\|" *: *"secret"' || true)"
if [ -n "$leaks" ]; then
  echo "some dev values survived the rendering:" >&2
  echo "$leaks" >&2
  exit 1
fi

# Not rendered, on purpose: the demo users' passwords. They are "secret" for everyone (in clear in
# the realm export). That is what makes the demo usable by anyone; a deployment beyond
# the demo must replace them (and this notice is here so that it does not forget).
echo "notice: demo users keep their public password (public-facing realm): intended for the demo only" >&2
echo "realm rendered into $out for $DEMO_HOST"
