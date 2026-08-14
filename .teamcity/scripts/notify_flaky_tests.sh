#!/bin/bash
# Orchestrates the consolidated flaky-test report for "Build All Core"
# (Ktor_KtorCore_All) and notifies @ktor-incident-responders on Slack.
#
# This file IS the TeamCity script step, so TeamCity resolves %...% references in
# it. It exports the resolved values as environment variables for the sourced
# helpers (lib_flaky.sh) and the collector scripts, which are plain files read by
# bash at runtime and therefore do NOT get %...% substitution.
#
# Sources:
#   - collect_teamcity.sh   : TeamCity retry-diff of Ktor_KtorCore_All (this run).
#   - collect_develocity.sh : Develocity 28d chronic-flaky classes (best-effort).
#   - collect_quarantine.sh : the @Flaky / _flaky tests run by the scheduled flaky-test build.
#                             The gate skips those, so without this source quarantining a test
#                             would hide it from the very report meant to track it (best-effort).
# merge_report.sh dedups across sources, resolves targets, and writes the
# consolidated flaky-report.{json,md,html} artifacts (surfaced as a report tab).
set -euo pipefail

# TeamCity-resolved values (exported for the helpers and collectors).
export TC_SERVER_URL="%teamcity.serverUrl%"
export WATCHED_BUILD_TYPE="Ktor_KtorCore_All"
export QUARANTINE_BUILD_TYPE="%quarantine.build.type%"
TC_BUILD_ID="%teamcity.build.id%"
SUBTEAM_ID="%slack.ktor.team.subteam.id%"

# Locate the helper scripts.
if [ -n "${FLAKY_SCRIPTS_DIR:-}" ]; then
  SCRIPTS_DIR="$FLAKY_SCRIPTS_DIR"
elif [ -f "$(dirname "${BASH_SOURCE[0]}")/lib_flaky.sh" ]; then
  SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
elif [ -f ".teamcity/scripts/lib_flaky.sh" ]; then
  SCRIPTS_DIR=".teamcity/scripts"
else
  echo "Cannot locate lib_flaky.sh (set FLAKY_SCRIPTS_DIR to the scripts directory)." >&2
  exit 1
fi
export FLAKY_SCRIPTS_DIR="$SCRIPTS_DIR"
# shellcheck source=lib_flaky.sh
source "$SCRIPTS_DIR/lib_flaky.sh"

require_tc_token || exit 1

# --- Collect (each source writes normalized JSON) ---
TC_FILE=$(mktemp)
DV_FILE=$(mktemp)
QR_FILE=$(mktemp)
trap 'rm -f "$TC_FILE" "$DV_FILE" "$QR_FILE"' EXIT

bash "$SCRIPTS_DIR/collect_teamcity.sh" > "$TC_FILE"
# collect_develocity.sh always exits 0 with valid JSON.
bash "$SCRIPTS_DIR/collect_develocity.sh" > "$DV_FILE"
# collect_quarantine.sh exits 0 with valid JSON while the build has no runs yet.
bash "$SCRIPTS_DIR/collect_quarantine.sh" > "$QR_FILE"

# --- Merge + write report artifacts (into the build working dir) ---
CONSOLIDATED=$(bash "$SCRIPTS_DIR/merge_report.sh" "$TC_FILE" "$DV_FILE" "$QR_FILE" ".")

THIS_RUN_COUNT=$(echo "$CONSOLIDATED" | jq -r '.thisRun | length')
DV_AVAILABLE=$(echo "$CONSOLIDATED" | jq -r '.dv.available')
QR_AVAILABLE=$(echo "$CONSOLIDATED" | jq -r '.quarantine.available')
QR_ALWAYS_FAILING=$(echo "$CONSOLIDATED" | jq -r '.quarantine.counts.alwaysFailing')
QR_STABLE=$(echo "$CONSOLIDATED" | jq -r '.quarantine.counts.stableCandidate')
QR_STILL_FLAKY=$(echo "$CONSOLIDATED" | jq -r '.quarantine.counts.stillFlaky')
echo "This run: $THIS_RUN_COUNT flaky test(s). Develocity available: $DV_AVAILABLE. Quarantine available: $QR_AVAILABLE. Report written to flaky-report.{json,md,html}."

