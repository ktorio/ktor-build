package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.defaultTimeouts
import subprojects.release.publishing.*

data class JDKEntry(val name: String, val env: String)
data class OSEntry(val name: String, val agentString: String, val taskName: String)
data class JSEntry(val name: String, val dockerContainer: String)
data class OSJDKEntry(val osEntry: OSEntry, val jdkEntry: JDKEntry)

const val junitReportArtifact =  "+:**/build/reports/** => junitReports.tgz"
const val memoryReportArtifact = "+:**/hs_err* => outOfMemoryDumps.tgz"

val macOS = OSEntry("macOS", "Mac OS X", "linkDebugTestMacosX64")
val linux = OSEntry("Linux", "Linux", "linkDebugTestLinuxX64")
val windows = OSEntry("Windows", "Windows", "linkDebugTestMingwX64")

val operatingSystems = listOf(macOS, linux, windows)

val java8 = JDKEntry("Java 8", "JDK_18")
val java11 = JDKEntry("Java 11", "JDK_11")

val jdkVersions = listOf(java8, java11)

val js = JSEntry("Chrome/Node.js", "stl5/ktor-test-image:latest")

val javaScriptEngines = listOf(js)

val stressTests = listOf(
    OSJDKEntry(linux, java8),
    OSJDKEntry(windows, java8))

val generatedBuilds = hashMapOf<String, BuildType>()

val publishingTargets = listOf(
    Build("JVM", generatedBuilds["${linux.name}${java11.name}"]),
    Build("JavaScript", generatedBuilds[js.name]),
    Build("Windows", generatedBuilds[windows.name]),
    Build("Linux", generatedBuilds[linux.name]),
    Build("macOS", generatedBuilds[macOS.name])
)

object ProjectCore : Project({
    id("ProjectKtorCore")
    name = "Core"
    description = "Ktor Core Framework"

    params {
        defaultTimeouts()
    }

    val OsJdk = operatingSystems.flatMap { os ->
        jdkVersions.map { jdk -> OSJDKEntry(os, jdk) }
    }

    val osJdkBuilds = OsJdk.map(::CoreBuild)
    val nativeBuilds = operatingSystems.map(::NativeBuild)
    val javaScriptBuilds = javaScriptEngines.map(::JavaScriptBuild)
    val stressTestBuilds = stressTests.map(::StressTestBuild)

    val allBuilds = osJdkBuilds.plus(nativeBuilds).plus(javaScriptBuilds).plus(stressTestBuilds)

    allBuilds.forEach(::buildType)

    buildType {
        createCompositeBuild("KtorCore_All", "Build All Core", VCSCore, allBuilds)
    }
})

fun BuildType.createCompositeBuild(buildId: String, buildName: String, vcsRoot: VcsRoot, builds: List<BuildType>) {
    id(buildId)
    name = buildName
    type = BuildTypeSettings.Type.COMPOSITE

    vcs {
        root(vcsRoot)
    }

    dependencies {
        builds.mapNotNull { it.id }.forEach { id ->
            snapshot(id) {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
    }
}

fun Requirements.require(os: String, minMemoryDB: Int = -1) {
    contains("teamcity.agent.jvm.os.name", os)

    if (minMemoryDB != -1) {
        noLessThan("teamcity.agent.hardware.memorySizeMb", minMemoryDB.toString())
    }
}
