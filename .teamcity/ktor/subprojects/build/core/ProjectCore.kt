package ktor.subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import ktor.*
import ktor.subprojects.build.docsamples.*
import ktor.subprojects.build.generator.*
import ktor.subprojects.build.plugin.*


object ProjectCore : Project({
    name = "Core"
    description = "Ktor Core Framework"

    buildType(Build_Core_Compile)
})

object Build_Core_Compile : BuildType({
    name = "Compile"

    vcs {
        root(VCSCore)
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
