package subprojects

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.triggers.*

/**
 * A set of common branch filters.
 *
 * Note that branches in branch filters should be references by
 * [logical names](https://www.jetbrains.com/help/teamcity/2024.07/working-with-feature-branches.html#Logical+Branch+Name).
 *
 * [Docs: Branch Filter Format](https://www.jetbrains.com/help/teamcity/2024.07/branch-filter.html#Branch+Filter+Format)
 */
object BranchFilter {
    const val AllBranches = "+:*"
    const val DefaultBranch = "+:<default>"
    const val PullRequest = "+:pull/*"
    const val DefaultOrPullRequest = "$DefaultBranch\n$PullRequest"
}

/**
 * A set of common trigger rules.
 *
 * [Docs: VCS Trigger Rules](https://www.jetbrains.com/help/teamcity/2024.07/configuring-vcs-triggers.html#vcs-trigger-rules-1)
 */
object TriggerRules {
    val IgnoreNonCodeChanges = """
        -:*.md
        -:.gitignore
    """.trimIndent()

    val IgnoreBotCommits = """
        -:user=*+renovate[bot]:.
        -:user=*+dependabot[bot]:.
    """.trimIndent()

    val GradleFiles = """
        +:**/*.gradle
        +:**/*.gradle.kts
        +:**/*.versions.toml
        +:buildSrc/**
        +:**/gradle-wrapper.properties
        +:**/gradle.properties
    """.trimIndent()
}

fun Triggers.onChangeDefaultOrPullRequest(additionalTriggerRules: String = "") {
    vcs {
        triggerRules = listOf(
            TriggerRules.IgnoreNonCodeChanges,
            additionalTriggerRules,
        ).joinToString("\n")
        branchFilter = BranchFilter.DefaultOrPullRequest
    }
}

fun Triggers.onChangeAllBranchesTrigger() {
    vcs {
        triggerRules = TriggerRules.IgnoreNonCodeChanges
        branchFilter = BranchFilter.AllBranches
    }
}

fun Triggers.nightlyEAPBranchesTrigger() {
    schedule {
        schedulingPolicy = daily {
            hour = 20
        }
        triggerBuild = always()
    }
}
