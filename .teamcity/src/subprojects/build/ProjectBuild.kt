package subprojects.build


import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
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

val macOS = OSEntry("macOS", "Mac OS X", "macosX64Test")
val linux = OSEntry("Linux", "Linux", "linuxX64Test")
val windows = OSEntry("Windows", "Windows", "mingwX64Test")

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
            id = "KtorKeepRule_DefaultBranchArtifacts"
            dataToKeep = allArtifacts()
            days(5)
            applyToBuilds {
                inBranches {
                    branchFilter = patterns(defaultBranch)
                }
            }
        }
    }
})

fun BuildFeatures.monitorPerformance() {
    perfmon {
    }
}

fun ParametrizedWithType.defaultTimeouts() {
    param("system.org.gradle.internal.http.connectionTimeout", "240000")
    param("system.org.gradle.internal.http.socketTimeout", "120000")
}