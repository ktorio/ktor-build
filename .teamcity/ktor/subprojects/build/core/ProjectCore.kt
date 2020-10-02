package ktor.subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.*
import ktor.*

val operatingSystems = listOf("macOS", "Linux", "Windows")
val jdkVersions = listOf("JDK_18", "JDK_11")

object ProjectCore : Project({
    name = "Core"
    description = "Ktor Core Framework"
    for (os in operatingSystems) {
        for (jdk in jdkVersions) {
            buildType(BuildTemplate(os, jdk))
        }
    }

//    buildType(Build_Core_Compile)
})



class BuildTemplate(val os: String, val jdk: String): BuildType({
    id("KtorMatrix_$os$jdk".toExtId())
    name = "Compile $os $jdk"

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
            tasks = "clean build"
            buildFile = ""
            gradleWrapperPath = ""
        }
    }

    requirements {
        noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

/*

object Build_Core_Compile : BuildType({

})
*/
