#!/usr/bin/env bash
# Share (%) of the CI runs of pull requests that failed over the last 24 hours; NA without any.
set -u
since=$(date -u -d '-1 day' +%Y-%m-%dT%H:%M:%SZ)
gh run list --workflow ci.yml --event pull_request --created ">=$since" --limit 200 \
  --json conclusion --jq '[.[] | select(.conclusion == "success" or .conclusion == "failure")]
    | if length == 0 then "NA" else (map(select(.conclusion == "failure")) | length) * 100 / length | floor end'
