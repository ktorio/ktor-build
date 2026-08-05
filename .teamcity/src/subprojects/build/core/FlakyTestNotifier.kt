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
    description = "Notifies @ktor-team-members on Slack when Build All Core detects flaky tests (result changed after retry)"

    params {
        password("env.SLACK_WEBHOOK_URL", "%system.slack.webhook.url%")
        param("slack.ktor.team.subteam.id", "%system.slack.ktor.team.subteam.id%")
    }

    steps {
        script {
            name = "Detect flaky tests and notify Slack"
            scriptFile("notify_flaky_tests.sh")
        }
    }

    triggers {
        finishBuildTrigger {
            buildType = "KtorCore_All"
            successfulOnly = false
            branchFilter = BranchFilter.DefaultBranch
        }
    }

    requirements {
        agent(Agents.OS.Linux)
    }
})
