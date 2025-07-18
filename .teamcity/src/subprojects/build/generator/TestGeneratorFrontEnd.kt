package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
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
            export BRANCH_NAME="%teamcity.build.branch%"
            export BRANCH_NAME=`echo ${'$'}BRANCH_NAME | sed 's|^refs/heads/||'`
            if [ -z "${'$'}BRANCH_NAME" ] || [ "${'$'}BRANCH_NAME" = "<default>" ]; then
            export BRANCH_NAME="master"
            fi
            
            echo "Using branch: ${'$'}BRANCH_NAME"

            curl -X POST \
            -H "Authorization: token %github.token%" \
            -H "Accept: application/vnd.github.v3+json" \
            https://api.github.com/repos/ktorio/ktor-generator-website/actions/workflows/playwright-tests.yml/dispatches \
            -d "{ \"ref\": \"${'$'}BRANCH_NAME\", \"inputs\": { \"registry_username\": \"%env.SPACE_USERNAME%\", \"registry_password\": \"%env.SPACE_PASSWORD%\" } }"
            """
        }
    }

    defaultBuildFeatures(VCSKtorGeneratorWebsite.id.toString())

    triggers {
        vcs {}
    }
})
