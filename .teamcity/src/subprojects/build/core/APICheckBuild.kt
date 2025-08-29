package subprojects.build.core

import dsl.*
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

    cancelPreviousBuilds()

    enableRustCompilation(os = Agents.OS.Linux)

    steps {
        setupRustAarch64CrossCompilation(os = Agents.OS.Linux)

        gradle {
            name = "API Check"
            tasks = "apiCheck"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(Agents.OS.Linux)
    }
})
