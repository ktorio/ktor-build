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
                
                source ${triggerGitHubWorkflowScript()}
                
                triggerAndMonitorWorkflow "ktorio/ktor-generator-website" \
                                        "playwright-tests.yml" \
                                        "%teamcity.build.branch%" \
                                        "%github.token%" \
                                        "%env.SPACE_USERNAME%" \
                                        "%env.SPACE_PASSWORD%"
            """
        }
    }

    defaultBuildFeatures(VCSKtorGeneratorWebsite.id.toString())

    triggers {
        onChangeDefaultOrPullRequest()
    }
})

private fun triggerGitHubWorkflowScript(): String {
    return "scripts/trigger_github_workflow.sh"
}

