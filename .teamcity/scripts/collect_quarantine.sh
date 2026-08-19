#!/bin/bash
# Reports on the quarantined tests run by the scheduled "Flaky (Quarantined) Tests" builds
# (one per OS: Linux + per-OS native — see QUARANTINE_BUILD_TYPES).
#
# Across the last QUARANTINE_RUNS runs of each build, every test gets a verdict:
#   still-flaky      : both passed and failed  -> genuinely flaky, keep quarantined
#   always-failing   : only ever failed        -> not flaky, it is broken; fix or @Ignore it
#   stable-candidate : only ever passed        -> ready to un-quarantine
set -euo pipefail

SCRIPTS_DIR="${FLAKY_SCRIPTS_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
source "$SCRIPTS_DIR/lib_flaky.sh"

# Space-separated list of quarantine build external ids (Linux + per-OS native). Falls back to the
# legacy single QUARANTINE_BUILD_TYPE, then to the Linux build, so older callers still work.
QUARANTINE_BUILD_TYPES="${QUARANTINE_BUILD_TYPES:-${QUARANTINE_BUILD_TYPE:-Ktor_KtorCore_FlakyTest}}"
QUARANTINE_RUNS="${QUARANTINE_RUNS:-7}"
# A test needs this many clean runs before it is suggested for un-quarantining.
QUARANTINE_STABLE_RUNS="${QUARANTINE_STABLE_RUNS:-3}"
# Max test occurrences fetched per query; warn_if_truncated flags a page that comes back at the cap.
OCC_CAP="${TC_TEST_OCCURRENCE_CAP:-10000}"

emit_empty() {
  jq -n --arg bt "$QUARANTINE_BUILD_TYPES" '
    {available: false, buildType: $bt, runs: 0, tests: [],
     counts: {stillFlaky: 0, alwaysFailing: 0, stableCandidate: 0}}'
}

# Best-effort, like the Develocity source: a missing build must not fail the notifier.
if ! require_tc_token; then
  emit_empty
  exit 0
fi

# Executed results across the last QUARANTINE_RUNS runs of every quarantine build.
QR_RESULTS_FILE=$(mktemp)
trap 'rm -f "$QR_RESULTS_FILE"' EXIT
RUNS_READ=0        # total runs read across all builds (drives the "any data?" check)
MAX_RUNS=0         # deepest single-build sampling window, reported as `runs`

for bt in $QUARANTINE_BUILD_TYPES; do
  if ! builds_json=$(teamcityApiRequest \
      "/builds?locator=buildType:$bt,branch:(default:true),state:finished,count:$QUARANTINE_RUNS&fields=build(id)"); then
    echo "Cannot read $bt (does the build configuration exist yet?); skipping it." >&2
    continue
  fi
  run_ids=$(printf '%s' "$builds_json" | jq -r '.build[]?.id')
  run_count=$(printf '%s\n' "$run_ids" | grep -c . || true)
  if [ "$run_count" -eq 0 ]; then
    echo "No finished runs of $bt yet." >&2
    continue
  fi
  echo "Found $run_count finished run(s) of $bt." >&2

  bt_runs=0
  for id in $run_ids; do
    if ! occurrences=$(teamcityApiRequest \
        "/testOccurrences?locator=build:(id:$id),ignored:false,count:$OCC_CAP&fields=testOccurrence(name,status)"); then
      echo "Skipping run $id of $bt: testOccurrences request failed." >&2
      continue
    fi
    warn_if_truncated "$occurrences" "$OCC_CAP" "$bt run $id"
    if ! printf '%s' "$occurrences" \
        | jq -r '.testOccurrence[]? | [.status, .name] | @tsv' >> "$QR_RESULTS_FILE"; then
      echo "Skipping run $id of $bt: could not parse test occurrences." >&2
      continue
    fi
    bt_runs=$((bt_runs + 1))
    RUNS_READ=$((RUNS_READ + 1))
  done
  if [ "$bt_runs" -gt "$MAX_RUNS" ]; then MAX_RUNS=$bt_runs; fi
done

if [ "$RUNS_READ" -eq 0 ]; then
  echo "No quarantine runs could be read; skipping the quarantine source." >&2
  emit_empty
  exit 0
fi

# Tally + classify. Every executed test is a quarantined test (the builds run only flaky tests), so
# there is no gate intersection. The target comes from the `[target]` suffix Kotlin appends to
# multiplatform test names, via the shared classifier; the JVM step reports plain names -> jvm.
QUARANTINE_RESULTS=$(cat "$QR_RESULTS_FILE")
TESTS_TSV=$(awk -F '\t' -v stableRuns="$QUARANTINE_STABLE_RUNS" "$FLAKY_AWK_CLASSIFY"'
  {
    status = $1; name = $2
    if (name == "") next
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
      print flaky_classify_by_name(n, "jvm") "\t" n "\t" p "\t" f "\t" verdict
    }
  }' <<< "$QUARANTINE_RESULTS" | sort -u)

jq -Rn --arg bt "$QUARANTINE_BUILD_TYPES" --argjson runs "$MAX_RUNS" '
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
