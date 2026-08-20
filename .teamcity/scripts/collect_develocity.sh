#!/bin/bash
set -euo pipefail

SCRIPTS_DIR="${FLAKY_SCRIPTS_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
source "$SCRIPTS_DIR/lib_flaky.sh"

DV_ROOT_PROJECT="${DV_ROOT_PROJECT:-ktor}"
DV_WINDOW_DAYS="${DV_WINDOW_DAYS:-28}"
DV_MIN_FLAKY="${DV_MIN_FLAKY:-1}"

emit_empty() {
  jq -n --argjson days "$DV_WINDOW_DAYS" \
    '{available: false, windowDays: $days, projectFlaky: 0, projectFailed: 0, classes: []}'
}

if ! require_dv_key; then
  emit_empty
  exit 0
fi

NOW_MS=$(( $(date +%s) * 1000 ))
MIN_MS=$(( NOW_MS - DV_WINDOW_DAYS * 86400 * 1000 ))

RESP=$(develocityApiRequest \
  "/tests-data/top?rootProjectNames=${DV_ROOT_PROJECT}&sortField=FLAKY&startTimeMin=${MIN_MS}&startTimeMax=${NOW_MS}&timeZoneId=UTC") || {
  echo "Develocity query failed; continuing without the Develocity source." >&2
  emit_empty
  exit 0
}

# NOTE: `/tests-data/top` aggregates at the CLASS level only — `.flaky` is the count of flaky
# test-run OUTCOMES for the class over the window, NOT a distinct test-method count.
echo "$RESP" | jq --argjson days "$DV_WINDOW_DAYS" --argjson minFlaky "$DV_MIN_FLAKY" '
  {
    available: true,
    windowDays: $days,
    projectFlaky: (.data.flakyOutcomeTrend.total // 0),
    projectFailed: (.data.failedOutcomeTrend.total // 0),
    classes: [
      (.data.topTests.tests // [])[]
      | {
          class: .name,
          flaky:  (.outcomeTrend.totalDistribution.flaky  // 0),
          failed: (.outcomeTrend.totalDistribution.failed // 0),
          total:  (.outcomeTrend.totalDistribution.total  // 0),
          meanMs: (.meanWallClockDuration // 0)
        }
      | select(.flaky >= $minFlaky)
    ]
  }'
