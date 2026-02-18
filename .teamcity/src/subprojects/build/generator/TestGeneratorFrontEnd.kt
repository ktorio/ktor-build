package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object TestGeneratorFrontEnd : BuildType({
    id("KtorGeneratorFrontendVerify")
    name = "Test generator frontend"

    params {
        password("github.token.ktor.generator.website", VcsToken.PROJECT_GENERATOR, display = ParameterDisplay.HIDDEN)
        password("github.actions.dispatch.pat", value = "%github.actions.dispatch.pat%", display = ParameterDisplay.HIDDEN)

        password("env.SPACE_USERNAME", value = "%space.packages.apl.user%")
        password("env.SPACE_PASSWORD", value = "%space.packages.apl.token%")

        password("env.GITHUB_TOKEN", value = "%github.actions.dispatch.pat%")
    }

    vcs {
        root(VCSKtorGeneratorWebsite)
        checkoutMode = CheckoutMode.ON_AGENT
        cleanCheckout = true
        showDependenciesChanges = true
    }

    steps {
        script {
            name = "Trigger GitHub Actions Workflow"
            scriptContent = """
                #!/bin/bash
                set -euo pipefail

                GITHUB_TOKEN="%env.GITHUB_TOKEN%"
                BRANCH_NAME="%teamcity.build.branch%"
                SPACE_USERNAME="%env.SPACE_USERNAME%"
                SPACE_PASSWORD="%env.SPACE_PASSWORD%"

                echo "Original branch name: ${'$'}BRANCH_NAME"

                fail_if_unresolved_or_empty() {
                  local name="${'$'}1"
                  local value="${'$'}2"
                  if [ -z "${'$'}value" ]; then
                    echo "ERROR: ${'$'}name is not set"
                    exit 1
                  fi
                  if [[ "${'$'}value" == *"%"* ]]; then
                    echo "ERROR: ${'$'}name was not resolved by TeamCity (still contains '%')"
                    exit 1
                  fi
                }

                fail_if_unresolved_or_empty "GITHUB_TOKEN" "${'$'}GITHUB_TOKEN"
                fail_if_unresolved_or_empty "SPACE_USERNAME" "${'$'}SPACE_USERNAME"
                fail_if_unresolved_or_empty "SPACE_PASSWORD" "${'$'}SPACE_PASSWORD"

                # Default branch fallback
                TARGET_BRANCH="master"

                # Determine target ref: PR source branch or current branch
                if [[ "${'$'}BRANCH_NAME" =~ pull/([0-9]+) ]]; then
                  PR_NUMBER="${'$'}{BASH_REMATCH[1]}"
                  echo "Getting PR source branch..."

                  PR_DATA=$(curl -sS \
                    -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                    -H "Accept: application/vnd.github+json" \
                    "https://api.github.com/repos/ktorio/ktor-generator-website/pulls/${'$'}PR_NUMBER")

                  if command -v jq &>/dev/null; then
                    PR_HEAD=$(echo "${'$'}PR_DATA" | jq -r '.head.ref // empty')
                  else
                    PR_HEAD=$(echo "${'$'}PR_DATA" | grep -o '\"ref\":\"[^\"]*\"' | head -n 1 | cut -d'"' -f4 || true)
                  fi

                  if [ -n "${'$'}PR_HEAD" ]; then
                    echo "Using PR source branch: ${'$'}PR_HEAD"
                    TARGET_BRANCH="${'$'}PR_HEAD"
                  else
                    echo "WARNING: Could not determine PR source branch, falling back to ${'$'}TARGET_BRANCH"
                  fi
                else
                  if [ -n "${'$'}BRANCH_NAME" ] && [ "${'$'}BRANCH_NAME" != "<default>" ]; then
                    BRANCH_NAME=$(echo "${'$'}BRANCH_NAME" | sed 's|^refs/heads/||')
                    TARGET_BRANCH="${'$'}BRANCH_NAME"
                  fi
                  echo "Using branch: ${'$'}TARGET_BRANCH"
                fi

                # Verify branch exists (treat non-200 as "can't use this ref")
                BRANCH_CHECK_CODE=$(curl -sS -o /dev/null -w "%{http_code}" \
                  -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                  -H "Accept: application/vnd.github+json" \
                  "https://api.github.com/repos/ktorio/ktor-generator-website/branches/${'$'}TARGET_BRANCH" || true)

                if [ "${'$'}BRANCH_CHECK_CODE" != "200" ]; then
                  echo "Branch check for '${'$'}TARGET_BRANCH' returned HTTP ${'$'}BRANCH_CHECK_CODE. Falling back to 'master'."
                  TARGET_BRANCH="master"
                fi

                # Build payload (inputs are required by workflow_dispatch)
                PAYLOAD=$(cat <<EOF
                {
                  "ref": "${'$'}TARGET_BRANCH",
                  "inputs": {
                    "registry_username": "${'$'}SPACE_USERNAME",
                    "registry_password": "${'$'}SPACE_PASSWORD"
                  }
                }
                EOF
                )

                echo "Triggering workflow with payload (inputs redacted):"
                echo "${'$'}PAYLOAD" | sed 's/"registry_password":[^,}]*/"registry_password":"<REDACTED>"/' | (jq . 2>/dev/null || cat)

                # Trigger the workflow (capture response body once)
                RESPONSE_FILE=$(mktemp)
                HTTP_STATUS=$(curl -sS -o "${'$'}RESPONSE_FILE" -w "%{http_code}" -X POST \
                  -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                  -H "Accept: application/vnd.github+json" \
                  -H "Content-Type: application/json" \
                  "https://api.github.com/repos/ktorio/ktor-generator-website/actions/workflows/playwright-tests.yml/dispatches" \
                  -d "${'$'}PAYLOAD" || true)

                if [ "${'$'}HTTP_STATUS" != "204" ] && [ "${'$'}HTTP_STATUS" != "202" ]; then
                  echo "Failed to trigger workflow. HTTP status: ${'$'}HTTP_STATUS"
                  echo "Response body:"
                  cat "${'$'}RESPONSE_FILE"
                  echo
                  exit 1
                fi

                echo "Successfully triggered GitHub Actions workflow on branch: ${'$'}TARGET_BRANCH (HTTP ${'$'}HTTP_STATUS)"

                echo "Waiting for workflow to start..."
                WORKFLOW_STARTED=false
                MAX_RETRIES=30
                RETRY_COUNT=0
                RUN_ID=""

                while [ "${'$'}RETRY_COUNT" -lt "${'$'}MAX_RETRIES" ] && [ "${'$'}WORKFLOW_STARTED" = false ]; do
                  WORKFLOW_RUNS=$(curl -sS \
                    -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                    -H "Accept: application/vnd.github+json" \
                    "https://api.github.com/repos/ktorio/ktor-generator-website/actions/workflows/playwright-tests.yml/runs?branch=${'$'}TARGET_BRANCH&per_page=5")

                  if command -v jq &>/dev/null; then
                    TIMESTAMP=$(date +%s)
                    RUN_ID=$(echo "${'$'}WORKFLOW_RUNS" | jq -r --arg ts "${'$'}TIMESTAMP" '.workflow_runs[] | select((.created_at | fromdateiso8601) > (${'$'}ts | tonumber) - 300) | .id' | head -n 1)
                    if [ -n "${'$'}RUN_ID" ] && [ "${'$'}RUN_ID" != "null" ]; then
                      WORKFLOW_STARTED=true
                      echo "Found workflow run with ID: ${'$'}RUN_ID"
                      break
                    fi
                  else
                    echo "jq not available; cannot reliably detect run id. Waiting 60 seconds and continuing."
                    sleep 60
                    WORKFLOW_STARTED=true
                    break
                  fi

                  echo "Waiting for workflow to start... (Attempt ${'$'}((RETRY_COUNT + 1))/${'$'}MAX_RETRIES)"
                  RETRY_COUNT=$((RETRY_COUNT + 1))
                  sleep 10
                done

                if [ "${'$'}WORKFLOW_STARTED" = false ]; then
                  echo "GitHub Actions workflow did not start within the expected time."
                  exit 1
                fi

                if [ -z "${'$'}RUN_ID" ] || [ "${'$'}RUN_ID" = "null" ]; then
                  echo "Workflow was dispatched, but run id couldn't be determined. Exiting successfully."
                  exit 0
                fi

                echo "Monitoring workflow execution (ID: ${'$'}RUN_ID)..."

                WORKFLOW_STATUS="queued"
                MAX_CHECKS=60
                CHECK_COUNT=0
                WORKFLOW_DATA=""

                while [ "${'$'}CHECK_COUNT" -lt "${'$'}MAX_CHECKS" ]; do
                  WORKFLOW_DATA=$(curl -sS \
                    -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                    -H "Accept: application/vnd.github+json" \
                    "https://api.github.com/repos/ktorio/ktor-generator-website/actions/runs/${'$'}RUN_ID")

                  if command -v jq &>/dev/null; then
                    WORKFLOW_STATUS=$(echo "${'$'}WORKFLOW_DATA" | jq -r '.status // empty')
                    WORKFLOW_CONCLUSION=$(echo "${'$'}WORKFLOW_DATA" | jq -r '.conclusion // empty')
                    echo "Workflow status: ${'$'}WORKFLOW_STATUS, conclusion: ${'$'}WORKFLOW_CONCLUSION (Check ${'$'}((CHECK_COUNT + 1))/${'$'}MAX_CHECKS)"
                    if [ "${'$'}WORKFLOW_STATUS" = "completed" ]; then
                      break
                    fi
                  else
                    if echo "${'$'}WORKFLOW_DATA" | grep -q '"status":"completed"'; then
                      WORKFLOW_STATUS="completed"
                      echo "Workflow appears to be completed."
                      break
                    fi
                  fi

                  CHECK_COUNT=$((CHECK_COUNT + 1))
                  sleep 30
                done

                if [ "${'$'}WORKFLOW_STATUS" != "completed" ]; then
                  echo "GitHub Actions workflow did not complete within the expected time."
                  echo "Current status: ${'$'}WORKFLOW_STATUS"
                  exit 1
                fi

                if command -v jq &>/dev/null; then
                  WORKFLOW_CONCLUSION=$(echo "${'$'}WORKFLOW_DATA" | jq -r '.conclusion // empty')
                  WORKFLOW_URL=$(echo "${'$'}WORKFLOW_DATA" | jq -r '.html_url // empty')
                else
                  WORKFLOW_CONCLUSION=$(echo "${'$'}WORKFLOW_DATA" | grep -o '"conclusion":"[^"]*"' | head -n 1 | cut -d'"' -f4 || true)
                  WORKFLOW_URL=$(echo "${'$'}WORKFLOW_DATA" | grep -o '"html_url":"[^"]*"' | head -n 1 | cut -d'"' -f4 || true)
                fi

                echo "Workflow completed with conclusion: ${'$'}WORKFLOW_CONCLUSION"
                echo "Workflow URL: ${'$'}WORKFLOW_URL"

                if [ "${'$'}WORKFLOW_CONCLUSION" = "success" ]; then
                  echo "GitHub Actions workflow completed successfully!"
                  exit 0
                fi

                echo "GitHub Actions workflow failed (conclusion: ${'$'}WORKFLOW_CONCLUSION)"
                exit 1
            """.trimIndent()
        }
    }

    defaultBuildFeatures()

    triggers {
        onChangeDefaultOrPullRequest()
    }
})
