package subprojects.build


import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.failureConditions.*
import subprojects.*
import subprojects.Agents.OS
import subprojects.build.core.*
import subprojects.build.docsamples.*
import subprojects.build.generator.*
import subprojects.build.plugin.*
import subprojects.build.samples.*

data class JDKEntry(
    val version: Int,
    val name: String = "Java $version",
    val env: String = "JDK_${version}_0",
) {

    companion object {
        val Java8 = JDKEntry(8, env = "JDK_1_8")
        val Java11 = JDKEntry(11)
        val Java17 = JDKEntry(17)
        val Java21 = JDKEntry(21)
        val JavaLTS = Java21
    }
}

/**
 * @property name The name to be shown as a part of a build name.
 * @property id The ID to be used as a part of build ID.
 */
data class NativeEntry(
    override val os: OS,
    val target: String,
    override val arch: Agents.Arch = Agents.Arch.X64,
    val id: String = "${os.id}_${arch.id}",
    val name: String = "Native ${os.id}",
) : AgentSpec {

    fun targetTask(prefix: String = "", suffix: String = ""): String {
        check(prefix.isNotEmpty() || suffix.isNotEmpty()) { "Prefix or suffix must be provided" }
        val target = if (prefix.isNotEmpty()) target.replaceFirstChar { it.uppercase() } else target
        return "${prefix}${target}${suffix}"
    }

    companion object {
        val MacOSX64 = NativeEntry(
            os = OS.MacOS,
            target = "macosX64",
        )

        val MacOSArm64 = NativeEntry(
            os = OS.MacOS,
            arch = Agents.Arch.Arm64,
            target = "macosArm64",
        )

        val LinuxX64 = NativeEntry(
            os = OS.Linux,
            target = "linuxX64",
        )

        val MingwX64 = NativeEntry(
            os = OS.Windows,
            target = "mingwX64",
        )

        val All = listOf(
            LinuxX64,
            MacOSX64,
            MacOSArm64,
            MingwX64,
        )
    }
}

data class JSEntry(val name: String, val dockerContainer: String)

data class OSJDKEntry(
    override val os: OS,
    val jdkEntry: JDKEntry,
    override val arch: Agents.Arch = Agents.Arch.X64,
) : AgentSpec {
    /** The ID to be used as a part of build ID. */
    val id: String = "${os.id}${jdkEntry.name}"
}

const val junitReportArtifact = "+:**/build/reports/** => junitReports.tgz"
const val memoryReportArtifact = "+:**/hs_err*|+:**/HEAP/* => outOfMemoryDumps.tgz"

val osJdks = listOf(
    OSJDKEntry(OS.Linux, JDKEntry.Java8), // Minimal supported version
    OSJDKEntry(OS.Linux, JDKEntry.Java11), // Minimal supported version for Jakarta modules
    OSJDKEntry(OS.Linux, JDKEntry.Java17), // Version used to build Android projects
    OSJDKEntry(OS.Linux, JDKEntry.JavaLTS), // Latest LTS
)

val js = JSEntry("Chrome/Node.js", "stl5/ktor-test-image:2024-12-11")

val javaScriptEngines = listOf(js)

val stressTests = listOf(
    OSJDKEntry(OS.Linux, JDKEntry.JavaLTS),
    OSJDKEntry(OS.Windows, JDKEntry.JavaLTS)
)

object ProjectBuild : Project({
    id("ProjectKtorBuild")
    name = "Build"
    description = "Build configurations for Ktor"

    subProject(ProjectGenerator)
    subProject(ProjectSamples)
    subProject(ProjectCore)
    subProject(ProjectDocSamples)
    subProject(ProjectPlugin)

    cleanup {
        keepRule {
            id = "KtorKeepRule_AllBranchesEverything"
            dataToKeep = everything()
            keepAtLeast = days(25)
        }
    }
})

fun ParametrizedWithType.defaultGradleParams() {
    param("system.org.gradle.internal.http.connectionTimeout", "240000")
    param("system.org.gradle.internal.http.socketTimeout", "120000")

    // Enforce Configuration Cache compatible launch mode
    param("teamcity.internal.gradle.runner.launch.mode", "gradle-tooling-api")
}

fun BuildType.defaultBuildFeatures(rootId: String) {
    features {
        perfmon {}

        githubPullRequestsLoader(rootId)
        githubCommitStatusPublisher(rootId)
    }

    failureConditions {
        failOnMetricChange {
            id = "KtorFailureConditionLogSize"
            metric = BuildFailureOnMetric.MetricType.BUILD_LOG_SIZE
            units = BuildFailureOnMetric.MetricUnit.DEFAULT_UNIT
            comparison = BuildFailureOnMetric.MetricComparison.MORE
            compareTo = value()
            param("metricThreshold", "15MB")
        }
        executionTimeoutMin = 120
    }
}

fun BuildFeatures.githubPullRequestsLoader(rootId: String) {
    pullRequests {
        vcsRootExtId = rootId

        provider = github {
            authType = token {
                token = VCSToken
            }
            filterTargetBranch = """
                +:refs/heads/*
                -:refs/pull/*/head
            """.trimIndent()
            filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
        }
    }
}

fun BuildFeatures.githubCommitStatusPublisher(vcsRootId: String = VCSCore.id.toString()) {
    commitStatusPublisher {
        vcsRootExtId = vcsRootId

        publisher = github {
            githubUrl = "https://api.github.com"
            authType = personalToken {
                token = VCSToken
            }
        }
    }
}
