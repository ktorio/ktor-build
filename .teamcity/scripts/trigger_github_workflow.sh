#!/bin/bash

getBranchName() {
    local BRANCH_NAME="$1"
    local GITHUB_TOKEN="$2"
    local REPO="$3"
    local DEFAULT_BRANCH=""

    echo "Original branch name: $BRANCH_NAME"

    echo "Retrieving default branch for repository..."
    REPO_DATA="$(curl -s \
          -H "Authorization: token $GITHUB_TOKEN" \
          -H "Accept: application/vnd.github.v3+json" \
          https://api.github.com/repos/"$REPO")"

    echo "$REPO_DATA" > repo_data.json

    if command -v jq &> /dev/null; then
        DEFAULT_BRANCH=$(echo "$REPO_DATA" | jq -r '.default_branch // "master"')
    else
        echo "jq not found, using grep fallback for parsing JSON"
        DEFAULT_BRANCH=$(grep -o '"default_branch"\s*:\s*"[^"]*"' repo_data.json | grep -o '"[^"]*"$' | tr -d '"')
        if [ -z "$DEFAULT_BRANCH" ]; then
            echo "WARNING: Could not parse default branch from API response, using 'master' as fallback"
            DEFAULT_BRANCH="master"
        fi
    fi
    echo "Repository default branch: $DEFAULT_BRANCH"

    if [[ "$BRANCH_NAME" =~ pull/([0-9]+) ]]; then
        PR_NUMBER=${BASH_REMATCH[1]}
        echo "Detected pull request #$PR_NUMBER"

        echo "Getting PR source branch..."
        PR_DATA="$(curl -s \
          -H "Authorization: token $GITHUB_TOKEN" \
          -H "Accept: application/vnd.github.v3+json" \
          https://api.github.com/repos/"$REPO"/pulls/"$PR_NUMBER")"

        echo "$PR_DATA" > pr_data.json

        if command -v jq &> /dev/null; then
            PR_HEAD=$(echo "$PR_DATA" | jq -r '.head.ref // empty')
            if [ -n "$PR_HEAD" ]; then
                echo "Using PR source branch (jq): $PR_HEAD"
                TARGET_BRANCH="$PR_HEAD"
            else
                echo "WARNING: Could not determine PR source branch, falling back to default branch"
                TARGET_BRANCH="$DEFAULT_BRANCH"
            fi
        else
            PR_HEAD=$(grep -o '"head":\s*{[^}]*"ref":\s*"[^"]*"' pr_data.json | grep -o '"ref":\s*"[^"]*"' | grep -o '"[^"]*"$' | tr -d '"')

            if [ -n "$PR_HEAD" ]; then
                echo "Using PR source branch: $PR_HEAD"
                TARGET_BRANCH="$PR_HEAD"
            else
                echo "WARNING: Could not determine PR source branch, falling back to default branch"
                TARGET_BRANCH="$DEFAULT_BRANCH"
            fi
        fi

        rm -f pr_data.json
    else
        if [ -n "$BRANCH_NAME" ] && [ "$BRANCH_NAME" != "<default>" ]; then
            BRANCH_NAME="${BRANCH_NAME#refs/heads/}"
            TARGET_BRANCH="$BRANCH_NAME"
        else
            TARGET_BRANCH="$DEFAULT_BRANCH"
        fi

        echo "Using branch: $TARGET_BRANCH"
    fi

    rm -f repo_data.json

    if command -v jq &> /dev/null; then
        ENCODED_BRANCH=$(printf "%s" "$TARGET_BRANCH" | jq -s -R -r @uri)
    else
        ENCODED_BRANCH=$(printf "%s" "$TARGET_BRANCH" | perl -MURI::Escape -ne 'print uri_escape($_)' 2>/dev/null || echo "$TARGET_BRANCH")
    fi

    BRANCH_CHECK=$(curl -s -o /dev/null -w "%{http_code}" \
      -H "Authorization: token $GITHUB_TOKEN" \
      -H "Accept: application/vnd.github.v3+json" \
      "https://api.github.com/repos/$REPO/branches/$ENCODED_BRANCH")

    if [ "$BRANCH_CHECK" != "200" ]; then
        echo "Branch $TARGET_BRANCH does not exist. Falling back to default branch."
        TARGET_BRANCH="$DEFAULT_BRANCH"
    fi

    echo "$TARGET_BRANCH"
}

