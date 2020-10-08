package subprojects.build.core

import VCSCore
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.*

data class JDKEntry(val name: String, val env: String)
data class OSEntry(val name: String, val agentString: String)
data class BrowserEntry(val name: String, val dockerContainer: String)

val operatingSystems = listOf(OSEntry("macOS", "Mac OS X"), OSEntry("Linux", "Linux"), OSEntry("Windows", "Windows"))
val jdkVersions = listOf(JDKEntry("Java 8", "JDK_18"), JDKEntry("Java 11", "JDK_11"))
val browsers = listOf(BrowserEntry("Chrome", "stl5/ktor-test-image:latest"))

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

class JavaScriptBuild(val browserEntry: BrowserEntry) : BuildType({
    id("KtorMatrixJavaScript_${browserEntry.name}".toExtId())
    name = "JavaScript on ${browserEntry.name}"

    setupDefaultVCSRootAndTriggers()

    steps {
        val gradleParameters = "--info -Penable-js-tests"
        gradle {
            name = "Parallel assemble"
            tasks = "assemble"
            gradleParams = gradleParameters
            dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
            dockerPull = true
            dockerImage = browserEntry.dockerContainer
        }
        gradle {
            name = "Build"
            tasks = "clean build --no-parallel --continue"
            gradleParams = gradleParameters
            dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
            dockerPull = true
            dockerImage = browserEntry.dockerContainer
        }
    }
    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
        noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
    }
})

class NativeBuild(val osEntry: OSEntry) : BuildType({
    id("KtorMatrixNative_${osEntry.name}".toExtId())
    name = "Native on ${osEntry.name}"

    setupDefaultVCSRootAndTriggers()

    steps {
        gradle {
            tasks = "build"
            jdkHome = "%env.JDK_11"
        }
    }
    requirements {
        noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
        contains("teamcity.agent.jvm.os.name", osEntry.agentString)
    }
})


class CoreBuild(val osEntry: OSEntry, val jdkEntry: JDKEntry) : BuildType({
    id("KtorMatrix_${osEntry.name}${jdkEntry.name}".toExtId())
    name = "${jdkEntry.name} on ${osEntry.name}"

    setupDefaultVCSRootAndTriggers()

    steps {
        gradle {
            tasks = "clean jvmTestClasses"
            jdkHome = "%env.${jdkEntry.env}%"
        }
    }
    requirements {
        noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
        contains("teamcity.agent.jvm.os.name", osEntry.agentString)
    }
})

private fun BuildType.setupDefaultVCSRootAndTriggers() {
    vcs {
        root(VCSCore)
    }
    triggers {
        vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_DEFAULT
        }
    }
}

