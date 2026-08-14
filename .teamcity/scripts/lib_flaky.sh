#!/bin/bash
# Shared helpers for the flaky-test report scripts.
#
# This file is SOURCED by collect_*.sh and notify_flaky_tests.sh — it is never the
# TeamCity script step itself, so TeamCity does NOT substitute %...% references in
# here. All TeamCity values must arrive via environment variables that the
# orchestrator (the actual script step) resolves and exports.

# Verify a TeamCity REST token is present and is not an unexpanded TC placeholder.
require_tc_token() {
  TC_REST_TOKEN="${TC_REST_TOKEN:-}"
  if [ -z "$TC_REST_TOKEN" ] || printf '%s' "$TC_REST_TOKEN" | grep -q '%.*%'; then
    echo "TC_REST_TOKEN is not configured; cannot query the TeamCity REST API." >&2
    echo "Provide a TeamCity access token (View rights on the Ktor Core project) via the env.TC_REST_TOKEN secure parameter." >&2
    return 1
  fi
}

# GET a TeamCity REST route and echo the JSON body.
# Usage: teamcityApiRequest "/route" [extra curl args...]
teamcityApiRequest() {
  local route=$1; shift
  local out status
  out=$(curl --silent --show-error --write-out $'\n%{http_code}' \
    --header "Authorization: Bearer $TC_REST_TOKEN" \
    "${TC_SERVER_URL:-https://ktor.teamcity.com}/app/rest$route" \
    --header "Accept: application/json" \
    "$@")
  status=${out##*$'\n'}
  if [ "$status" -ge 400 ]; then
    echo "TeamCity REST $route -> HTTP $status" >&2
    echo "${out%$'\n'*}" >&2
    return 1
  fi
  echo "${out%$'\n'*}"
}

# Verify a Develocity access key is present and is not an unexpanded TC placeholder.
require_dv_key() {
  DV_ACCESS_KEY="${DV_ACCESS_KEY:-}"
  if [ -z "$DV_ACCESS_KEY" ] || printf '%s' "$DV_ACCESS_KEY" | grep -q '%.*%'; then
    echo "DV_ACCESS_KEY is not configured; skipping the Develocity source." >&2
    echo "Provide a Develocity access key (build-data read) via the env.DV_ACCESS_KEY secure parameter." >&2
    return 1
  fi
}

# GET a Develocity route and echo the JSON body.
# Usage: develocityApiRequest "/path?query"
develocityApiRequest() {
  local route=$1; shift
  local out status
  out=$(curl --silent --show-error --write-out $'\n%{http_code}' \
    --header "Authorization: Bearer $DV_ACCESS_KEY" \
    "${DV_SERVER_URL:-https://ge.jetbrains.com}$route" \
    --header "Accept: application/json" \
    "$@")
  status=${out##*$'\n'}
  if [ "$status" -ge 400 ]; then
    echo "Develocity $route -> HTTP $status" >&2
    echo "${out%$'\n'*}" >&2
    return 1
  fi
  echo "${out%$'\n'*}"
}

# Post a plain-text message to Slack via the incoming webhook in SLACK_WEBHOOK_URL.
# Usage: post_to_slack "message text"
post_to_slack() {
  local message=$1
  local url="${SLACK_WEBHOOK_URL:-}"
  if [ -z "$url" ] || printf '%s' "$url" | grep -q '%.*%'; then
    echo "SLACK_WEBHOOK_URL is not configured; skipping Slack notification." >&2
    return 0
  fi
  local payload
  payload=$(jq -n --arg text "$message" '{text: $text, link_names: 1}')
  if curl --silent --show-error -X POST -H 'Content-type: application/json' \
      --data "$payload" "$url" > /dev/null; then
    echo "Slack notification sent."
  else
    echo "Slack notification failed to send." >&2
    return 1
  fi
}
