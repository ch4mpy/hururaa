#!/usr/bin/env bash
# Deterministic detection (no model): measures each metric of ops/bands/bands.json, appends the
# value to its history, compares it with its rolling baseline, and prints the breaches as JSON
# lines. Claude is not involved here; it only triages the issues the workflow opens from them.
#
#   ops/bands/detect.sh [--config <file>] [--data <dir>] [--simulate <metric>] [<metric>...]
#
# --config    default ops/bands/bands.json (another file for tests)
# --data      history folder, one <metric>.csv per metric ("<UTC timestamp>,<value>"); default
#             ops/bands/data (the bands workflow checks out the bands-data branch there)
# --simulate  records nothing for that metric and reports a breach with rule "simulated" (tier
#             propose): to test the chain issue → triage without breaking anything
#
# Rules (Western Electric, one-sided: higher is worse for every metric here), against the mean and
# standard deviation of the points of the last windowDays, the current point excluded:
#   1  the point is beyond mean + 3σ                       → propose
#   2  2 of the last 3 points beyond mean + 2σ             → diagnose
#   4  the last 8 points above the mean (drift)            → diagnose
# While the history holds fewer than minPoints points, or σ is 0, the metric's fixed thresholds
# apply instead (value ≥ fixed.propose → propose, ≥ fixed.diagnose → diagnose). They also stay a
# floor for the rules above: below fixed.diagnose no breach, below fixed.propose at most diagnose.
# Each breach line: {"metric","value","mean","sd","n","rule","tier","description"}.
set -u
cd "$(git rev-parse --show-toplevel)" || exit 1
config=ops/bands/bands.json
data=ops/bands/data
simulate=""
only=()
while [ $# -gt 0 ]; do
  case "$1" in
    --config) config=$2; shift 2 ;;
    --data) data=$2; shift 2 ;;
    --simulate) simulate=$2; shift 2 ;;
    -h|--help) sed -n '2,28p' "$0"; exit 0 ;;
    *) only+=("$1"); shift ;;
  esac
done
mkdir -p "$data"
min_points=$(jq -r '.minPoints // 20' "$config")
now=$(date -u +%Y-%m-%dT%H:%M:%SZ)

jq -c '.metrics[]' "$config" | while IFS= read -r metric; do
  name=$(jq -r .name <<<"$metric")
  if [ ${#only[@]} -gt 0 ] && [[ ! " ${only[*]} " == *" $name "* ]]; then continue; fi
  description=$(jq -r '.description // ""' <<<"$metric")
  emit() { # value mean sd n rule tier
    jq -cn --arg m "$name" --arg v "$1" --arg mean "$2" --arg sd "$3" --arg n "$4" --arg r "$5" \
      --arg t "$6" --arg d "$description" \
      '{metric: $m, value: $v, mean: $mean, sd: $sd, n: ($n | tonumber), rule: $r, tier: $t, description: $d}'
  }
  if [ "$name" = "$simulate" ]; then
    emit "simulated" "-" "-" 0 simulated propose
    continue
  fi

  value=$(bash -c "$(jq -r .command <<<"$metric")" 2>/dev/null | tail -1 | tr -d '\r[:space:]')
  echo "$name = ${value:-NA}" >&2
  [[ "$value" =~ ^[0-9]+([.][0-9]+)?$ ]] || continue # NA: nothing measured this time

  history="$data/$name.csv"
  touch "$history"
  window=$(jq -r '.windowDays // 30' <<<"$metric")
  since=$(date -u -d "-$window days" +%Y-%m-%dT%H:%M:%SZ)
  # the baseline: points of the window, before this one
  baseline=$(awk -F, -v since="$since" '$1 >= since { print $2 }' "$history")
  echo "$now,$value" >>"$history"

  stats=$(awk '{ s += $1; q += $1 * $1; n++ } END {
      if (n == 0) { print "0 0 0"; exit }
      m = s / n; v = q / n - m * m; if (v < 0) v = 0
      printf "%.4f %.4f %d\n", m, sqrt(v), n }' <<<"$baseline")
  read -r mean sd n <<<"$stats"
  diagnose=$(jq -r '.fixed.diagnose' <<<"$metric")
  propose=$(jq -r '.fixed.propose' <<<"$metric")

  if [ "$n" -lt "$min_points" ] || awk -v sd="$sd" 'BEGIN { exit !(sd == 0) }'; then
    if awk -v v="$value" -v t="$propose" 'BEGIN { exit !(v >= t) }'; then
      emit "$value" "$mean" "$sd" "$n" "fixed>=$propose" propose
    elif awk -v v="$value" -v t="$diagnose" 'BEGIN { exit !(v >= t) }'; then
      emit "$value" "$mean" "$sd" "$n" "fixed>=$diagnose" diagnose
    fi
    continue
  fi

  # the last points, this one included, for rules 2 and 4
  recent=$( (awk -F, -v since="$since" '$1 >= since { print $2 }' "$history") | tail -8)
  rule=""
  tier=""
  if awk -v v="$value" -v m="$mean" -v s="$sd" 'BEGIN { exit !(v > m + 3 * s) }'; then
    rule=1; tier=propose
  elif [ "$(tail -3 <<<"$recent" | awk -v m="$mean" -v s="$sd" '$1 > m + 2 * s { c++ } END { print c + 0 }')" -ge 2 ]; then
    rule=2; tier=diagnose
  elif [ "$(wc -l <<<"$recent")" -ge 8 ] &&
    [ "$(awk -v m="$mean" '$1 > m { c++ } END { print c + 0 }' <<<"$recent")" -eq 8 ]; then
    rule=4; tier=diagnose
  fi
  [ -n "$rule" ] || continue
  # The fixed thresholds stay a floor: on counts that are almost always 0, σ is tiny and a single
  # event would otherwise pass rule 1 or 4. Below fixed.diagnose, no breach; below fixed.propose,
  # no more than diagnose.
  awk -v v="$value" -v t="$diagnose" 'BEGIN { exit !(v >= t) }' || continue
  if [ "$tier" = propose ] && ! awk -v v="$value" -v t="$propose" 'BEGIN { exit !(v >= t) }'; then
    tier=diagnose
  fi
  emit "$value" "$mean" "$sd" "$n" "$rule" "$tier"
done
