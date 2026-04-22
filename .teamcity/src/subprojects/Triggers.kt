package subprojects

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger.DAY.*

/**
 * A set of common branch filters.
 *
 * Note that branches in branch filters should be references by
 * [logical names](https://www.jetbrains.com/help/teamcity/latest/working-with-feature-branches.html#Logical+Branch+Name).
 *
 * [Docs: Branch Filter Format](https://www.jetbrains.com/help/teamcity/latest/branch-filter.html#Branch+Filter+Format)
 */
object BranchFilter {
    const val AllBranches = "+:*"
    const val DefaultBranch = "+:<default>"
    const val ReleaseBranches = "+:release/*"
    const val PullRequest = "+:pull/*"
    const val DefaultOrPullRequest = "$DefaultBranch\n$ReleaseBranches\n$PullRequest"
}

fun Triggers.onChangeDefaultOrPullRequest() {
    vcs {
        branchFilter = BranchFilter.DefaultOrPullRequest
    }
}

fun Triggers.onChangeAllBranchesTrigger() {
    vcs {
        branchFilter = BranchFilter.AllBranches
    }
}

fun Triggers.weeklyEapBranchesTrigger() {
    schedule {
        schedulingPolicy = weekly {
            dayOfWeek = Sunday
            hour = 20
        }
        triggerBuild = always()
    }
}
