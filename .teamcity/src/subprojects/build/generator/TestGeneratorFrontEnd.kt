package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object TestGeneratorFrontEnd : BuildType({
    id("KtorGeneratorFrontendVerify")
    name = "Test generator frontend"
    params {
        password("env.SPACE_USERNAME", value = "%space.packages.apl.user%")
        password("env.SPACE_PASSWORD", value = "%space.packages.apl.token%")
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
        set -e
        
        export BRANCH_NAME="%teamcity.build.branch%"
        echo "Original branch name: ${'$'}BRANCH_NAME"
        
        TARGET_BRANCH="master"
        
        if [[ "${'$'}BRANCH_NAME" =~ pull/([0-9]+) ]]; then
            PR_NUMBER=${'$'}{BASH_REMATCH[1]}
            echo "Detected pull request #${'$'}PR_NUMBER"
            
            echo "Getting PR source branch..."
            PR_DATA=$(curl -s \
              -H "Authorization: Bearer %github.token.ktor.generator.website%" \
              -H "Accept: application/vnd.github.v3+json" \
              https://api.github.com/repos/ktorio/ktor-generator-website/pulls/${'$'}PR_NUMBER)
            
            PR_HEAD=$(echo "${'$'}PR_DATA" | grep -o '\"head\":{[^}]*}' | grep -o '\"ref\":\"[^\"]*\"' | cut -d'"' -f4)
            
            if [ -n "${'$'}PR_HEAD" ]; then
                echo "Using PR source branch: ${'$'}PR_HEAD"
                TARGET_BRANCH="${'$'}PR_HEAD"
            else
                if command -v jq &> /dev/null; then
                    PR_HEAD=$(echo "${'$'}PR_DATA" | jq -r '.head.ref // empty')
                    if [ -n "${'$'}PR_HEAD" ]; then
                        echo "Using PR source branch (jq): ${'$'}PR_HEAD"
                        TARGET_BRANCH="${'$'}PR_HEAD"
                    else
                        echo "WARNING: Could not determine PR source branch, falling back to default branch"
                    fi
                else
                    echo "WARNING: Could not determine PR source branch, falling back to default branch"
                fi
            fi
        else
            if [ -n "${'$'}BRANCH_NAME" ] && [ "${'$'}BRANCH_NAME" != "<default>" ]; then
                # Strip refs/heads/ prefix if present
                BRANCH_NAME=$(echo "${'$'}BRANCH_NAME" | sed 's|^refs/heads/||')
                TARGET_BRANCH="${'$'}BRANCH_NAME"
            fi
    
            echo "Using branch: ${'$'}TARGET_BRANCH"
        fi
        
        echo "DEBUG: PR Data (first 500 chars):"
        echo "${'$'}PR_DATA" | head -c 500
        
        BRANCH_CHECK=$(curl -s -o /dev/null -w "%{http_code}" \
          -H "Authorization: Bearer %github.token.ktor.generator.website%" \
          -H "Accept: application/vnd.github.v3+json" \
          https://api.github.com/repos/ktorio/ktor-generator-website/branches/${'$'}TARGET_BRANCH)
          
        if [ "${'$'}BRANCH_CHECK" != "200" ]; then
            echo "Branch ${'$'}TARGET_BRANCH does not exist. Falling back to master branch."
            TARGET_BRANCH="master"
        fi
        
        PAYLOAD=$(cat <<EOF
{
  "ref": "${'$'}TARGET_BRANCH",
  "inputs": {
    "registry_username": "%env.SPACE_USERNAME%",
    "registry_password": "%env.SPACE_PASSWORD%"
  }
}
EOF
)
        
        echo "Triggering workflow with payload:"
        echo "${'$'}PAYLOAD" | jq . 2>/dev/null || echo "${'$'}PAYLOAD"
        
        HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
        -H "Authorization: Bearer %github.token.ktor.generator.website%" \
        -H "Accept: application/vnd.github.v3+json" \
        -H "Content-Type: application/json" \
        https://api.github.com/repos/ktorio/ktor-generator-website/actions/workflows/playwright-tests.yml/dispatches \
        -d "${'$'}PAYLOAD")
        
        if [ "${'$'}HTTP_STATUS" = "204" ]; then
    echo "Successfully triggered GitHub Actions workflow on branch: ${'$'}TARGET_BRANCH"
else
    echo "Failed to trigger workflow. HTTP status: ${'$'}HTTP_STATUS"
    echo "Error details (without exposing sensitive headers):"
    ERROR_RESPONSE=$(curl -s -X POST \
    -H "Authorization: Bearer %github.token.ktor.generator.website%" \
    -H "Accept: application/vnd.github.v3+json" \
    -H "Content-Type: application/json" \
    https://api.github.com/repos/ktorio/ktor-generator-website/actions/workflows/playwright-tests.yml/dispatches \
    -d "${'$'}PAYLOAD")
    
    echo "Response body: ${'$'}ERROR_RESPONSE"
    echo "Check your GitHub token permissions and verify the repository and workflow exist."
    
    exit 1
fi
        
        echo "Waiting for workflow to start..."
        WORKFLOW_STARTED=false
        MAX_RETRIES=30
        RETRY_COUNT=0
        RUN_ID=""
        
        while [ ${'$'}RETRY_COUNT -lt ${'$'}MAX_RETRIES ] && [ "${'$'}WORKFLOW_STARTED" = false ]; do
            WORKFLOW_RUNS=$(curl -s \
            -H "Authorization: Bearer %github.token.ktor.generator.website%" \
            -H "Accept: application/vnd.github.v3+json" \
            "https://api.github.com/repos/ktorio/ktor-generator-website/actions/workflows/playwright-tests.yml/runs?branch=${'$'}TARGET_BRANCH&per_page=5")
            if command -v jq &> /dev/null; then
                TIMESTAMP=$(date +%s)
                # Look for runs that started in the last 5 minutes (300 seconds)
                RUN_ID=$(echo "${'$'}WORKFLOW_RUNS" | jq -r --arg ts "${'$'}TIMESTAMP" '.workflow_runs[] | select((.created_at | fromdateiso8601) > (${'$'}ts | tonumber) - 300) | .id' | head -n 1)
                if [ -n "${'$'}RUN_ID" ]; then
                    WORKFLOW_STARTED=true
                    echo "Found workflow run with ID: ${'$'}RUN_ID"
                else
                    echo "Waiting for workflow to start... (Attempt ${'$'}RETRY_COUNT/${'$'}MAX_RETRIES)"
                    RETRY_COUNT=$((RETRY_COUNT + 1))
                    sleep 10
                fi
            else
                echo "jq not available, cannot parse workflow response reliably. Waiting 60 seconds..."
                sleep 60
                WORKFLOW_STARTED=true  # We'll just assume it started after waiting
            fi
        done
        
        if [ "${'$'}WORKFLOW_STARTED" = false ]; then
            echo "GitHub Actions workflow did not start within the expected time."
            exit 1
        fi

        echo "Monitoring workflow execution (ID: ${'$'}RUN_ID)..."
        
        WORKFLOW_STATUS="in_progress"
        MAX_CHECKS=60  # Wait up to ~30 minutes (60 checks × 30 seconds)
        CHECK_COUNT=0
        
        while [ ${'$'}CHECK_COUNT -lt ${'$'}MAX_CHECKS ] && [ "${'$'}WORKFLOW_STATUS" = "in_progress" ] || [ "${'$'}WORKFLOW_STATUS" = "queued" ] || [ "${'$'}WORKFLOW_STATUS" = "waiting" ]; do
            WORKFLOW_DATA=$(curl -s \
            -H "Authorization: Bearer %github.token.ktor.generator.website%" \
            -H "Accept: application/vnd.github.v3+json" \
            "https://api.github.com/repos/ktorio/ktor-generator-website/actions/runs/${'$'}RUN_ID")
            
            if command -v jq &> /dev/null; then
                WORKFLOW_STATUS=$(echo "${'$'}WORKFLOW_DATA" | jq -r '.status')
                WORKFLOW_CONCLUSION=$(echo "${'$'}WORKFLOW_DATA" | jq -r '.conclusion')
                
                echo "Workflow status: ${'$'}WORKFLOW_STATUS, conclusion: ${'$'}WORKFLOW_CONCLUSION (Check ${'$'}CHECK_COUNT/${'$'}MAX_CHECKS)"
                
                if [ "${'$'}WORKFLOW_STATUS" = "completed" ]; then
                    break
                fi
            else
                echo "jq not available, checking raw response for completion status..."
                if echo "${'$'}WORKFLOW_DATA" | grep -q '"status":"completed"'; then
                    WORKFLOW_STATUS="completed"
                    echo "Workflow appears to be completed."
                    break
                fi
            fi
            CHECK_COUNT=$((CHECK_COUNT + 1))
            sleep 30
        done
        if [ "${'$'}WORKFLOW_STATUS" = "completed" ]; then
            if command -v jq &> /dev/null; then
                WORKFLOW_CONCLUSION=$(echo "${'$'}WORKFLOW_DATA" | jq -r '.conclusion')
                WORKFLOW_URL=$(echo "${'$'}WORKFLOW_DATA" | jq -r '.html_url')
            else
                if echo "${'$'}WORKFLOW_DATA" | grep -q '"conclusion":"success"'; then
                    WORKFLOW_CONCLUSION="success"
                else
                    WORKFLOW_CONCLUSION="failure"
                fi
                WORKFLOW_URL=$(echo "${'$'}WORKFLOW_DATA" | grep -o '"html_url":"[^"]*"' | cut -d'"' -f4)
            fi
            echo "Workflow completed with status: ${'$'}WORKFLOW_CONCLUSION"
            echo "Workflow URL: ${'$'}WORKFLOW_URL"
            
            if [ "${'$'}WORKFLOW_CONCLUSION" = "success" ]; then
                echo "GitHub Actions workflow completed successfully!"
                exit 0
            else
                echo "GitHub Actions workflow failed with status: ${'$'}WORKFLOW_CONCLUSION"
                exit 1
            fi
        else
            echo "GitHub Actions workflow did not complete within the expected time."
            echo "Current status: ${'$'}WORKFLOW_STATUS"
            exit 1
        fi
        """
        }
    }

    defaultBuildFeatures()

    triggers {
        onChangeDefaultOrPullRequest()
    }
})
