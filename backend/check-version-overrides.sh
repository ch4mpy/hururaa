#!/usr/bin/env bash
#
# Guards the version properties that backend/pom.xml overrides from the Spring Boot BOM.
#
# Such an override only exists to raise a dependency past a CVE that the current Spring Boot
# release still ships. It is meant to be temporary, and it is dangerous to leave behind: once Boot
# manages a version at least as high, the override stops adding anything, and as soon as Boot moves
# past it the override silently *downgrades* the dependency. Nothing else catches that -- the
# vulnerability scanners see a version with no known CVE and stay quiet.
#
# So this script compares every version property declared in backend/pom.xml against the same
# property in the spring-boot-dependencies BOM of the parent release, and fails when ours is no
# longer strictly greater. Overrides are discovered, not listed here: a property counts as one as
# soon as the BOM declares it too.
#
# Run it from anywhere; CI runs it in the backend job.
set -euo pipefail

POM="$(cd "$(dirname "$0")" && pwd)/pom.xml"
[ -f "$POM" ] || { echo "no pom.xml next to $0" >&2; exit 1; }

# The <version> of the <parent> block, i.e. the spring-boot-starter-parent release in use.
boot_version=$(awk '/<parent>/,/<\/parent>/' "$POM" | sed -n 's:.*<version>\(.*\)</version>.*:\1:p' | head -1)
[ -n "$boot_version" ] || { echo "could not read the Spring Boot parent version from $POM" >&2; exit 1; }

bom_url="https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/${boot_version}/spring-boot-dependencies-${boot_version}.pom"
bom=$(mktemp)
trap 'rm -f "$bom"' EXIT
curl -fsSL --max-time 60 "$bom_url" -o "$bom" || {
  echo "could not download $bom_url" >&2
  exit 1
}

echo "Spring Boot parent: $boot_version"

# Only the properties we declare ourselves matter: an inherited one is not an override.
ours=$(awk '/<properties>/,/<\/properties>/' "$POM" \
  | sed -n 's:.*<\([a-zA-Z0-9._-]*\.version\)>\([^<]*\)</\1>.*:\1 \2:p')

status=0
found=0
while read -r name value; do
  [ -n "${name:-}" ] || continue
  managed=$(sed -n "s:.*<${name}>\([^<]*\)</${name}>.*:\1:p" "$bom" | head -1)
  # A property the BOM does not know is ours alone (plugin versions, project settings...).
  [ -n "$managed" ] || continue
  found=$((found + 1))
  if [ "$value" = "$managed" ]; then
    echo "STALE  $name: Spring Boot $boot_version now manages $managed itself -> delete the override from backend/pom.xml"
    status=1
  elif [ "$(printf '%s\n%s\n' "$value" "$managed" | sort -V | head -1)" = "$value" ]; then
    echo "DOWNGRADE  $name: pinned to $value while Spring Boot $boot_version manages $managed -> delete the override from backend/pom.xml"
    status=1
  else
    echo "ok  $name: $value overrides the $managed managed by Spring Boot $boot_version"
  fi
done <<< "$ours"

[ "$found" -gt 0 ] || echo "no version property of backend/pom.xml overrides the Spring Boot BOM"
exit $status