triggerWorkflow() {
    local REPO="$1"
    local WORKFLOW_FILE="$2"
    local TARGET_BRANCH="$3"
    local GITHUB_TOKEN="$4"

    echo "Triggering workflow on branch: $TARGET_BRANCH"

    REGISTRY_USERNAME="${REGISTRY_USERNAME:-}"
    REGISTRY_PASSWORD="${REGISTRY_PASSWORD:-}"

    PAYLOAD=$(cat <<EOF
{
  "ref": "$TARGET_BRANCH",
  "inputs": {
    "registry_username": "$REGISTRY_USERNAME",
    "registry_password": "$REGISTRY_PASSWORD"
  }
}
EOF
)

    HTTP_STATUS="$(curl -s -o response.txt -w "%{http_code}" -X POST \
    -H "Authorization: token $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    -H "Content-Type: application/json" \
    https://api.github.com/repos/"$REPO"/actions/workflows/"$WORKFLOW_FILE"/dispatches \
    -d "$PAYLOAD")"

    if [ "$HTTP_STATUS" != "204" ]; then
        echo "Failed to trigger workflow. HTTP status: $HTTP_STATUS"
                rm -f response.txt
                return 1
    fi

    rm -f response.txt
    echo "Successfully triggered GitHub Actions workflow on branch: $TARGET_BRANCH"
    return 0
}

