#!/bin/bash
# Reports on the quarantined tests run by the scheduled "Flaky (Quarantined) Tests" build.
#
# Across the last QUARANTINE_RUNS runs each test gets a verdict:
#   still-flaky      : both passed and failed  -> genuinely flaky, keep quarantined
#   always-failing   : only ever failed        -> not flaky, it is broken; fix or @Ignore it
#   stable-candidate : only ever passed        -> ready to un-quarantine
set -euo pipefail

SCRIPTS_DIR="${FLAKY_SCRIPTS_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
source "$SCRIPTS_DIR/lib_flaky.sh"

QUARANTINE_BUILD_TYPE="${QUARANTINE_BUILD_TYPE:-Ktor_KtorCore_FlakyTest}"
WATCHED_BUILD_TYPE="${WATCHED_BUILD_TYPE:-Ktor_KtorCore_All}"
QUARANTINE_RUNS="${QUARANTINE_RUNS:-7}"
# A test needs this many clean runs before it is suggested for un-quarantining.
QUARANTINE_STABLE_RUNS="${QUARANTINE_STABLE_RUNS:-3}"

emit_empty() {
  jq -n --arg bt "$QUARANTINE_BUILD_TYPE" '
    {available: false, buildType: $bt, runs: 0, tests: [],
     counts: {stillFlaky: 0, alwaysFailing: 0, stableCandidate: 0}}'
}

# Best-effort, like the Develocity source: a missing build must not fail the notifier.
if ! require_tc_token; then
  emit_empty
  exit 0
fi

BUILDS_JSON=$(teamcityApiRequest \
  "/builds?locator=buildType:$QUARANTINE_BUILD_TYPE,branch:(default:true),state:finished,count:$QUARANTINE_RUNS&fields=build(id)") || {
  echo "Cannot read $QUARANTINE_BUILD_TYPE (does the build configuration exist yet?); skipping the quarantine source." >&2
  emit_empty
  exit 0
}

RUN_IDS=$(echo "$BUILDS_JSON" | jq -r '.build[]?.id')
RUN_COUNT=$(echo "$RUN_IDS" | grep -c . || true)
if [ "$RUN_COUNT" -eq 0 ]; then
  echo "No finished runs of $QUARANTINE_BUILD_TYPE yet; nothing to report." >&2
  emit_empty
  exit 0
fi
echo "Found $RUN_COUNT finished run(s) of $QUARANTINE_BUILD_TYPE." >&2

# The tests the gate skips. `ignored:true` covers both @Ignore and an ExecutionCondition opting out.
GATE_BUILD_ID=$(teamcityApiRequest \
  "/builds?locator=buildType:$WATCHED_BUILD_TYPE,branch:(default:true),state:finished,count:1&fields=build(id)" \
  | jq -r '.build[0].id // empty') || GATE_BUILD_ID=""

SKIPPED_IN_GATE=""
if [ -n "$GATE_BUILD_ID" ]; then
  SKIPPED_IN_GATE=$(teamcityApiRequest \
    "/testOccurrences?locator=build:(id:$GATE_BUILD_ID),ignored:true,count:10000&fields=testOccurrence(name)" \
    | jq -r '.testOccurrence[]?.name')
fi
SKIPPED_COUNT=$(echo "$SKIPPED_IN_GATE" | grep -c . || true)
echo "$WATCHED_BUILD_TYPE skips $SKIPPED_COUNT test(s)." >&2

# Executed results from the quarantine runs (skipped ones carry no verdict, so exclude them).
QUARANTINE_RESULTS=$(
  for id in $RUN_IDS; do
    teamcityApiRequest \
      "/testOccurrences?locator=build:(id:$id),ignored:false,count:10000&fields=testOccurrence(name,status)" \
      | jq -r '.testOccurrence[]? | [.status, .name] | @tsv'
  done
)

# Intersect, tally, and classify. The target comes from the `[target]` suffix Kotlin appends to
# multiplatform test names; unlike collect_teamcity.sh there is no per-target build to read it from.
TESTS_TSV=$(awk -F '\t' -v stableRuns="$QUARANTINE_STABLE_RUNS" '
  function classify(name) {
    if (name ~ /wasmJs/)                                                  { return "wasmJs\t" }
    if (name ~ /\[js[,\]]/)                                               { return "js\t" }
    if (match(name, /mingwX64|linuxX64|linuxArm64|macosX64|macosArm64/))  { return "native\t" substr(name, RSTART, RLENGTH) }
    if (name ~ /\[jvm\]/ || name ~ /\[Android\]/)                         { return "jvm\t" }
    return "jvm\t"   # the JVM step reports plain names, without a target suffix
  }
  NR == FNR { skipped[$0] = 1; next }
  {
    status = $1; name = $2
    if (name == "" || !(name in skipped)) next
    if (status == "SUCCESS") passed[name]++
    else if (status == "FAILURE") failed[name]++
  }
  END {
    for (n in passed) names[n] = 1
    for (n in failed) names[n] = 1
    for (n in names) {
      p = (n in passed) ? passed[n] : 0
      f = (n in failed) ? failed[n] : 0
      if (p > 0 && f > 0)            verdict = "still-flaky"
      else if (f > 0)                verdict = "always-failing"
      else if (p >= stableRuns)      verdict = "stable-candidate"
      else                           verdict = "still-flaky"   # too few clean runs to judge
      print classify(n) "\t" n "\t" p "\t" f "\t" verdict
    }
  }' <(echo "$SKIPPED_IN_GATE") <(echo "$QUARANTINE_RESULTS") | sort -u)

jq -Rn --arg bt "$QUARANTINE_BUILD_TYPE" --argjson runs "$RUN_COUNT" '
  [inputs
    | split("\t")
    | select(length == 6)
    | {target: .[0], targetDetail: .[1], name: .[2],
       passed: (.[3]|tonumber), failed: (.[4]|tonumber), verdict: .[5]}]
  | sort_by(.verdict, .target, .name) as $tests
  | {available: true, buildType: $bt, runs: $runs, tests: $tests,
     counts: {
       stillFlaky:      ([$tests[] | select(.verdict == "still-flaky")]      | length),
       alwaysFailing:   ([$tests[] | select(.verdict == "always-failing")]   | length),
       stableCandidate: ([$tests[] | select(.verdict == "stable-candidate")] | length)
     }}' <<< "$TESTS_TSV"
