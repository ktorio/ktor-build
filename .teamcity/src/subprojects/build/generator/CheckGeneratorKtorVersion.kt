package subprojects.build.generator

import dsl.addSlackNotifications
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.gitHubAppBuildScopedToken
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger.DAY.Monday
import subprojects.*

/** External TeamCity id of [PublishPluginRegistry], used by this build's [finishBuildTrigger]. */
const val PUBLISH_PLUGIN_REGISTRY_EXTERNAL_ID = "Ktor_KtorPluginRegistry"

/**
 * Checks that the deployed generator does not advertise one Ktor version while pinning a different
 * one in the projects it produces, and that neither has fallen behind the latest Ktor release.
 */
object CheckGeneratorKtorVersion : BuildType({
    id("KtorGeneratorVersionCheck")
    name = "Check generator Ktor version"
    description = "Verifies start.ktor.io advertises, generates and pins a consistent, current Ktor version"

    params {
        param("generator.base.url", "https://start.ktor.io")
        password("env.GITHUB_PAT_FALLBACK", value = "%github.actions.dispatch.pat%", display = ParameterDisplay.HIDDEN)
    }

    steps {
        script {
            name = "Trigger & wait for GitHub Actions workflow"
            scriptContent = """
                #!/bin/bash
                set -euo pipefail

                OWNER="ktorio"
                REPO="ktor-generator-website"
                WORKFLOW_FILE="ktor-version-check.yml"

                # Always the default branch: this checks a deployment, not a branch.
                TARGET_REF="master"
                export TARGET_REF
                TARGET_BASE_URL="%generator.base.url%"
                export TARGET_BASE_URL

                # Resolve GITHUB_TOKEN: prefer the GitHub App scoped token, fall back to the PAT.
                if [ -n "${'$'}{GITHUB_TOKEN:-}" ] && [[ "${'$'}GITHUB_TOKEN" != *"%"* ]]; then
                  echo "Using GitHub App scoped token"
                elif [ -n "${'$'}{GITHUB_PAT_FALLBACK:-}" ] && [[ "${'$'}GITHUB_PAT_FALLBACK" != *"%"* ]]; then
                  echo "WARNING: GITHUB_TOKEN is not set (gitHubAppBuildScopedToken feature may have failed)"
                  echo "Falling back to PAT"
                  export GITHUB_TOKEN="${'$'}GITHUB_PAT_FALLBACK"
                else
                  echo "ERROR: Neither GITHUB_TOKEN (GitHub App) nor GITHUB_PAT_FALLBACK is available"
                  exit 1
                fi

                if ! command -v python3 >/dev/null 2>&1; then
                  echo "python3 not found; installing..."
                  sudo apt-get update -y
                  sudo apt-get install -y python3
                fi

                REQUEST_ID="teamcity-%teamcity.build.id%-$(date +%s)"
                export REQUEST_ID
                echo "Using request_id=${'$'}REQUEST_ID, target=${'$'}TARGET_BASE_URL"

                PAYLOAD=$(python3 -c 'import json,os
print(json.dumps({
  "ref": os.environ["TARGET_REF"],
  "inputs": {
    "request_id": os.environ["REQUEST_ID"],
    "base_url": os.environ["TARGET_BASE_URL"],
    "enforce_freshness": "true"
  }
}))
')

                RESPONSE_FILE=$(mktemp)
                HTTP_STATUS=$(curl -sS -o "${'$'}RESPONSE_FILE" -w "%{http_code}" -X POST \
                  -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                  -H "Accept: application/vnd.github+json" \
                  -H "Content-Type: application/json" \
                  "https://api.github.com/repos/${'$'}OWNER/${'$'}REPO/actions/workflows/${'$'}WORKFLOW_FILE/dispatches" \
                  -d "${'$'}PAYLOAD" || true)

                if [ "${'$'}HTTP_STATUS" != "204" ] && [ "${'$'}HTTP_STATUS" != "202" ]; then
                  echo "Failed to trigger workflow. HTTP status: ${'$'}HTTP_STATUS"
                  cat "${'$'}RESPONSE_FILE"
                  exit 1
                fi

                echo "Triggered workflow on ref ${'$'}TARGET_REF (HTTP ${'$'}HTTP_STATUS)"
                echo "Finding workflow run id by request_id=${'$'}REQUEST_ID ..."

                run_id=""
                for i in {1..30}; do
                  runs_json=$(curl -sS \
                    -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                    -H "Accept: application/vnd.github+json" \
                    "https://api.github.com/repos/${'$'}OWNER/${'$'}REPO/actions/workflows/${'$'}WORKFLOW_FILE/runs?event=workflow_dispatch&branch=${'$'}TARGET_REF&per_page=30")

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
                  echo "The workflow's run-name must interpolate the request_id input; see ktor-version-check.yml"
                  exit 1
                fi

                echo "Found run_id=${'$'}run_id. Waiting for completion..."
                deadline=$(( $(date +%s) + 30*60 ))

                while true; do
                  if (( $(date +%s) > deadline )); then
                    echo "ERROR: Timed out waiting for GitHub Actions run ${'$'}run_id"
                    exit 1
                  fi

                  run_json=$(curl -sS \
                    -H "Authorization: Bearer ${'$'}GITHUB_TOKEN" \
                    -H "Accept: application/vnd.github+json" \
                    "https://api.github.com/repos/${'$'}OWNER/${'$'}REPO/actions/runs/${'$'}run_id")

                  read -r status conclusion html_url < <(python3 -c 'import json,sys
run=json.load(sys.stdin)
print(run.get("status") or "-", run.get("conclusion") or "-", run.get("html_url") or "-")
' <<< "${'$'}run_json")

                  echo "Run status=${'$'}status conclusion=${'$'}conclusion url=${'$'}html_url"

                  if [[ "${'$'}status" == "completed" ]]; then
                    if [[ "${'$'}conclusion" == "success" ]]; then
                      echo "Generator Ktor version is consistent and current."
                      exit 0
                    fi
                    echo "##teamcity[buildProblem description='Ktor version drift on ${'$'}TARGET_BASE_URL - see ${'$'}html_url']"
                    exit 1
                  fi

                  sleep 10
                done
            """.trimIndent()
        }
    }

    features {
        gitHubAppBuildScopedToken {
            connectionId = "PROJECT_EXT_7"
            parameterName = "env.GITHUB_TOKEN"
            targetRepositories = "ktor-generator-website"
        }
    }

    addSlackNotifications(
        channel = "#ktor-website-generator-tests",
        buildFailed = true
    )

    triggers {
        // A registry publication changes the version the generator will serve, so check right after.
        finishBuildTrigger {
            buildType = PUBLISH_PLUGIN_REGISTRY_EXTERNAL_ID
            successfulOnly = true
            branchFilter = BranchFilter.DefaultBranch
        }

        // Ktor ships roughly monthly, so weekly bounds the detection lag at 7 days.
        schedule {
            schedulingPolicy = weekly {
                dayOfWeek = Monday
                hour = 7
            }
            branchFilter = BranchFilter.DefaultBranch
            triggerBuild = always()
        }
    }

    requirements {
        agent(Agents.OS.Linux)
    }
})
