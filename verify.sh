#!/usr/bin/env bash
# One command to check a change before calling it done (CONTRIBUTING.md, « Vérifier son travail »).
# The local equivalent of the CI jobs (.github/workflows/ci.yml): keep both in step.
#
#   ./verify.sh              backend and frontend, from scratch (clean build, localized build)
#   ./verify.sh --quick      no clean, no production build: faster, for intermediate checks
#   ./verify.sh --backend    backend only (--frontend: frontend only)
#
# The last line is "VERIFY OK" or "VERIFY KO: <stage>" (exit code 0 / 1). JDK and Node versions
# come from .sdkmanrc and .nvmrc; the JDK is looked up in SDKMAN's candidates when the current one
# differs. Dependencies are assumed installed (npm ci, npm run api: see README).
set -u
cd "$(dirname "$0")" || exit 1
root=$(pwd)

backend=true
frontend=true
quick=false
for arg in "$@"; do
  case "$arg" in
    --quick) quick=true ;;
    --backend) frontend=false ;;
    --frontend) backend=false ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

ko() {
  echo
  echo "VERIFY KO: $1"
  exit 1
}

step() {
  echo
  echo "=== $1"
}

if $backend; then
  java_version=$(sed -n 's/^java=//p' .sdkmanrc | tr -d '\r')
  java_major=${java_version%%.*}
  current_major=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
  if [ "${current_major:-}" != "$java_major" ]; then
    candidate="$HOME/.sdkman/candidates/java/$java_version"
    [ -d "$candidate" ] || ko "JDK $java_version not found (current: ${current_major:-none}; sdk env install)"
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
  step "backend: Spring Boot version overrides"
  bash backend/check-version-overrides.sh || ko "backend version overrides"
  step "backend: build and tests"
  goals="verify"
  $quick || goals="clean verify"
  (cd backend && bash ./mvnw -B -ntp $goals) || ko "backend build and tests"
fi

if $frontend; then
  node_major=$(tr -d '\r' <.nvmrc)
  current_node=$(node -v 2>/dev/null | sed 's/^v\([0-9]*\).*/\1/')
  [ "${current_node:-}" = "${node_major%%.*}" ] ||
    echo "warning: Node ${current_node:-none} found, .nvmrc asks for $node_major" >&2
  cd "$root/frontend/angular" || ko "frontend folder"
  step "frontend: lint"
  npm run lint || ko "frontend lint"
  step "frontend: tests"
  npm run test:ci || ko "frontend tests"
  if ! $quick; then
    step "frontend: localized build"
    npm run build || ko "frontend build"
  fi
fi

echo
echo "VERIFY OK"
