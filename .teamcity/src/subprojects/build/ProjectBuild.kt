package subprojects.build


import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.BuildFailureOnMetric
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.failOnMetricChange
import subprojects.*
import subprojects.build.apidocs.ProjectBuildAPIDocs
import subprojects.build.core.*
import subprojects.build.docsamples.*
import subprojects.build.generator.*
import subprojects.build.plugin.*
import subprojects.build.samples.*

data class JDKEntry(val name: String, val env: String)
data class OSEntry(val name: String, val agentString: String, val taskName: String)
data class JSEntry(val name: String, val dockerContainer: String)
data class OSJDKEntry(val osEntry: OSEntry, val jdkEntry: JDKEntry)

const val junitReportArtifact =  "+:**/build/reports/** => junitReports.tgz"
const val memoryReportArtifact = "+:**/hs_err* => outOfMemoryDumps.tgz"

val macOS = OSEntry("macOS", "Mac OS X", "cleanMacosX64Test macosX64Test")
val linux = OSEntry("Linux", "Linux", "cleanLinuxX64Test linuxX64Test")
val windows = OSEntry("Windows", "Windows", "cleanMingwX64Test mingwX64Test")

val operatingSystems = listOf(macOS, linux, windows)

val java8 = JDKEntry("Java 8", "JDK_18")
val java11 = JDKEntry("Java 11", "JDK_11")

val jdkVersions = listOf(java8, java11)

val js = JSEntry("Chrome/Node.js", "stl5/ktor-test-image:latest")

val javaScriptEngines = listOf(js)

val stressTests = listOf(
    OSJDKEntry(linux, java8),
    OSJDKEntry(windows, java8))

object ProjectBuild : Project({
    id("ProjectKtorBuild")
    name = "Build"
    description = "Build configurations for Ktor"

    subProject(ProjectGenerator)
    subProject(ProjectSamples)
    subProject(ProjectCore)
    subProject(ProjectDocSamples)
    subProject(ProjectBuildAPIDocs)
    subProject(ProjectPlugin)

    cleanup {
        keepRule {
            id = "KtorKeepRule_AllBranchesEverything"
            dataToKeep = everything()
            keepAtLeast = days(25)
        }
    }
})

fun ParametrizedWithType.defaultTimeouts() {
    param("system.org.gradle.internal.http.connectionTimeout", "240000")
    param("system.org.gradle.internal.http.socketTimeout", "120000")
}

fun BuildType.defaultBuildFeatures(rootId: String) {
    features {
        perfmon {
        }

        githubPullRequestsLoader(rootId)
        githubCommitStatusPublisher()
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
        executionTimeoutMin = 50
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
            +:*
            -:pull/*
        """.trimIndent()
            filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
        }
    }
}

fun BuildFeatures.githubCommitStatusPublisher() {
    commitStatusPublisher {
        vcsRootExtId = VCSCore.id.toString()

        publisher = github {
            githubUrl = "https://api.github.com"
            authType = personalToken {
                token = VCSToken
            }
        }
    }
}