findLatestWorkflowByName() {
    local REPO="$1"
    local TARGET_BRANCH="$2"
    local GITHUB_TOKEN="$3"
    local WORKFLOW_NAME="End-to-End Tests with Docker"
    local START_TIME
    START_TIME="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

    echo "Looking for the latest '$WORKFLOW_NAME' workflow run that started after $START_TIME..."

    WORKFLOW_ID=""
    MAX_ATTEMPTS=30
    ATTEMPT=0
    SLEEP_SECONDS=10

    while [ -z "$WORKFLOW_ID" ] && [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
        ATTEMPT=$((ATTEMPT+1))
        echo "Checking for workflow run (attempt $ATTEMPT/$MAX_ATTEMPTS)..."

        RUNS_URL="https://api.github.com/repos/$REPO/actions/runs?branch=$TARGET_BRANCH&per_page=10"

        WORKFLOWS=$(curl -s \
          -H "Authorization: token $GITHUB_TOKEN" \
          -H "Accept: application/vnd.github.v3+json" \
          "$RUNS_URL")

        echo "$WORKFLOWS" > workflows_data.json

        if command -v jq &> /dev/null; then
            WORKFLOW_ID=$(echo "$WORKFLOWS" | jq -r --arg time "$START_TIME" --arg name "$WORKFLOW_NAME" \
                '.workflow_runs[] | select(.created_at >= $time) | select(.name == $name) | .id' | head -n 1)
        else
            FOUND_WORKFLOWS=$(grep -o '{[^}]*"name"[^}]*"'"$WORKFLOW_NAME"'"[^}]*}' workflows_data.json)

            if [ -n "$FOUND_WORKFLOWS" ]; then
                echo "$FOUND_WORKFLOWS" > found_workflows.json
                WORKFLOW_CREATED=$(grep -o '"created_at"\s*:\s*"[^"]*"' found_workflows.json | head -n1 | grep -o '"[^"]*"$' | tr -d '"')

                if [[ "$WORKFLOW_CREATED" > "$START_TIME" ]]; then
                    WORKFLOW_ID=$(grep -o '"id"\s*:\s*[0-9]*' found_workflows.json | head -n1 | grep -o '[0-9]*$')
                fi
                rm -f found_workflows.json
            fi
        fi

        if [ -n "$WORKFLOW_ID" ]; then
            echo "Found workflow run with ID: $WORKFLOW_ID"
            rm -f workflows_data.json
            break
        fi

        rm -f workflows_data.json
        echo "Waiting $SLEEP_SECONDS seconds before checking again..."
        sleep $SLEEP_SECONDS
    done

    if [ -z "$WORKFLOW_ID" ]; then
        echo "Could not find a '$WORKFLOW_NAME' workflow run after $MAX_ATTEMPTS attempts."
        return 1
    fi

    echo "$WORKFLOW_ID"
}

monitorWorkflow() {
    local REPO="$1"
    local WORKFLOW_ID="$2"
    local GITHUB_TOKEN="$3"

    echo "Monitoring workflow run status (ID: $WORKFLOW_ID)..."

    MAX_MONITOR_ATTEMPTS=60
    MONITOR_ATTEMPT=0
    MONITOR_SLEEP_SECONDS=30

    while [ $MONITOR_ATTEMPT -lt $MAX_MONITOR_ATTEMPTS ]; do
        MONITOR_ATTEMPT=$((MONITOR_ATTEMPT+1))

        WORKFLOW_DATA=$(curl -s \
          -H "Authorization: token $GITHUB_TOKEN" \
          -H "Accept: application/vnd.github.v3+json" \
          "https://api.github.com/repos/$REPO/actions/runs/$WORKFLOW_ID")

        echo "$WORKFLOW_DATA" > workflow_status.json

        if command -v jq &> /dev/null; then
            WORKFLOW_STATUS=$(echo "$WORKFLOW_DATA" | jq -r '.status')
            WORKFLOW_CONCLUSION=$(echo "$WORKFLOW_DATA" | jq -r '.conclusion')
            WORKFLOW_URL=$(echo "$WORKFLOW_DATA" | jq -r '.html_url')
        else
            WORKFLOW_STATUS=$(grep -o '"status"\s*:\s*"[^"]*"' workflow_status.json | head -n 1 | grep -o '"[^"]*"$' | tr -d '"')
            WORKFLOW_CONCLUSION=$(grep -o '"conclusion"\s*:\s*"[^"]*"' workflow_status.json | head -n 1 | grep -o '"[^"]*"$' | tr -d '"' || echo "")
            WORKFLOW_URL=$(grep -o '"html_url"\s*:\s*"[^"]*"' workflow_status.json | head -n 1 | grep -o '"[^"]*"$' | tr -d '"')
        fi

        rm -f workflow_status.json

        echo "Workflow status: $WORKFLOW_STATUS, conclusion: $WORKFLOW_CONCLUSION (check $MONITOR_ATTEMPT/$MAX_MONITOR_ATTEMPTS)"
        echo "Workflow URL: $WORKFLOW_URL"

        if [ "$WORKFLOW_STATUS" = "completed" ]; then
            echo "Workflow run completed with conclusion: $WORKFLOW_CONCLUSION"

            if [ "$WORKFLOW_CONCLUSION" = "success" ]; then
                echo "Workflow succeeded!"
                return 0
            else
                echo "Workflow failed with conclusion: $WORKFLOW_CONCLUSION"
                return 1
            fi
        fi

        echo "Waiting $MONITOR_SLEEP_SECONDS seconds before checking again..."
        sleep $MONITOR_SLEEP_SECONDS
    done

    echo "Timed out waiting for workflow to complete after $((MAX_MONITOR_ATTEMPTS * MONITOR_SLEEP_SECONDS)) seconds."
    return 1
}

triggerAndMonitorWorkflow() {
    local REPO="$1"
    local WORKFLOW_FILE="$2"
    local BRANCH_NAME="$3"
    local GITHUB_TOKEN="$4"

    if [ -z "${REGISTRY_USERNAME}" ] || [ -z "${REGISTRY_PASSWORD}" ]; then
            echo "Registry credentials must be set as environment variables REGISTRY_USERNAME and REGISTRY_PASSWORD"
    fi

    TARGET_BRANCH=$(getBranchName "$BRANCH_NAME" "$GITHUB_TOKEN" "$REPO")

    if ! triggerWorkflow "$REPO" "$WORKFLOW_FILE" "$TARGET_BRANCH" "$GITHUB_TOKEN"
    then
        exit 1
    fi

    if ! WORKFLOW_ID=$(findLatestWorkflowByName "$REPO" "$TARGET_BRANCH" "$GITHUB_TOKEN")
    then
        exit 1
    fi

    if ! monitorWorkflow "$REPO" "$WORKFLOW_ID" "$GITHUB_TOKEN"
    then
        exit 1
    fi

    exit 0
}
