#!/usr/bin/env bash
# Failed demo deployments over the last 7 days: Deploy demo runs that failed (build, tests, scan or
# deployment), plus rollbacks whose redeployment failed (a rollback refused by its own check, e.g.
# a mistyped SHA, deployed nothing and doesn't count). NA if the GitHub API can't be read.
set -u
since=$(date -u -d '-7 days' +%Y-%m-%dT%H:%M:%SZ)
deploys=$(gh run list --workflow deploy-demo.yml --created ">=$since" --limit 100 --json conclusion \
  --jq '[.[] | select(.conclusion == "failure")] | length') || { echo NA; exit 0; }
if ! ids=$(gh run list --workflow rollback-demo.yml --created ">=$since" --limit 100 --json databaseId,conclusion \
  --jq '.[] | select(.conclusion == "failure") | .databaseId' 2>&1); then
  # a rollback workflow not yet on the default branch has simply never run
  grep -q 'not found on the default branch' <<<"$ids" || { echo NA; exit 0; }
  ids=""
fi
rollbacks=0
for id in $ids; do
  failed=$(gh run view "$id" --json jobs \
    --jq '[.jobs[] | select((.name | test("Redeploy")) and .conclusion == "failure")] | length') || { echo NA; exit 0; }
  [ "$failed" -eq 0 ] || rollbacks=$((rollbacks + 1))
done
echo $((deploys + rollbacks))
