package subprojects.build.core

import VCSCore
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.*

data class JDKEntry(val name: String, val env: String)
data class OSEntry(val name: String, val agentString: String)

val operatingSystems = listOf(OSEntry("macOS", "Mac OS X"), OSEntry("Linux", "Linux"), OSEntry("Windows", "Windows"))
val jdkVersions = listOf(JDKEntry("Java 9","JDK_19"), JDKEntry("Java 11", "JDK_11"))

object ProjectCore : Project({
    id("ProjectKtorCore")
    name = "Core"
    description = "Ktor Core Framework"
    for (os in operatingSystems) {
        for (jdk in jdkVersions) {
            buildType(BuildTemplate(os, jdk))
        }
    }
})



class BuildTemplate(val osEntry: OSEntry, val jdkEntry: JDKEntry): BuildType({
    id("KtorMatrix_${osEntry.name}${jdkEntry.name}".toExtId())
    name = "Build with ${jdkEntry.name} on ${osEntry.name}"

    vcs {
        root(VCSCore)
    }
    triggers {
        vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_DEFAULT
            branchFilter = ""
        }
    }
    steps {
        gradle {
            tasks = "clean jvmTestClasses"
            buildFile = ""
            gradleWrapperPath = ""
            jdkHome = "%env.${jdkEntry.env}%"
        }
    }
    requirements {
        noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
        contains("teamcity.agent.jvm.os.name", osEntry.agentString)
    }
})

