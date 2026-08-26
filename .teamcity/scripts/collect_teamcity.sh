#!/bin/bash
set -euo pipefail

SCRIPTS_DIR="${FLAKY_SCRIPTS_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
source "$SCRIPTS_DIR/lib_flaky.sh"

WATCHED_BUILD_TYPE="${WATCHED_BUILD_TYPE:-Ktor_KtorCore_All}"
OCC_CAP="${TC_TEST_OCCURRENCE_CAP:-10000}"

require_tc_token || exit 1

emit_json() {
  jq -Rn --arg rev "$1" --argjson attempts "$2" '
    [inputs
      | split("\t")
      | select(length == 4)
      | {target: .[0], targetDetail: .[1], name: .[2],
         failBuildId: (if .[3] == "" then null else .[3] end)}]
    | sort_by(.target, .name) as $flaky
    | {revision: $rev, attempts: $attempts, flaky: $flaky}' <<< "$3"
}

# 1. Most recent finished builds on the default branch, newest first.
#    A failure here must not abort the notifier: report nothing flaky and let the
#    other sources (Develocity, quarantine) and the report still be produced.
if ! BUILDS_JSON=$(teamcityApiRequest "/builds?locator=buildType:$WATCHED_BUILD_TYPE,branch:(default:true),state:finished,count:15&fields=build(id,revisions(revision(version)))"); then
  echo "Cannot list recent $WATCHED_BUILD_TYPE builds; reporting nothing flaky for this run." >&2
  emit_json "" 0 ""
  exit 0
fi

# 2. The revision of the latest run. All of its retry attempts share this revision.
LATEST_REVISION=$(echo "$BUILDS_JSON" | jq -r '.build[0].revisions.revision[0].version // empty')
if [ -z "$LATEST_REVISION" ]; then
  echo "No revision found for the latest $WATCHED_BUILD_TYPE build; nothing to check." >&2
  emit_json "" 0 ""
  exit 0
fi

# 3. Every finished build for that revision (i.e. the retry attempts).
ATTEMPT_IDS=$(echo "$BUILDS_JSON" | jq -r --arg rev "$LATEST_REVISION" \
  '.build[] | select(.revisions.revision[0].version == $rev) | .id')
ATTEMPT_COUNT=$(echo "$ATTEMPT_IDS" | grep -c . || true)

echo "Revision ${LATEST_REVISION:0:12} has $ATTEMPT_COUNT finished attempt(s) of $WATCHED_BUILD_TYPE." >&2
if [ "$ATTEMPT_COUNT" -lt 2 ]; then
  echo "Fewer than two attempts — no retry happened, so there is nothing flaky to report." >&2
  emit_json "$LATEST_REVISION" "$ATTEMPT_COUNT" ""
  exit 0
fi

# 4. Collect every test's status + owning sub-build across all attempts.
#    A transient failure on one attempt must not abort the notifier: skip that attempt
#    (a missing attempt can only hide a flip, never invent one) and diff the rest.
ALL_RESULTS_FILE=$(mktemp)
trap 'rm -f "$ALL_RESULTS_FILE"' EXIT
ATTEMPTS_READ=0
for id in $ATTEMPT_IDS; do
  if ! occurrences=$(teamcityApiRequest "/testOccurrences?locator=build:(id:$id),ignored:false,count:$OCC_CAP&fields=testOccurrence(name,status,build(buildTypeId))"); then
    echo "Skipping attempt $id: testOccurrences request failed." >&2
    continue
  fi
  warn_if_truncated "$occurrences" "$OCC_CAP" "attempt $id"
  if ! printf '%s' "$occurrences" \
      | jq -r --arg aid "$id" '.testOccurrence[]? | [.status, (.build.buildTypeId // ""), .name, $aid] | @tsv' >> "$ALL_RESULTS_FILE"; then
    echo "Skipping attempt $id: could not parse test occurrences." >&2
    continue
  fi
  ATTEMPTS_READ=$((ATTEMPTS_READ + 1))
done
ALL_RESULTS=$(cat "$ALL_RESULTS_FILE")

# Diffing needs at least two attempts actually read; otherwise we can't observe a flip.
if [ "$ATTEMPTS_READ" -lt 2 ]; then
  echo "Only $ATTEMPTS_READ of $ATTEMPT_COUNT attempt(s) could be read; not enough to diff — reporting nothing flaky." >&2
  emit_json "$LATEST_REVISION" "$ATTEMPT_COUNT" ""
  exit 0
fi

# 5. Flaky = a test seen with BOTH SUCCESS and FAILURE across the attempts.
FLAKY_TSV=$(echo "$ALL_RESULTS" | awk -F '\t' "$FLAKY_AWK_CLASSIFY"'
  function classify(bt, name) {
    if (bt ~ /KtorMatrixNative_/)     { sub(/.*KtorMatrixNative_/, "", bt); return "native\t" bt }
    if (bt ~ /KtorMatrixCore_/)       { return "jvm\t" }
    if (bt ~ /KtorMatrixJavaScript_/) { return "js\t" }
    if (bt ~ /KtorMatrixWasmJs_/)     { return "wasmJs\t" }
    # No matrix target on the buildTypeId; fall back to the shared name-suffix classifier.
    return flaky_classify_by_name(name, "unknown")
  }
  {
    status = $1; bt = $2; name = $3; aid = $4
    if (name == "") next
    seen[name] = seen[name] " " status
    if (!(name in btid)) btid[name] = bt
    # Remember one attempt (composite build) where this test FAILED, for a deep link to that build.
    if (status ~ /FAILURE/ && !(name in failb)) failb[name] = aid
  }
  END {
    for (n in seen)
      if (seen[n] ~ /FAILURE/ && seen[n] ~ /SUCCESS/)
        print classify(btid[n], n) "\t" n "\t" failb[n]
  }' | sort -u)

FLAKY_COUNT=$(echo "$FLAKY_TSV" | grep -c . || true)
if [ "$FLAKY_COUNT" -eq 0 ]; then
  echo "No test changed result across the attempts — nothing flaky to report." >&2
fi

emit_json "$LATEST_REVISION" "$ATTEMPT_COUNT" "$FLAKY_TSV"
