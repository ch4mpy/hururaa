#!/usr/bin/env bash
# Turns the breaches printed by detect.sh (JSON lines on stdin) into GitHub issues, for the human
# and for /sdlc-triage: one open issue per metric, labelled band-breach and tier:<diagnose|propose>.
# A metric that already has an open issue gets a comment (and tier:propose if it escalates),
# never a second issue.
#
#   ops/bands/detect.sh | ops/bands/report.sh [--dry-run]
set -u
dry=false
[ "${1:-}" != "--dry-run" ] || dry=true
run() { if $dry; then printf 'DRY-RUN:'; printf ' %q' "$@"; echo; else "$@"; fi; }
run_url=${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-ch4mpy/hururaa}/actions/runs/${GITHUB_RUN_ID:-local}

while IFS= read -r breach; do
  [ -n "$breach" ] || continue
  metric=$(jq -r .metric <<<"$breach")
  tier=$(jq -r .tier <<<"$breach")
  body=$(jq -r --arg run "$run_url" '
    "**Métrique :** `" + .metric + "` — " + .description + "\n\n" +
    "| Valeur | Moyenne | Écart type | Points de référence | Règle | Palier |\n" +
    "| --- | --- | --- | --- | --- | --- |\n" +
    "| " + .value + " | " + .mean + " | " + .sd + " | " + (.n | tostring) + " | " + .rule + " | " + .tier + " |\n\n" +
    "Détectée par [ops/bands](" + $run + ") (règles : `ops/bands/detect.sh`). " +
    "Palier **diagnose** : diagnostic en lecture seule ; **propose** : diagnostic et proposition " +
    "(intention, PR ou rollback). Triage : `/sdlc-triage` dans Claude Code, puis un libellé " +
    "`triage:fix-now`, `triage:later` ou `triage:dismissed` posé par un humain."' <<<"$breach")
  title="[bande] $metric"
  existing=$(gh issue list --state open --label band-breach --search "\"$title\" in:title" \
    --json number,title,labels --jq ".[] | select(.title == \"$title\")" 2>/dev/null | head -1)
  if [ -n "$existing" ]; then
    number=$(jq -r .number <<<"$existing")
    echo "issue #$number ($metric): new breach" >&2
    run gh issue comment "$number" --body "Nouveau dépassement.

$body"
    if [ "$tier" = propose ] && jq -e '[.labels[].name] | index("tier:diagnose")' <<<"$existing" >/dev/null; then
      run gh issue edit "$number" --add-label tier:propose --remove-label tier:diagnose
    fi
  else
    echo "new issue ($metric, $tier)" >&2
    run gh issue create --title "$title" --label band-breach --label "tier:$tier" --body "$body"
  fi
done
