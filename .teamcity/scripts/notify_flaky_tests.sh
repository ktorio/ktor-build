#!/bin/bash
set -euo pipefail

WATCHED_BUILD_TYPE="KtorCore_All"
SUBTEAM_ID="%slack.ktor.team.subteam.id%"
BUILD_URL="%teamcity.serverUrl%/viewLog.html?buildId=%teamcity.build.id%"

function teamcityApiRequest() {
  local route=$1; shift
  curl --silent --fail-with-body \
    --user "%system.teamcity.auth.userId%:%system.teamcity.auth.password%" \
    "%teamcity.serverUrl%/app/rest$route" \
    --header "Accept: application/json" \
    "$@"
}

# 1. Most recent finished KtorCore_All builds on the default branch, newest first.
BUILDS_JSON=$(teamcityApiRequest "/builds?locator=buildType:$WATCHED_BUILD_TYPE,branch:(default:true),state:finished,count:15&fields=build(id,revisions(revision(version)))")

# 2. The revision of the latest run. All of its retry attempts share this revision.
LATEST_REVISION=$(echo "$BUILDS_JSON" | jq -r '.build[0].revisions.revision[0].version // empty')
if [ -z "$LATEST_REVISION" ]; then
  echo "No revision found for the latest $WATCHED_BUILD_TYPE build; nothing to check."
  exit 0
fi

# 3. Every finished build for that revision (i.e. the retry attempts).
ATTEMPT_IDS=$(echo "$BUILDS_JSON" | jq -r --arg rev "$LATEST_REVISION" \
  '.build[] | select(.revisions.revision[0].version == $rev) | .id')
ATTEMPT_COUNT=$(echo "$ATTEMPT_IDS" | grep -c . || true)

echo "Revision ${LATEST_REVISION:0:12} has $ATTEMPT_COUNT finished attempt(s) of $WATCHED_BUILD_TYPE."
if [ "$ATTEMPT_COUNT" -lt 2 ]; then
  echo "Fewer than two attempts — no retry happened, so there is nothing flaky to report."
  exit 0
fi

# 4. Collect every test's status across all attempts.
ALL_RESULTS=""
for id in $ATTEMPT_IDS; do
  TESTS=$(teamcityApiRequest "/testOccurrences?locator=build:(id:$id),count:10000&fields=testOccurrence(name,status)" \
    | jq -r '.testOccurrence[]? | "\(.status)\t\(.name)"')
  ALL_RESULTS="$ALL_RESULTS
$TESTS"
done

# 5. Flaky = a test seen with BOTH SUCCESS and FAILURE across the attempts.
FLAKY_TESTS=$(echo "$ALL_RESULTS" | awk -F '\t' '
  NF < 2 { next }
  { seen[$2] = seen[$2] " " $1 }
  END {
    for (t in seen)
      if (seen[t] ~ /FAILURE/ && seen[t] ~ /SUCCESS/) print t
  }' | sort -u)

FLAKY_COUNT=$(echo "$FLAKY_TESTS" | grep -c . || true)
if [ "$FLAKY_COUNT" -eq 0 ]; then
  echo "No test changed result across the attempts — nothing flaky to report."
  exit 0
fi

echo "Detected $FLAKY_COUNT flaky test(s):"
echo "$FLAKY_TESTS"

# 6. Post to Slack. The webhook URL arrives via the env.SLACK_WEBHOOK_URL password param.
SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:-}"
if [ -z "$SLACK_WEBHOOK_URL" ] || echo "$SLACK_WEBHOOK_URL" | grep -q '%.*%'; then
  echo "SLACK_WEBHOOK_URL is not configured; skipping Slack notification."
  exit 0
fi

# @-mention the @ktor-incident-responders user group when its Slack id is configured;
if [ -n "$SUBTEAM_ID" ] && ! echo "$SUBTEAM_ID" | grep -q '%.*%'; then
  MENTION="<!subteam^$SUBTEAM_ID|@ktor-incident-responders>"
else
  echo "slack.ktor.team.subteam.id user group is not set."
  MENTION="@ktor-incident-responders"
fi

# Keep the message readable when many tests flip.
TEST_LIST=$(echo "$FLAKY_TESTS" | head -15 | sed 's/^/• /')
if [ "$FLAKY_COUNT" -gt 15 ]; then
  TEST_LIST="$TEST_LIST
…and $((FLAKY_COUNT - 15)) more"
fi

MESSAGE=":warning: $MENTION — $FLAKY_COUNT flaky test(s) detected in *Build All Core* (result changed after retry) on revision \`${LATEST_REVISION:0:12}\`:
$TEST_LIST
<$BUILD_URL|View build>"

PAYLOAD=$(jq -n --arg text "$MESSAGE" '{text: $text, link_names: 1}')
if curl --silent --show-error -X POST -H 'Content-type: application/json' \
    --data "$PAYLOAD" "$SLACK_WEBHOOK_URL" > /dev/null; then
  echo "Slack notification sent."
else
  echo "Slack notification failed to send."
fi