# A quarantined test that always fails, or has stopped flipping, needs a human decision — report it
# even on a run where nothing flipped, since otherwise it stays invisible indefinitely.
QUARANTINE_LINE=""
if [ "$QR_AVAILABLE" = "true" ] && { [ "$QR_ALWAYS_FAILING" -gt 0 ] || [ "$QR_STABLE" -gt 0 ]; }; then
  QUARANTINE_LINE="Quarantine — still flaky: $QR_STILL_FLAKY · always failing: $QR_ALWAYS_FAILING (fix or @Ignore) · ready to un-quarantine: $QR_STABLE
"
fi

# Notify when THIS run flipped something (the original trigger intent) or when quarantine needs a
# decision; the report artifacts are published regardless.
if [ "$THIS_RUN_COUNT" -eq 0 ] && [ -z "$QUARANTINE_LINE" ]; then
  exit 0
fi

echo "$CONSOLIDATED" | jq -r '
  .thisRun | sort_by(.target, .name)[]
  | "  [" + .target + (if (.targetDetail // "") != "" then ":" + .targetDetail else "" end) + "] " + .name
    + (if .chronic then "  (chronic 28d)" else "" end)'

# --- Notify: Slack ---
LATEST_REVISION=$(echo "$CONSOLIDATED" | jq -r '.revision // ""')
TARGET_SUMMARY=$(echo "$CONSOLIDATED" | jq -r '.byTargetCounts | to_entries | map(.key + ": " + (.value|tostring)) | join(", ")')
OVERLAP_COUNT=$(echo "$CONSOLIDATED" | jq -r '.overlapCount')

TEST_LIST=$(echo "$CONSOLIDATED" | jq -r '
  .thisRun | sort_by(.target, .name) | .[:15][]
  | "• [" + .target + (if (.targetDetail // "") != "" then ":" + .targetDetail else "" end) + "] " + .name
    + (if .chronic then "  ⚠︎ chronic(28d)" else "" end)')
if [ "$THIS_RUN_COUNT" -gt 15 ]; then
  TEST_LIST="$TEST_LIST
…and $((THIS_RUN_COUNT - 15)) more"
fi

# One-line chronic context from Develocity (top 3 classes by 28d flaky count).
CHRONIC_LINE=""
if [ "$DV_AVAILABLE" = "true" ]; then
  CHRONIC_TOP=$(echo "$CONSOLIDATED" | jq -r '
    (.chronic[0:3] | map((.class | split(".") | last) + " (" + (.flaky|tostring) + ")") | join(", ")) // ""')
  if [ -n "$CHRONIC_TOP" ]; then
    CHRONIC_LINE="Chronic (Develocity 28d) — top: $CHRONIC_TOP · $OVERLAP_COUNT of this run also chronic
"
  fi
fi

# @-mention the @ktor-incident-responders user group when its Slack id is configured.
if [ -n "$SUBTEAM_ID" ] && ! echo "$SUBTEAM_ID" | grep -q '%.*%'; then
  MENTION="<!subteam^$SUBTEAM_ID|@ktor-incident-responders>"
else
  echo "slack.ktor.team.subteam.id user group is not set." >&2
  MENTION="@ktor-incident-responders"
fi

BUILD_URL="$TC_SERVER_URL/viewLog.html?buildId=$TC_BUILD_ID"
if [ "$THIS_RUN_COUNT" -gt 0 ]; then
  HEADLINE="$THIS_RUN_COUNT flaky test(s) in *Build All Core* (retry-diff) on revision \`${LATEST_REVISION:0:12}\` — by target: $TARGET_SUMMARY"
else
  # Reached only because quarantine needs a decision, so don't claim a retry-diff finding.
  HEADLINE="nothing flipped in *Build All Core*, but quarantined tests need a decision"
fi
MESSAGE=":warning: $MENTION — $HEADLINE
$TEST_LIST
${CHRONIC_LINE}${QUARANTINE_LINE}<$BUILD_URL|View build> — full report in the *Flaky Tests* tab"

post_to_slack "$MESSAGE"
