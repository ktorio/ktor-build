package subprojects

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import jetbrains.buildServer.configs.kotlin.vcs.*
import subprojects.build.*

const val defaultBranch = "refs/heads/main"
const val VCSUsername = "hhariri"
const val VCSToken = "%github.token%"
const val DefaultAndPullRequests = "+:$defaultBranch\n+:refs/(pull/*)/head"
const val AllBranchesAndPullRequests = "+:refs/heads/*\n+:refs/(pull/*)/head"

object VCSCore : PasswordVcsRoot({
    name = "Ktor Core"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = AllBranchesAndPullRequests
})

object VCSCoreEAP : PasswordVcsRoot({
    name = "Ktor Core EAP Branches"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = """
        +:refs/heads/(*-eap)
        +:$defaultBranch
    """.trimIndent()
})

object VCSDocs : PasswordVcsRoot({
    name = "Ktor documentation"
    url = "https://github.com/ktorio/ktor-documentation.git"
})

object VCSSamples : PasswordVcsRoot({
    name = "Ktor Samples"
    url = "https://github.com/ktorio/ktor-samples.git"
})

object VCSGetStartedSample : PasswordVcsRoot({
    name = "Ktor Get Started Sample"
    url = "https://github.com/ktorio/ktor-get-started.git"
})

object VCSGradleSample : PasswordVcsRoot({
    name = "Ktor Gradle Sample"
    url = "https://github.com/ktorio/ktor-gradle-sample.git"
})

object VCSMavenSample : PasswordVcsRoot({
    name = "Ktor Maven Sample"
    url = "https://github.com/ktorio/ktor-maven-sample.git"
})

object VCSHttpApiSample : PasswordVcsRoot({
    name = "Ktor HTTP API Sample"
    url = "https://github.com/ktorio/ktor-http-api-sample.git"
})

object VCSWebSocketsChatSample : PasswordVcsRoot({
    name = "Ktor Websockets Chat Sample"
    url = "https://github.com/ktorio/ktor-websockets-chat-sample.git"
})

object VCSWebsiteSample : PasswordVcsRoot({
    name = "Ktor Website Sample"
    url = "https://github.com/ktorio/ktor-website-sample.git"
})

object VCSAPIDocs : PasswordVcsRoot({
    name = "API Docs"
    url = "https://github.com/ktorio/api.ktor.io.git"
})

object VCSKotlinxHtml : PasswordVcsRoot({
    name = "Kotlinx.html Library"
    url = "https://github.com/Kotlin/kotlinx.html.git"
    branch = "refs/heads/master"
    branchSpec = AllBranchesAndPullRequests
})

object VCSKtorBenchmarks : PasswordVcsRoot({
    name = "Ktor Benchmarks"
    url = "https://github.com/ktorio/ktor-benchmarks.git"
})

object VCSPluginRegistry : PasswordVcsRoot({
    name = "Ktor Plugin Registry"
    url = "https://github.com/ktorio/ktor-plugin-registry.git"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorGeneratorBackend : PasswordVcsRoot({
    name = "Ktor Generator Backend"
    url = "https://github.com/ktorio/ktor-generator-backend.git"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorGeneratorWebsite : PasswordVcsRoot({
    name = "Ktor Generator Website"
    url = "https://github.com/ktorio/ktor-generator-website.git"
    branch = "refs/heads/master"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorCLI : PasswordVcsRoot({
    name = "Ktor CLI"
    url = "https://github.com/ktorio/ktor-cli.git"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorBuildPlugins : PasswordVcsRoot({
    name = "Ktor Build Plugins"
    url = "https://github.com/ktorio/ktor-build-plugins.git"
    branchSpec = DefaultAndPullRequests
})

open class KtorVcsRoot(init: GitVcsRoot.() -> Unit) : GitVcsRoot({
    userNameStyle = UserNameStyle.USERID
    branch = defaultBranch

    init()
})

open class PasswordVcsRoot(init: GitVcsRoot.() -> Unit) : KtorVcsRoot({
    authMethod = password {
        userName = VCSUsername
        password = VCSToken
    }

    init()
})

fun Triggers.onChangeDefaultOrPullRequest() {
    vcs {
        triggerRules = """
            -:*.md
            -:.gitignore
            -:user=renovate[bot]:.
            -:user=dependabot[bot]:.
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
            -:user=renovate[bot]:.
            -:user=dependabot[bot]:.
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
