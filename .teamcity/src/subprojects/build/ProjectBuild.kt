package subprojects.build


import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.failureConditions.*
import subprojects.*
import subprojects.build.core.*
import subprojects.build.docsamples.*
import subprojects.build.generator.*
import subprojects.build.plugin.*
import subprojects.build.samples.*

data class JDKEntry(val name: String, val env: String)
data class OSEntry(
    val os: Agents.OS,
    val testTasks: String,
    val binaryTasks: String,
    val arch: Agents.Arch = Agents.Arch.X64,
)

data class JSEntry(val name: String, val dockerContainer: String)
data class OSJDKEntry(val osEntry: OSEntry, val jdkEntry: JDKEntry) {
    val os: Agents.OS get() = osEntry.os
}

const val junitReportArtifact = "+:**/build/reports/** => junitReports.tgz"
const val memoryReportArtifact = "+:**/hs_err*|+:**/HEAP/* => outOfMemoryDumps.tgz"

val macOS = OSEntry(
    os = Agents.OS.MacOS,
    testTasks = "cleanMacosX64Test macosX64Test",
    binaryTasks = "linkReleaseExecutableMacosX64",
)

val linux = OSEntry(
    os = Agents.OS.Linux,
    testTasks = "cleanLinuxX64Test linuxX64Test",
    binaryTasks = "linkReleaseExecutableLinuxX64 linkReleaseExecutableLinuxArm64"
)

val windows = OSEntry(
    os = Agents.OS.Windows,
    testTasks = "cleanMingwX64Test mingwX64Test",
    binaryTasks = "linkReleaseExecutableMingwX64",
)

val operatingSystems = listOf(macOS, linux, windows)

val java8 = JDKEntry("Java 8", "JDK_1_8")
val java11 = JDKEntry("Java 11", "JDK_11_0")
val java17 = JDKEntry("Java 17", "JDK_17_0")
val java21 = JDKEntry("Java 21", "JDK_21_0")
val javaLTS = java21

val osJdks = listOf(
    OSJDKEntry(linux, java8), // Minimal supported version
    OSJDKEntry(linux, java17), // Version used to build Android projects
    OSJDKEntry(linux, javaLTS), // Latest LTS
)

val js = JSEntry("Chrome/Node.js", "stl5/ktor-test-image:latest")

val javaScriptEngines = listOf(js)

val stressTests = listOf(
    OSJDKEntry(linux, javaLTS),
    OSJDKEntry(windows, javaLTS)
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

    // Reduce the lifetime of Kotlin Daemons
    // See: https://github.com/gradle/gradle/issues/29331
    param("env.JAVA_OPTS", "-Dkotlin.daemon.options=autoshutdownIdleSeconds=30")
}

fun BuildType.defaultBuildFeatures(rootId: String) {
    features {
        perfmon {
        }

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
