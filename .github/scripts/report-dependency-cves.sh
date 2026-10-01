#!/usr/bin/env bash
#
# Publishes the findings of ci.yml's Trivy dependency scan.
#
# The scan itself does not fail the job. A dependency CVE is published upstream on its own
# schedule, with no relation to the change being validated, so failing on one blocks every open
# pull request (Dependabot's included) on work that has nothing to do with it. Instead the findings
# go to the run summary, and to a single GitHub issue kept up to date: opened on the first run that
# finds something, edited by later runs, closed again once the dependencies are clean.
#
# Vulnerabilities with no fix available are excluded by the scan itself, so everything reported
# here is actionable: bump the dependency, or override the version the Spring Boot BOM manages
# (see backend/check-version-overrides.sh, which keeps such overrides from outliving their reason).
set -euo pipefail

REPORT=${REPORT:-trivy-deps.txt}
TITLE=${TITLE:-'Vulnerable dependencies (Trivy)'}
SUMMARY=${GITHUB_STEP_SUMMARY:-/dev/stdout}
MARKER=${MARKER:-'<!-- hururaa-trivy-dependency-report -->'}

if [ "${SCAN_OUTCOME:-success}" = "failure" ] && [ -s "$REPORT" ]; then
  has_findings=true
else
  has_findings=false
fi

if $has_findings; then
  cat "$REPORT"
  {
    echo "### Vulnerable dependencies"
    echo
    echo "Fixable CRITICAL/HIGH vulnerabilities, which do not fail this job:"
    echo '```'
    head -c 50000 "$REPORT"
    echo '```'
  } >> "$SUMMARY"
else
  echo "No fixable CRITICAL/HIGH dependency vulnerability." | tee -a "$SUMMARY"
fi

# Writing an issue needs a writable token, which runs triggered by Dependabot or by a fork do not
# get. Their findings still show up in the run summary above, and the next ordinary run reconciles
# the issue.
if [ "${CAN_WRITE:-false}" != "true" ]; then
  echo "Read-only token (Dependabot or fork run): leaving the issue untouched."
  exit 0
fi
if ! command -v gh >/dev/null; then
  echo "gh is not available: leaving the issue untouched."
  exit 0
fi
if ! command -v jq >/dev/null; then
  echo "jq is not available: leaving the issue untouched."
  exit 0
fi

issue=$(
  gh issue list --state all --json number,state,title,body,createdAt |
    jq -c --arg marker "$MARKER" --arg title "$TITLE" \
      'map(select((.body // "") | contains($marker)) | select(.title == $title)) | sort_by(.createdAt) | .[0] // empty'
)
number=$(jq -r '.number // empty' <<< "$issue")
state=$(jq -r '.state // empty' <<< "$issue")

if $has_findings; then
  body=$(
    echo "$MARKER"
    echo
    echo "Trivy found fixable CRITICAL/HIGH vulnerabilities in the project dependencies."
    echo
    echo '```'
    head -c 50000 "$REPORT"
    echo '```'
    echo
    echo "_Last updated from ${GITHUB_SHA:-unknown commit} on branch ${GITHUB_REF_NAME:-unknown}._"
  )
  if [ -n "$number" ]; then
    if [ "$state" = "closed" ]; then
      gh issue reopen "$number"
    fi
    gh issue edit "$number" --body "$body"
    echo "Updated issue #$number."
  else
    gh issue create --title "$TITLE" --body "$body"
  fi
elif [ -n "$number" ]; then
  gh issue close "$number" \
    --comment "No fixable CRITICAL/HIGH dependency vulnerability left as of ${GITHUB_SHA:-unknown commit}."
  echo "Closed issue #$number."
fi
