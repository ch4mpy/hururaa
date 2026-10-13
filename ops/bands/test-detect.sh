#!/usr/bin/env bash
# Tests of ops/bands/detect.sh: fixed thresholds on a short history; rules 1, 2 and 4 on a 25-point
# history (mean 2, small spread); the fixed thresholds as a floor of the rules on a count that is
# almost always 0; NA; the recorded point; --simulate. Each case uses its own data folder.
# usage: bash ops/bands/test-detect.sh (exit 1 on any failure)
set -u
cd "$(git rev-parse --show-toplevel)" || exit 1
S=$(mktemp -d)
fails=0
cfg() { # value [diagnose propose] -> a one-metric config whose command prints that value
  jq -n --arg v "$1" --argjson d "${2:-3}" --argjson p "${3:-6}" '{minPoints: 20, metrics: [{name: "m",
    description: "test", command: ("echo " + $v), windowDays: 30, fixed: {diagnose: $d, propose: $p}}]}' >"$S/cfg.json"
}
history() { # dir values... -> one point per hour, oldest first, all within the window
  mkdir -p "$1"; : >"$1/m.csv"
  local d=$1; shift; local i=$#
  for v in "$@"; do echo "$(date -u -d "-$i hours" +%Y-%m-%dT%H:%M:%SZ),$v" >>"$d/m.csv"; i=$((i - 1)); done
}
check() { # label expected-rule/tier-or-none data-dir
  got=$(bash ops/bands/detect.sh --config "$S/cfg.json" --data "$3" 2>/dev/null | jq -r '.rule + "/" + .tier' | head -1)
  got=${got:-none}
  if [ "$got" = "$2" ]; then v=PASS; else v=FAIL; fails=$((fails + 1)); fi
  printf '%s %-46s got %-18s want %s\n' "$v" "$1" "$got" "$2"
}
base=(2 1 3 2 2 1 3 2 2 1 3 2 2 1 3 2 2 1 3 2 2 1 3 2 2) # 25 points, mean 2
zeros=(0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1) # a count, almost always 0

cfg 7; history "$S/a" 1 2;                    check "short history, 7 >= propose 6"            "fixed>=6/propose" "$S/a"
cfg 4; history "$S/b" 1 2;                    check "short history, 4 >= diagnose 3"           "fixed>=3/diagnose" "$S/b"
cfg 1; history "$S/c" 1 2;                    check "short history, 1: no breach"              "none" "$S/c"
cfg 9 0 0; history "$S/d" "${base[@]}";       check "rule 1: 9 > mean + 3 sd"                  "1/propose" "$S/d"
cfg 3.8 0 0; history "$S/e" "${base[@]}" 3.8; check "rule 2: 2 of 3 > mean + 2 sd"             "2/diagnose" "$S/e"
cfg 2.6 0 0; history "$S/f" "${base[@]}" 2.6 2.6 2.6 2.6 2.6 2.6 2.6
                                              check "rule 4: 8 points above the mean"          "4/diagnose" "$S/f"
cfg 2 0 0; history "$S/g" "${base[@]}";       check "long history, 2: no breach"               "none" "$S/g"
cfg 1 1 2; history "$S/k" "${zeros[@]}";      check "floor: 1 event, rule 1 capped to diagnose" "1/diagnose" "$S/k"
cfg 1 2 3; history "$S/l" "${zeros[@]}";      check "floor: 1 event below fixed.diagnose"      "none" "$S/l"
cfg 3 1 2; history "$S/m" "${zeros[@]}";      check "floor: 3 events reach fixed.propose"      "1/propose" "$S/m"
cfg NA; history "$S/h" 1 2;                   check "NA: nothing measured"                     "none" "$S/h"

points=$(wc -l <"$S/a/m.csv")
if [ "$points" -eq 3 ]; then echo "PASS point recorded"; else echo "FAIL point recorded: $points lines"; fails=$((fails + 1)); fi
got=$(bash ops/bands/detect.sh --config "$S/cfg.json" --data "$S/i" --simulate m 2>/dev/null | jq -r '.rule + "/" + .tier')
if [ "$got" = "simulated/propose" ]; then echo "PASS --simulate"; else echo "FAIL --simulate: $got"; fails=$((fails + 1)); fi
rm -rf "$S"
echo "failures: $fails"
[ "$fails" -eq 0 ]
