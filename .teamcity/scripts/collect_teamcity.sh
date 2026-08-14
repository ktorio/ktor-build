#!/bin/bash
set -euo pipefail

SCRIPTS_DIR="${FLAKY_SCRIPTS_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
source "$SCRIPTS_DIR/lib_flaky.sh"

WATCHED_BUILD_TYPE="${WATCHED_BUILD_TYPE:-Ktor_KtorCore_All}"

require_tc_token || exit 1

emit_json() {
  jq -Rn --arg rev "$1" --argjson attempts "$2" '
    [inputs
      | split("\t")
      | select(length == 3)
      | {target: .[0], targetDetail: .[1], name: .[2]}]
    | sort_by(.target, .name) as $flaky
    | {revision: $rev, attempts: $attempts, flaky: $flaky}' <<< "$3"
}

# 1. Most recent finished builds on the default branch, newest first.
BUILDS_JSON=$(teamcityApiRequest "/builds?locator=buildType:$WATCHED_BUILD_TYPE,branch:(default:true),state:finished,count:15&fields=build(id,revisions(revision(version)))")

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
ALL_RESULTS=$(
  for id in $ATTEMPT_IDS; do
    teamcityApiRequest "/testOccurrences?locator=build:(id:$id),count:10000&fields=testOccurrence(name,status,build(buildTypeId))" \
      | jq -r '.testOccurrence[]? | [.status, (.build.buildTypeId // ""), .name] | @tsv'
  done
)

# 5. Flaky = a test seen with BOTH SUCCESS and FAILURE across the attempts.
FLAKY_TSV=$(echo "$ALL_RESULTS" | awk -F '\t' '
  function classify(bt, name) {
    if (bt ~ /KtorMatrixNative_/)     { sub(/.*KtorMatrixNative_/, "", bt); return "native\t" bt }
    if (bt ~ /KtorMatrixCore_/)       { return "jvm\t" }
    if (bt ~ /KtorMatrixJavaScript_/) { return "js\t" }
    if (bt ~ /KtorMatrixWasmJs_/)     { return "wasmJs\t" }
    # Fallback: parse the KMP target from the test-name [suffix].
    if (name ~ /wasmJs/)              { return "wasmJs\t" }
    if (name ~ /\[js[,\]]/)           { return "js\t" }
    if (match(name, /mingwX64|linuxX64|linuxArm64|macosX64|macosArm64/))
                                      { return "native\t" substr(name, RSTART, RLENGTH) }
    if (name ~ /\[jvm\]/)             { return "jvm\t" }
    if (name ~ /\[Android\]/)         { return "jvm\t" }  # Android client-engine tests run on the JVM
    return "unknown\t"
  }
  {
    status = $1; bt = $2; name = $3
    if (name == "") next
    seen[name] = seen[name] " " status
    if (!(name in btid)) btid[name] = bt
  }
  END {
    for (n in seen)
      if (seen[n] ~ /FAILURE/ && seen[n] ~ /SUCCESS/)
        print classify(btid[n], n) "\t" n
  }' | sort -u)

FLAKY_COUNT=$(echo "$FLAKY_TSV" | grep -c . || true)
if [ "$FLAKY_COUNT" -eq 0 ]; then
  echo "No test changed result across the attempts — nothing flaky to report." >&2
fi

emit_json "$LATEST_REVISION" "$ATTEMPT_COUNT" "$FLAKY_TSV"
