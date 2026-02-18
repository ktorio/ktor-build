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

                TARGET_BRANCH="master"

                if [[ "${'$'}BRANCH_NAME" =~ pull/([0-9]+) ]]; then
                  PR_NUMBER="${'$'}{BASH_REMATCH[1]}"
                  echo "Detected pull request #${'$'}PR_NUMBER"
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

                echo "Dispatching workflow (inputs redacted)..."
                echo "${'$'}PAYLOAD" | sed 's/"registry_password":[^,}]*/"registry_password":"<REDACTED>"/' | (jq . 2>/dev/null || cat)

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

                echo "Successfully triggered workflow on ref: ${'$'}TARGET_BRANCH (HTTP ${'$'}HTTP_STATUS)"
            """.trimIndent()
        }
    }

    defaultBuildFeatures()

    triggers {
        onChangeDefaultOrPullRequest()
    }
})
