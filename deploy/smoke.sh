#!/usr/bin/env bash
# Smoke test of a deployed Hurura'a (CONTRIBUTING.md, étape 5): the few checks that tell whether a
# deployment works, run right after it. Read-only.
#
#   deploy/smoke.sh                      the demo, https://hururaa.c4-soft.com
#   deploy/smoke.sh https://host.docker.internal -k   the dev stack (self-signed certificate)
#
# The last line is "SMOKE OK" or "SMOKE KO: <n> check(s)" (exit code 0 / 1).
set -u
base=${1:-https://hururaa.c4-soft.com}
shift || true
curl_opts=("$@")
failures=0

expect() { # label expected-status url [curl options...]
  local label=$1 want=$2 url=$3
  shift 3
  local got
  # ${a[@]+...}: an empty array under set -u breaks bash < 4.4 (macOS)
  got=$(curl -s -o /dev/null -m 20 -w '%{http_code}' ${curl_opts[@]+"${curl_opts[@]}"} "$@" "$url")
  if [ "$got" = "$want" ]; then
    printf 'OK  %-55s %s\n' "$label" "$got"
  else
    printf 'KO  %-55s %s (expected %s)\n' "$label" "$got" "$want"
    failures=$((failures + 1))
  fi
}

expect "front (fr)" 200 "$base/fr/"
expect "front (en)" 200 "$base/en/"
expect "gateway /me, no session: anonymous" 200 "$base/gateway/me" -H 'Accept: application/json'
# docs/intent/0002: a session cookie the gateway no longer knows is treated as no cookie on /me
# (anonymous, a new session), never a redirection
expect "gateway /me, unknown session: anonymous" 200 "$base/gateway/me" \
  -H 'Accept: application/json' -H 'Cookie: JSESSIONID=0123456789ABCDEF0123456789ABCDEF'
expect "API through the BFF, no session: 401" 401 "$base/gateway/bff/api/v1/me/delegations" \
  -H 'Accept: application/json'
expect "Keycloak realm (OIDC discovery)" 200 \
  "$base/auth/realms/public-facing/.well-known/openid-configuration"

echo
if [ "$failures" -eq 0 ]; then
  echo "SMOKE OK"
else
  echo "SMOKE KO: $failures check(s)"
  exit 1
fi
