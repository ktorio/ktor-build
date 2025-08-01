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
        
        TARGET_BRANCH="main"
        
        if [[ "${'$'}BRANCH_NAME" =~ pull/([0-9]+) ]]; then
            PR_NUMBER=${'$'}{BASH_REMATCH[1]}
            echo "Detected pull request #${'$'}PR_NUMBER"
            
            echo "Getting PR source branch..."
            PR_DATA=$(curl -s \
              -H "Authorization: token %github.token%" \
              -H "Accept: application/vnd.github.v3+json" \
              https://api.github.com/repos/ktorio/ktor-generator-website/pulls/${'$'}PR_NUMBER)
            
            PR_HEAD=$(echo "${'$'}PR_DATA" | grep -o '"head":.*"ref":"[^"]*"' | grep -o '"ref":"[^"]*"' | cut -d'"' -f4)
            
            if [ -n "${'$'}PR_HEAD" ]; then
                echo "Using PR source branch: ${'$'}PR_HEAD"
                TARGET_BRANCH="${'$'}PR_HEAD"
            else
                echo "WARNING: Could not determine PR source branch, falling back to default branch"
            fi
        else
            if [ -n "${'$'}BRANCH_NAME" ] && [ "${'$'}BRANCH_NAME" != "<default>" ]; then
                # Strip refs/heads/ prefix if present
                BRANCH_NAME=$(echo "${'$'}BRANCH_NAME" | sed 's|^refs/heads/||')
                TARGET_BRANCH="${'$'}BRANCH_NAME"
            fi
    
            echo "Using branch: ${'$'}TARGET_BRANCH"
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
        -H "Authorization: token %github.token%" \
        -H "Accept: application/vnd.github.v3+json" \
        -H "Content-Type: application/json" \
        https://api.github.com/repos/ktorio/ktor-generator-website/actions/workflows/playwright-tests.yml/dispatches \
        -d "${'$'}PAYLOAD")
        
        if [ "${'$'}HTTP_STATUS" = "204" ]; then
            echo "Successfully triggered GitHub Actions workflow on branch: ${'$'}TARGET_BRANCH"
        else
            echo "Failed to trigger workflow. HTTP status: ${'$'}HTTP_STATUS"
            echo "Error details:"
            curl -v -X POST \
            -H "Authorization: token %github.token%" \
            -H "Accept: application/vnd.github.v3+json" \
            -H "Content-Type: application/json" \
            https://api.github.com/repos/ktorio/ktor-generator-website/actions/workflows/playwright-tests.yml/dispatches \
            -d "${'$'}PAYLOAD"
            
            exit 1
        fi
        """
        }
    }

    defaultBuildFeatures(VCSKtorGeneratorWebsite.id.toString())

    triggers {
        onChangeDefaultOrPullRequest()
    }
})
