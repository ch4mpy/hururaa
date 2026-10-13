#!/usr/bin/env bash
# Turns the breaches printed by detect.sh (JSON lines on stdin) into GitHub issues, for the human
# and for /sdlc-triage: one open issue per metric, labelled band-breach and tier:<diagnose|propose>.
#   - an open issue for the metric: a comment only when the value or the tier changed since the
#     last report (a hidden marker in the issue keeps it), plus tier:propose if it escalates;
#   - a metric whose issue a human dismissed (triage:dismissed) within the last 7 days: nothing;
#   - otherwise: a new issue.
# Any GitHub API failure fails the script (a silent failure would create duplicates).
#
#   ops/bands/detect.sh | ops/bands/report.sh [--dry-run]
set -u
dry=false
[ "${1:-}" != "--dry-run" ] || dry=true
run() { if $dry; then printf 'DRY-RUN:'; printf ' %q' "$@"; echo; else "$@"; fi; }
run_url=${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-ch4mpy/hururaa}/actions/runs/${GITHUB_RUN_ID:-local}
quiet_days=7

open_issues=$(gh issue list --state open --label band-breach --limit 200 \
  --json number,title,labels,body,comments) || { echo "cannot list the open band-breach issues" >&2; exit 1; }
since=$(date -u -d "-$quiet_days days" +%Y-%m-%dT%H:%M:%SZ)
dismissed=$(gh issue list --state closed --label band-breach --label triage:dismissed --limit 200 \
  --json title,closedAt --jq "[.[] | select(.closedAt >= \"$since\") | .title]") ||
  { echo "cannot list the dismissed band-breach issues" >&2; exit 1; }

while IFS= read -r breach; do
  [ -n "$breach" ] || continue
  metric=$(jq -r .metric <<<"$breach")
  tier=$(jq -r .tier <<<"$breach")
  value=$(jq -r .value <<<"$breach")
  marker="<!-- bands: value=$value tier=$tier -->"
  body=$(jq -r --arg run "$run_url" --arg marker "$marker" '
    "**Métrique :** `" + .metric + "` — " + .description + "\n\n" +
    "| Valeur | Moyenne | Écart type | Points de référence | Règle | Palier |\n" +
    "| --- | --- | --- | --- | --- | --- |\n" +
    "| " + .value + " | " + .mean + " | " + .sd + " | " + (.n | tostring) + " | " + .rule + " | " + .tier + " |\n\n" +
    "Détectée par [ops/bands](" + $run + ") (règles : `ops/bands/detect.sh`). " +
    "Palier **diagnose** : diagnostic en lecture seule ; **propose** : diagnostic et proposition " +
    "(intention, PR ou rollback). Triage : `/sdlc-triage` dans Claude Code, puis un libellé " +
    "`triage:fix-now`, `triage:later` ou `triage:dismissed` posé par un humain.\n\n" + $marker' <<<"$breach")
  title="[bande] $metric"
  existing=$(jq -c --arg t "$title" '[.[] | select(.title == $t)] | first // empty' <<<"$open_issues")

  if [ -n "$existing" ]; then
    number=$(jq -r .number <<<"$existing")
    last=$(jq -r '[.body, (.comments[].body)] | map(select(test("<!-- bands: "))) | last // ""
      | capture("<!-- bands: value=(?<v>[^ ]*) tier=(?<t>[^ ]*) -->") | .v + " " + .t' <<<"$existing" 2>/dev/null)
    if [ "$last" = "$value $tier" ]; then
      echo "issue #$number ($metric): unchanged ($value, $tier)" >&2
      continue
    fi
    echo "issue #$number ($metric): changed (${last:-?} → $value $tier)" >&2
    run gh issue comment "$number" --body "Le dépassement a évolué.

$body"
    if [ "$tier" = propose ] && jq -e '[.labels[].name] | index("tier:diagnose")' <<<"$existing" >/dev/null; then
      run gh issue edit "$number" --add-label tier:propose --remove-label tier:diagnose
    fi
  elif jq -e --arg t "$title" 'index($t)' <<<"$dismissed" >/dev/null; then
    echo "$metric: dismissed by a human less than $quiet_days days ago, not reopened" >&2
  else
    echo "new issue ($metric, $tier)" >&2
    run gh issue create --title "$title" --label band-breach --label "tier:$tier" --body "$body"
  fi
done
