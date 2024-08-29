package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*

object APICheckBuild : BuildType({
    id("KtorMatrixCore_APICheck".toExtId())
    name = "Check API"
    artifactRules = formatArtifacts(memoryReportArtifact)
    vcs {
        root(VCSCore)
    }
    triggers {
        onChangeNoBranchFilter()
    }
    steps {
        gradle {
            buildFile = "build.gradle.kts"
            name = "API Check"
            tasks = "apiCheck"
            jdkHome = "%env.${java8.env}%"
        }
    }
    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
    }
})
