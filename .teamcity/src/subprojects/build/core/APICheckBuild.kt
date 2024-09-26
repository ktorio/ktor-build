package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object APICheckBuild : BuildType({
    id("KtorMatrixCore_APICheck".toId())
    name = "Check API"
    artifactRules = formatArtifacts(memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    triggers {
        onChangeDefaultOrPullRequest()
    }

    steps {
        gradle {
            buildFile = "build.gradle.kts"
            name = "API Check"
            tasks = "apiCheck"
            jdkHome = "%env.${javaLTS.env}%"
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
    }
})
