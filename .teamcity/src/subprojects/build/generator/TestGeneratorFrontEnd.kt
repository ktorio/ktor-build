package subprojects.build.generator

import dsl.addSlackNotifications
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object TestGeneratorFrontEnd : BuildType({
    id("KtorGeneratorFrontendVerify")
    name = "Test generator frontend"

    params {
        password("github.token.ktor.generator.website", VcsToken.PROJECT_GENERATOR, display = ParameterDisplay.HIDDEN)

        password("env.GITHUB_TOKEN", value = "%github.actions.dispatch.pat%", display = ParameterDisplay.HIDDEN)

        password("env.SPACE_USERNAME", value = "%space.packages.apl.user%", display = ParameterDisplay.HIDDEN)
        password("env.SPACE_PASSWORD", value = "%space.packages.apl.token%", display = ParameterDisplay.HIDDEN)
    }

    vcs {
        root(VCSKtorGeneratorWebsite)
        checkoutMode = CheckoutMode.ON_AGENT
        cleanCheckout = true
        showDependenciesChanges = true
    }

    steps {
        script {
            name = "Trigger & wait for GitHub Actions workflow"
            scriptContent = """
                #!/bin/bash
                set -euo pipefail

                OWNER="ktorio"
                REPO="ktor-generator-website"
                WORKFLOW_FILE="playwright-tests.yml"

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

                if ! command -v python3 >/dev/null 2>&1; then
                  echo "python3 not found; installing..."
                  sudo apt-get update -y
                  sudo apt-get install -y python3
                fi

                python3 --version

                TARGET_BRANCH="master"

                if [[ "${'$'}BRANCH_NAME" =~ pull/([0-9]+) ]]; then
                  PR_NUMBER="${'$'}{BASH_REMATCH[1]}"
                  echo "Detected pull request #${'$'}PR_NUMBER"
                  echo "Getting PR source branch..."

                  PR_DATA=$(curl -sS \
                    -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                    -H "Accept: application/vnd.github+json" \
                    "https://api.github.com/repos/${'$'}OWNER/${'$'}REPO/pulls/${'$'}PR_NUMBER")

                  PR_HEAD=$(python3 -c 'import json,sys
try:
    data=json.load(sys.stdin)
    print((data.get("head") or {}).get("ref") or "")
except Exception:
    print("")
' <<< "${'$'}PR_DATA")

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

                export TARGET_BRANCH

                REQUEST_ID="teamcity-%teamcity.build.id%-$(date +%s)"
                export REQUEST_ID
                echo "Using request_id=${'$'}REQUEST_ID"

                PAYLOAD=$(python3 -c 'import json,os
ref=os.environ["TARGET_BRANCH"]
rid=os.environ["REQUEST_ID"]
user=os.environ["SPACE_USERNAME"]
pw=os.environ["SPACE_PASSWORD"]
print(json.dumps({
  "ref": ref,
  "inputs": {
    "request_id": rid,
    "registry_username": user,
    "registry_password": pw
  }
}))
')

                echo "Dispatching workflow (inputs redacted)..."
                python3 -c 'import json,sys
payload=json.load(sys.stdin)
payload["inputs"]["registry_password"]="<REDACTED>"
print(json.dumps(payload, indent=2))
' <<< "${'$'}PAYLOAD"

                RESPONSE_FILE=$(mktemp)
                HTTP_STATUS=$(curl -sS -o "${'$'}RESPONSE_FILE" -w "%{http_code}" -X POST \
                  -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                  -H "Accept: application/vnd.github+json" \
                  -H "Content-Type: application/json" \
                  "https://api.github.com/repos/${'$'}OWNER/${'$'}REPO/actions/workflows/${'$'}WORKFLOW_FILE/dispatches" \
                  -d "${'$'}PAYLOAD" || true)

                if [ "${'$'}HTTP_STATUS" != "204" ] && [ "${'$'}HTTP_STATUS" != "202" ]; then
                  echo "Failed to trigger workflow. HTTP status: ${'$'}HTTP_STATUS"
                  echo "Response body:"
                  cat "${'$'}RESPONSE_FILE"
                  echo
                  exit 1
                fi

                echo "Successfully triggered workflow on ref: ${'$'}TARGET_BRANCH (HTTP ${'$'}HTTP_STATUS)"

                echo "Finding workflow run id by request_id=${'$'}REQUEST_ID ..."
                run_id=""
                for i in {1..30}; do
                  runs_json=$(curl -sS \
                    -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                    -H "Accept: application/vnd.github+json" \
                    "https://api.github.com/repos/${'$'}OWNER/${'$'}REPO/actions/workflows/${'$'}WORKFLOW_FILE/runs?event=workflow_dispatch&branch=${'$'}TARGET_BRANCH&per_page=30")

                  run_id=$(python3 -c 'import json,os,sys
rid=os.environ.get("REQUEST_ID","")
data=json.load(sys.stdin)
for r in data.get("workflow_runs",[]):
    title=(r.get("display_title") or r.get("name") or "")
    if rid and ("request_id="+rid) in title:
        print(r.get("id",""))
        break
' <<< "${'$'}runs_json")

                  if [[ -n "${'$'}run_id" && "${'$'}run_id" != "null" ]]; then
                    break
                  fi

                  sleep 2
                done

                if [[ -z "${'$'}run_id" || "${'$'}run_id" == "null" ]]; then
                  echo "ERROR: Could not locate the GitHub Actions run for request_id=${'$'}REQUEST_ID"
                  echo "Make sure the workflow has: run-name: \"... (request_id=${'$'}{{ inputs.request_id }})\""
                  exit 1
                fi

                echo "Found run_id=${'$'}run_id. Waiting for completion..."

                deadline=$(( $(date +%s) + 60*60 ))

                while true; do
                  if (( $(date +%s) > deadline )); then
                    echo "ERROR: Timed out waiting for GitHub Actions run ${'$'}run_id"
                    exit 1
                  fi

                  run_json=$(curl -sS \
                    -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                    -H "Accept: application/vnd.github+json" \
                    "https://api.github.com/repos/${'$'}OWNER/${'$'}REPO/actions/runs/${'$'}run_id")

                  status=$(python3 -c 'import json,sys
run=json.load(sys.stdin)
print(run.get("status") or "")
' <<< "${'$'}run_json")

                  conclusion=$(python3 -c 'import json,sys
run=json.load(sys.stdin)
print(run.get("conclusion") or "")
' <<< "${'$'}run_json")

                  html_url=$(python3 -c 'import json,sys
run=json.load(sys.stdin)
print(run.get("html_url") or "")
' <<< "${'$'}run_json")

                  echo "Run status=${'$'}status conclusion=${'$'}{conclusion:-<none>} url=${'$'}html_url"

                  if [[ "${'$'}status" == "completed" ]]; then
                    if [[ "${'$'}conclusion" == "success" ]]; then
                      echo "GitHub workflow succeeded."
                      exit 0
                    else
                      echo "GitHub workflow failed: conclusion=${'$'}conclusion"
                      exit 1
                    fi
                  fi

                  sleep 10
                done
            """.trimIndent()
        }
    }

    defaultBuildFeatures()
    addSlackNotifications(
        channel = "#ktor-website-generator-tests",
        buildFailed = true
    )

    triggers {
        onChangeDefaultOrPullRequest()
    }
})
