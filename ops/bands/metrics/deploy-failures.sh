#!/usr/bin/env bash
# Failed demo deployments and rollbacks over the last 7 days.
set -u
since=$(date -u -d '-7 days' +%Y-%m-%dT%H:%M:%SZ)
failures=0
for workflow in deploy-demo.yml rollback-demo.yml; do
  n=$(gh run list --workflow "$workflow" --created ">=$since" --limit 100 --json conclusion \
    --jq '[.[] | select(.conclusion == "failure")] | length' 2>/dev/null || echo 0)
  failures=$((failures + ${n:-0}))
done
echo "$failures"
