package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.*

/**
 * Alerts the Ktor team user group on Slack when the "Build All Core" composite ([ProjectCore]'s
 * `KtorCore_All`) detects a flaky test — a test whose result changes across the
 * `retryBuild` attempts on the default branch.
 */
object CoreFlakyTestNotifier : BuildType({
    id("KtorCore_FlakyTestNotifier")
    name = "Flaky Test Notifier"
    description = "Consolidates flaky tests from TeamCity (retry-diff) and Develocity (28d) and notifies @ktor-incident-responders on Slack"

    // The consolidated report is surfaced as the "Flaky Tests" build report tab (see ProjectCore).
    artifactRules = """
        flaky-report.json
        flaky-report.md
        flaky-report.html
    """.trimIndent()

    params {
        password("env.SLACK_WEBHOOK_URL", "%system.slack.webhook.url%")
        password("env.TC_REST_TOKEN", "%system.teamcity.rest.token%")
        password("env.DV_ACCESS_KEY", "%system.develocity.access.key%")
        param("slack.ktor.team.subteam.id", "%system.slack.ktor.team.subteam.id%")
        param("quarantine.build.type", FLAKY_TEST_BUILD_EXTERNAL_ID)
    }

    steps {
        script {
            name = "Detect flaky tests and notify Slack"
            scriptFile("notify_flaky_tests.sh")
        }
    }

    triggers {
        finishBuildTrigger {
            buildType = "Ktor_KtorCore_All"
            successfulOnly = false
            branchFilter = BranchFilter.DefaultBranch
        }

        finishBuildTrigger {
            buildType = FLAKY_TEST_BUILD_EXTERNAL_ID
            successfulOnly = false
            branchFilter = BranchFilter.DefaultBranch
        }
    }

    requirements {
        agent(Agents.OS.Linux)
    }
})
