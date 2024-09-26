package subprojects

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.build.*

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

fun Triggers.onChangeDefaultOrPullRequest() {
    vcs {
        triggerRules = """
            ${TriggerRules.IgnoreNonCodeChanges}
            ${TriggerRules.IgnoreBotCommits}
        """.trimIndent()
        branchFilter = BranchFilter.DefaultOrPullRequest
    }
}

fun Triggers.onChangeAllBranchesTrigger() {
    vcs {
        triggerRules = """
            ${TriggerRules.IgnoreNonCodeChanges}
            ${TriggerRules.IgnoreBotCommits}
        """.trimIndent()
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

fun Triggers.onBuildTargetChanges(target: BuildTarget) {
    val targetSources = target.sourceSets.joinToString("\n") { sourceSet ->
        // Include the sourceSet itself and all possible suffixes like Arm64/X64, Main/Test, Simulator/Device
        """
            +:**/$sourceSet/**
            +:**/$sourceSet*/**
        """.trimIndent()
    }

    vcs {
        triggerRules = """
            $targetSources
            ${TriggerRules.GradleFiles}
        """.trimIndent()
        branchFilter = BranchFilter.DefaultOrPullRequest
    }
}

// Should be in sync with TargetsConfig.kt in the Ktor project
class BuildTarget(sourceSets: List<String>) {

    constructor(vararg sourceSets: String) : this(sourceSets.toList())

    val sourceSets = sourceSets + "common"

    companion object {
        val JVM = BuildTarget("jvm", "jvmAndPosix", "jvmAndNix")
        val JS = BuildTarget("js", "jsAndWasmShared")
        val WasmJS = BuildTarget("wasmJs", "jsAndWasmShared")

        fun Native(osEntry: OSEntry) = BuildTarget(
            listOf("desktop", "posix", "jvmAndPosix") +
                nixSourceSets(osEntry) +
                osSourceSets(osEntry)
        )

        /** Source sets that are built only on a specific OS. */
        private fun osSourceSets(osEntry: OSEntry): List<String> = when (osEntry) {
            macOS -> listOf("darwin", "macos", "ios", "tvos", "watchos")
            linux -> listOf("linux")
            windows -> listOf("windows", "mingw")
            else -> emptyList()
        }

        private fun nixSourceSets(osEntry: OSEntry): List<String> = when (osEntry) {
            linux, macOS -> listOf("nix", "jvmAndNix")
            else -> emptyList()
        }
    }
}
