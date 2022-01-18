package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*

object JPMSCheckBuild: BuildType({
    id("JPMSCheck".toExtId())
    name = "Check JPMS"
    vcs {
        root(VCSCore)
    }
    triggers {
        onChangeAllBranchesTrigger()
    }
    steps {
        gradle {
            buildFile = "build.gradle.kts"
            name = "API Check"
            tasks = ":ktor-java-modules-test:compileJava"
            jdkHome = "%env.${java8.env}%"
        }
    }
    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
    }
})
