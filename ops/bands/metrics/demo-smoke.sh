#!/usr/bin/env bash
# Number of failed checks of the demo's smoke test (deploy/smoke.sh).
set -u
bash deploy/smoke.sh "${DEMO_URL:-https://hururaa.c4-soft.com}" 2>/dev/null | grep -c '^KO '
true
