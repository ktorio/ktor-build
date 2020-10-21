package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*

data class JDKEntry(val name: String, val env: String)
data class OSEntry(val name: String, val agentString: String, val taskName: String)
data class JavaScriptEngine(val name: String, val dockerContainer: String)
data class OSJDKEntry(val osEntry: OSEntry, val jdkEntry: JDKEntry)

const val junitReportArtifact =  "+:**/build/reports => junitReports.tgz"
const val memoryReportArtifact = "+:**/hs_err* => outOfMemoryDumps.tgz"

val operatingSystems = listOf(
    OSEntry("macOS", "Mac OS X", "linkDebugTestMacosX64"),
    OSEntry("Li" +
        "nux", "Linux", "linkDebugTestLinuxX64"),
    OSEntry("Windows", "Windows", "linkDebugTestMingwX64"))
val jdkVersions = listOf(
    JDKEntry("Java 8", "JDK_18"),
    JDKEntry("Java 11", "JDK_11"))
val javaScriptEngines = listOf(
    JavaScriptEngine("Chrome/Node.js", "stl5/ktor-test-image:latest"))
val stressTests = listOf(
    OSJDKEntry(OSEntry("Linux", "Linux", "linkDebugTestLinuxX64"), JDKEntry("Java 8", "JDK_18")),
    OSJDKEntry(OSEntry("Windows", "Windows", "linkDebugTestMingwX64"), JDKEntry("Java 8", "JDK_18")))

object ProjectCore : Project({
    id("ProjectKtorCore")
    name = "Core"
    description = "Ktor Core Framework"

    params {
        param("system.org.gradle.internal.http.connectionTimeout", "120000")
        param("system.org.gradle.internal.http.socketTimeout", "120000")
    }

    val osJVMCombos = operatingSystems.flatMap { os ->
        jdkVersions.map { jdk -> OSJDKEntry(os, jdk) }
    }

    val osJVMBuilds = osJVMCombos.map(::CoreBuild)
    val nativeBuilds = operatingSystems.map(::NativeBuild)
    val javaScriptBuilds = javaScriptEngines.map(::JavaScriptBuild)
    val stressTestBuilds = stressTests.map(::StressTestBuild)

    val allBuilds = osJVMBuilds.plus(nativeBuilds).plus(javaScriptBuilds).plus(stressTestBuilds)

    allBuilds.forEach(::buildType)

    buildType {
        id("KtorCore_All")
        name = "Build All Core"
        type = BuildTypeSettings.Type.COMPOSITE

        vcs {
            root(VCSCore)
        }

        dependencies {
            allBuilds.mapNotNull { it.id }.forEach { id ->
                snapshot(id) {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
        }
    }
})

fun Requirements.require(os: String, minMemoryDB: Int) {
    contains("teamcity.agent.jvm.os.name", os)
    noLessThan("teamcity.agent.hardware.memorySizeMb", minMemoryDB.toString())
}
