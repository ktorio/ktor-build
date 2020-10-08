package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v2019_2.*

data class JDKEntry(val name: String, val env: String)
data class OSEntry(val name: String, val agentString: String, val taskName: String)
data class JavaScriptEngine(val name: String, val dockerContainer: String)

val operatingSystems = listOf(OSEntry("macOS", "Mac OS X", "linkDebugTestMacosX64"), OSEntry("Linux", "Linux", "linkDebugTestLinuxX64"), OSEntry("Windows", "Windows", "linkDebugTestMingwX64"))
val jdkVersions = listOf(JDKEntry("Java 8", "JDK_18"), JDKEntry("Java 11", "JDK_11"))
val browsers = listOf(JavaScriptEngine("Chrome/Node.js", "stl5/ktor-test-image:latest"))


object ProjectCore : Project({
    id("ProjectKtorCore")
    name = "Core"
    description = "Ktor Core Framework"
    for (os in operatingSystems) {
        for (jdk in jdkVersions) {
            buildType(CoreBuild(os, jdk))
        }
    }
    for (os in operatingSystems) {
        buildType(NativeBuild(os))
    }
    for (browser in browsers) {
        buildType(JavaScriptBuild(browser))
    }
})
