#!/usr/bin/env bash
# HIGH/CRITICAL vulnerabilities with an upstream fix in the images running on the demo, with the
# exemptions of that version's .trivyignore.yaml (the same scan as the deployment, run again: new
# CVEs are published after a deployment). NA when Trivy isn't installed.
set -u
command -v trivy >/dev/null || { echo NA; exit 0; }
sha=$(bash ops/bands/metrics/deployed-sha.sh)
[ -n "$sha" ] || { echo NA; exit 0; }
owner=$(gh repo view --json owner --jq '.owner.login' | tr '[:upper:]' '[:lower:]')
ignore=$(mktemp)
if ! git show "$sha:.trivyignore.yaml" >"$ignore" 2>/dev/null; then
  git fetch -q origin "$sha" && git show "$sha:.trivyignore.yaml" >"$ignore" 2>/dev/null
fi
total=0
for image in hururaa-keycloak hururaa-gateway hururaa-api hururaa-frontend; do
  n=$(trivy image --quiet --scanners vuln --severity CRITICAL,HIGH --ignore-unfixed \
    --ignorefile "$ignore" --format json "ghcr.io/$owner/$image:$sha" 2>/dev/null |
    jq '[.Results[]?.Vulnerabilities[]?] | length' 2>/dev/null)
  total=$((total + ${n:-0}))
done
rm -f "$ignore"
echo "$total"
