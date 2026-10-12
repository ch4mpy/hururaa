#!/usr/bin/env bash
# The commit whose images run on the demo: the most recent successful deployment or rollback.
set -u
{
  gh run list --workflow deploy-demo.yml --status success --limit 1 \
    --json headSha,updatedAt --jq '.[] | .updatedAt + " " + .headSha'
  gh run list --workflow rollback-demo.yml --status success --limit 1 \
    --json displayTitle,updatedAt --jq '.[] | .updatedAt + " " + (.displayTitle | split(" ") | last)'
} 2>/dev/null | sort | tail -1 | cut -d' ' -f2
