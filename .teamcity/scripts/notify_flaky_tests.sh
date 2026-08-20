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
export QUARANTINE_BUILD_TYPES="%quarantine.build.type%"
TC_BUILD_ID="%teamcity.build.id%"
SUBTEAM_ID="%slack.ktor.team.subteam.id%"
NOTIFIER_BUILD_TYPE="%notifier.build.type%"
KTOR_REPO_URL="%ktor.repo.url%"
STATE_FILE="flaky-notify-state.json"

if [ -z "$KTOR_REPO_URL" ] || printf '%s' "$KTOR_REPO_URL" | grep -q '%.*%'; then
  KTOR_REPO_URL="https://github.com/ktorio/ktor"
fi
KTOR_REPO_URL="${KTOR_REPO_URL%/}"
# owner/name slug (e.g. ktorio/ktor) for GitHub code-search queries.
KTOR_REPO_SLUG=$(printf '%s' "$KTOR_REPO_URL" | sed -E 's#^https?://[^/]+/##')
# Exported so merge_report.sh can turn classes/revision into GitHub links in the HTML report tab.
export KTOR_REPO_URL KTOR_REPO_SLUG

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

# Signature of the last message posted, read from the previous notifier build's state artifact
# via REST. The signature has two independent components — `tr` (this-run retry-diff finding) and
# `qr` (quarantine decision) — so each is de-duplicated on its own.
previous_posted_sig() {
  if [ -z "$NOTIFIER_BUILD_TYPE" ] || printf '%s' "$NOTIFIER_BUILD_TYPE" | grep -q '%.*%'; then
    return 0
  fi
  local prev_id state
  prev_id=$(teamcityApiRequest \
    "/builds?locator=buildType:$NOTIFIER_BUILD_TYPE,branch:(default:true),state:finished,count:1&fields=build(id)" \
    2>/dev/null | jq -r '.build[0].id // empty') || return 0
  [ -n "$prev_id" ] || return 0
  state=$(teamcityApiRequest "/builds/id:$prev_id/artifacts/content/$STATE_FILE" 2>/dev/null) || return 0
  printf '%s' "$state" | jq -cS '.sig // empty' 2>/dev/null || true
}

# Persist the two signature components (each a compact JSON value, or empty -> null) for the next run.
write_state() {
  jq -cn --argjson tr "${1:-null}" --argjson qr "${2:-null}" '{sig: {tr: $tr, qr: $qr}}' > "$STATE_FILE" 2>/dev/null \
    || printf '{"sig":{"tr":null,"qr":null}}\n' > "$STATE_FILE"
  echo "Wrote $STATE_FILE." >&2
}

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

