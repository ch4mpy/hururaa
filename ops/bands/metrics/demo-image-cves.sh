#!/usr/bin/env bash
# HIGH/CRITICAL vulnerabilities with an upstream fix in the images running on the demo, with the
# exemptions of that version's .trivyignore.yaml (the same scan as the deployment, run again: new
# CVEs are published after a deployment). NA when Trivy isn't installed, when the deployed version
# is unknown, or when any scan fails (a failed scan must not be recorded as "0 vulnerabilities").
set -u
command -v trivy >/dev/null || { echo NA; exit 0; }
sha=$(bash ops/bands/metrics/deployed-sha.sh)
[ -n "$sha" ] || { echo NA; exit 0; }
owner=$(gh repo view --json owner --jq '.owner.login' | tr '[:upper:]' '[:lower:]')
# Trivy reads an ignore file as YAML only when its name ends in .yaml or .yml
ignore=$(mktemp --suffix=.yaml)
trap 'rm -f "$ignore" "$ignore.json"' EXIT
if ! git show "$sha:.trivyignore.yaml" >"$ignore" 2>/dev/null; then
  git fetch -q origin "$sha" && git show "$sha:.trivyignore.yaml" >"$ignore" 2>/dev/null || { echo NA; exit 0; }
fi
total=0
for image in hururaa-keycloak hururaa-gateway hururaa-api hururaa-frontend; do
  trivy image --quiet --scanners vuln --severity CRITICAL,HIGH --ignore-unfixed \
    --ignorefile "$ignore" --format json --output "$ignore.json" "ghcr.io/$owner/$image:$sha" 2>/dev/null ||
    { echo NA; exit 0; }
  n=$(jq '[.Results[]?.Vulnerabilities[]?] | length' "$ignore.json" 2>/dev/null)
  [[ "$n" =~ ^[0-9]+$ ]] || { echo NA; exit 0; }
  total=$((total + n))
done
echo "$total"
