package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object APICheckBuild : BuildType({
    id("KtorMatrixCore_APICheck".toId())
    name = "Check ABI"
    artifactRules = formatArtifacts(memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    params {
        extraGradleParams()
    }

    cancelPreviousBuilds()
    enableRustForRelevantChanges(Agents.OS.MacOS)

    steps {
        gradle {
            name = "API Check"
            tasks = "checkLegacyAbi"
            gradleParams = "$GradleParams"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.MacOS, Agents.Arch.Arm64)
    }
})