CUR_TR=$(echo "$CONSOLIDATED" | jq -cS --argjson runCount "$THIS_RUN_COUNT" '
  if $runCount > 0
  then {revision: .revision, tests: (.thisRun | map(.target + "|" + (.targetDetail // "") + "|" + .name) | sort)}
  else null end')
if [ -n "$QUARANTINE_LINE" ]; then
  CUR_QR=$(echo "$CONSOLIDATED" | jq -cS '
    .quarantine.tests // []
    | map(select(.verdict == "always-failing" or .verdict == "stable-candidate"))
    | map(.verdict + "|" + .target + "|" + .name) | sort')
else
  CUR_QR=null
fi

PREV_SIG=$(previous_posted_sig)
PREV_TR=null
PREV_QR=null
if [ -n "$PREV_SIG" ]; then
  PREV_TR=$(printf '%s' "$PREV_SIG" | jq -cS '.tr // null')
  PREV_QR=$(printf '%s' "$PREV_SIG" | jq -cS '.qr // null')
fi

# Post a component only when it is present this run AND differs from previously posted.
POST_TR=no
if [ "$THIS_RUN_COUNT" -gt 0 ] && [ "$CUR_TR" != "$PREV_TR" ]; then POST_TR=yes; fi
POST_QR=no
if [ -n "$QUARANTINE_LINE" ] && [ "$CUR_QR" != "$PREV_QR" ]; then POST_QR=yes; fi

# What to store next: advance a component only where it is present this run, otherwise carry the
# previous value forward so a later unchanged run still de-duplicates against it.
NEW_TR="$PREV_TR"
if [ "$THIS_RUN_COUNT" -gt 0 ]; then NEW_TR="$CUR_TR"; fi
NEW_QR="$PREV_QR"
if [ -n "$QUARANTINE_LINE" ]; then NEW_QR="$CUR_QR"; fi

# Nothing new in either component: stay silent, but persist the (carried-forward) signature so the
# chain of state artifacts stays continuous. The report artifacts are published regardless.
if [ "$POST_TR" = "no" ] && [ "$POST_QR" = "no" ]; then
  echo "No new flaky findings since the last Slack post; skipping notification." >&2
  write_state "$NEW_TR" "$NEW_QR"
  exit 0
fi

echo "$CONSOLIDATED" | jq -r '
  .thisRun | sort_by(.target, .name)[]
  | "  [" + .target + (if (.targetDetail // "") != "" then ":" + .targetDetail else "" end) + "] " + .name
    + (if .chronic then "  (chronic 28d)" else "" end)'

# --- Notify: Slack (Block Kit dashboard) ---
LATEST_REVISION=$(echo "$CONSOLIDATED" | jq -r '.revision // ""')
SHORT_REV="${LATEST_REVISION:0:12}"
# Number of this run's flaky tests whose class is chronic (on the Develocity 28d list) — counted per
# test, so "N / M this run" reads as N of the M tests that flaked this run are chronic.
CHRONIC_TEST_COUNT=$(echo "$CONSOLIDATED" | jq -r '[.thisRun[] | select(.chronic)] | length')

# @-mention the @ktor-incident-responders user group when its Slack id is configured.
if [ -n "$SUBTEAM_ID" ] && ! echo "$SUBTEAM_ID" | grep -q '%.*%'; then
  MENTION="<!subteam^$SUBTEAM_ID|@ktor-incident-responders>"
else
  echo "slack.ktor.team.subteam.id user group is not set." >&2
  MENTION="@ktor-incident-responders"
fi

# Link targets for the action buttons.
BUILD_URL="$TC_SERVER_URL/viewLog.html?buildId=$TC_BUILD_ID"
COMMIT_URL=""
[ -n "$LATEST_REVISION" ] && COMMIT_URL="$KTOR_REPO_URL/commit/$LATEST_REVISION"
# The report is the flaky-report.html artifact of this notifier build; link straight to it when the
# notifier build type is known, otherwise fall back to the build page.
if [ -n "$NOTIFIER_BUILD_TYPE" ] && ! printf '%s' "$NOTIFIER_BUILD_TYPE" | grep -q '%.*%'; then
  REPORT_URL="$TC_SERVER_URL/repository/download/$NOTIFIER_BUILD_TYPE/$TC_BUILD_ID:id/flaky-report.html"
else
  REPORT_URL="$BUILD_URL"
fi

# Quarantine summary.
QUARANTINE_TEXT=""
if [ -n "$QUARANTINE_LINE" ]; then
  QUARANTINE_TEXT="still flaky: $QR_STILL_FLAKY · always failing: $QR_ALWAYS_FAILING (fix or @Ignore) · ready to un-quarantine: $QR_STABLE"
fi

# Plain-text fallback shown in notifications / by clients that don't render blocks.
if [ "$THIS_RUN_COUNT" -gt 0 ]; then
  FALLBACK="$THIS_RUN_COUNT flaky test(s) in Build All Core on ${SHORT_REV:-unknown}"
else
  FALLBACK="Quarantined tests need a decision in Build All Core"
fi

# Build the Block Kit payload with jq so all text (test names, links, quarantine) is safely escaped.
PAYLOAD=$(jq -n \
  --argjson c "$CONSOLIDATED" \
  --arg repo "$KTOR_REPO_URL" --arg slug "$KTOR_REPO_SLUG" \
  --arg mention "$MENTION" --arg fallback "$FALLBACK" \
  --arg buildUrl "$BUILD_URL" --arg reportUrl "$REPORT_URL" --arg commitUrl "$COMMIT_URL" \
  --arg shortRev "$SHORT_REV" --arg quarantine "$QUARANTINE_TEXT" \
  --argjson thisRun "$THIS_RUN_COUNT" --argjson chronicTests "$CHRONIC_TEST_COUNT" '
  def simpleClass: ((.class // .name) | split(".") | last);
  def ghSearch: $repo + "/search?type=code&q=" + ("repo:" + $slug + " " + simpleClass | @uri);

  ($c.dv.available) as $dv
  | ($c.byTargetCounts | to_entries | map(.key + " " + (.value|tostring)) | join(" · ")) as $targets
  | (if $commitUrl != "" then "<" + $commitUrl + "|`" + $shortRev + "`>" else "`unknown`" end) as $revRef
  | ( $c.thisRun | sort_by(.target, .name) | .[0:15]
      | map("• `" + .target + (if (.targetDetail // "") != "" then ":" + .targetDetail else "" end) + "`  "
            + "<" + ghSearch + "|" + .name + ">" + (if .chronic then "  ⚠︎" else "" end))
      | join("\n") ) as $lines
  | (if ($c.thisRun|length) > 15 then $lines + "\n…and " + (($c.thisRun|length) - 15 | tostring) + " more" else $lines end) as $testBody
  | ( $c.chronic[0:3] | map("<" + ghSearch + "|" + simpleClass + "> (" + (.flaky|tostring) + ")") | join(" · ") ) as $chronicTop
  | ( [ {type:"button", text:{type:"plain_text", text:"View build"}, url:$buildUrl},
        {type:"button", text:{type:"plain_text", text:"Flaky report"}, url:$reportUrl} ]
      + (if $commitUrl != "" then [ {type:"button", text:{type:"plain_text", text:"Commit"}, url:$commitUrl} ] else [] end)
    ) as $buttons
  | {
      text: $fallback,
      blocks: (
        [ { type:"header", text:{ type:"plain_text", emoji:true,
              text:(if $thisRun > 0 then "⚠️ Flaky tests — Build All Core"
                    else "⚠️ Quarantine needs a decision — Build All Core" end) } },
          { type:"section", text:{ type:"mrkdwn", text:$mention } } ]
        + (if $thisRun > 0
             then [ { type:"section", fields: (
                       [ { type:"mrkdwn", text:("*Revision*\n" + $revRef) },
                         { type:"mrkdwn", text:("*Targets*\n" + (if $targets == "" then "—" else $targets end)) },
                         { type:"mrkdwn", text:("*Retry attempts*\n" + ($c.attempts|tostring)) } ]
                       + (if $dv then [ { type:"mrkdwn", text:("*Chronic overlap*\n" + ($chronicTests|tostring) + " / " + ($thisRun|tostring) + " this run") } ] else [] end)
                     ) } ]
             else [ { type:"section", fields:[
                       { type:"mrkdwn", text:("*Revision*\n" + $revRef) },
                       { type:"mrkdwn", text:("*Retry attempts*\n" + ($c.attempts|tostring)) } ] } ]
           end)
        + (if $thisRun > 0
             then [ { type:"divider" },
                    { type:"section", text:{ type:"mrkdwn", text:("*Flaky this run*\n" + $testBody) } } ]
             else [] end)
        + (if $quarantine != ""
             then [ { type:"section", text:{ type:"mrkdwn", text:("*Quarantine*\n" + $quarantine) } } ]
             else [] end)
        + (if ($dv and $chronicTop != "")
             then [ { type:"context", elements:[ { type:"mrkdwn", text:("Chronic 28d (flaky runs): " + $chronicTop) } ] } ]
             else [] end)
        + [ { type:"divider" }, { type:"actions", elements:$buttons } ]
      )
    }')

# Advance the stored signature only when the post actually goes out; on failure keep the previous
# values so the next run retries this same finding instead of treating it as already delivered.
if post_slack_payload "$PAYLOAD"; then
  write_state "$NEW_TR" "$NEW_QR"
else
  write_state "$PREV_TR" "$PREV_QR"
  echo "Slack post failed; signature left unchanged so the next run retries." >&2
  exit 1
fi
