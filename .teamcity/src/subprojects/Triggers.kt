package subprojects

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.build.*

fun Triggers.onChangeDefaultOrPullRequest() {
    vcs {
        triggerRules = """
            -:*.md
            -:.gitignore
            -:user=*+renovate[bot]:.
            -:user=*+dependabot[bot]:.
        """.trimIndent()
        branchFilter = """
            +:pull/*
            +:<default>
        """
    }
}

fun Triggers.onChangeAllBranchesTrigger() {
    vcs {
        triggerRules = """
            -:*.md
            -:.gitignore
            -:user=*+renovate[bot]:.
            -:user=*+dependabot[bot]:.
        """.trimIndent()
        branchFilter = """
            +:*
            -:dependabot/*
            -:renovate/*
        """.trimIndent()
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

    val gradle = """
        +:**/*.gradle
        +:**/*.gradle.kts
        +:**/*.versions.toml
        +:buildSrc/**
        +:**/gradle-wrapper.properties
        +:**/gradle.properties
    """.trimIndent()

    vcs {
        triggerRules = listOf(targetSources, gradle).joinToString("\n")
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
