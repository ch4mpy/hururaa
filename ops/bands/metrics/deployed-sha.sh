#!/usr/bin/env bash
# The commit whose images run on the demo: the most recent deployment or rollback whose step
# "Pull and restart the stack" ran, whether it succeeded or not (a deployment failing after
# `docker compose up` has still replaced the containers). Prints nothing if none is found.
# Limit: it trusts the run history; a manual change on the VPS goes unseen.
set -u
step="Pull and restart the stack"
for workflow in deploy-demo.yml rollback-demo.yml; do
  gh run list --workflow "$workflow" --limit 10 --json databaseId,updatedAt,headSha,displayTitle \
    --jq ".[] | [.updatedAt, (.databaseId | tostring), .headSha, .displayTitle, \"$workflow\"] | @tsv" 2>/dev/null
done | sort -r | while IFS=$'\t' read -r _ id head title workflow; do
  ran=$(gh run view "$id" --json jobs --jq "[.jobs[].steps[]? | select(.name == \"$step\" and .status == \"completed\"
    and (.conclusion == \"success\" or .conclusion == \"failure\"))] | length" 2>/dev/null)
  [ "${ran:-0}" -gt 0 ] || continue
  if [ "$workflow" = rollback-demo.yml ]; then echo "${title##* }"; else echo "$head"; fi
  break
done
