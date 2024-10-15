#!/bin/bash
set -euo pipefail

function teamcityApiRequest() {
  local route=$1; shift
  curl --silent --fail-with-body \
    --user "%system.teamcity.auth.userId%:%system.teamcity.auth.password%" \
    "%teamcity.serverUrl%/app/rest$route" \
    --header "Content-Type: application/json" \
    --header "Accept: application/json" \
    "$@"
}

# Get running builds on the same branch
buildType="%system.teamcity.buildType.id%"
branch="%teamcity.build.branch%"
runningBuilds=$(teamcityApiRequest "/builds?locator=buildType:$buildType,branch:$branch,state:running" | jq -r '.build[].id')

# Cancel each running build except the current one
buildUrl=${BUILD_URL:-"{unknown}"}
currentBuildId="%teamcity.build.id%"

body='{"comment": "Superseded by: '$buildUrl'", "readdIntoQueue": false}'
for buildId in $runningBuilds; do
  if [ "$buildId" != "$currentBuildId" ]; then
    echo "Cancelling build '$buildId'..."
    teamcityApiRequest "/builds/$buildId" --request POST --data "$body" > /dev/null || echo "Failed"
  fi
done

echo "DONE."
