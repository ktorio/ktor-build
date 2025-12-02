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
    enableRustForRelevantChanges(Agents.OS.Linux)

    steps {
        setupRustAarch64CrossCompilation(os = Agents.OS.Linux)

        gradle {
            name = "API Check"
            tasks = "checkLegacyAbi %gradle_params%"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux)
    }
})
