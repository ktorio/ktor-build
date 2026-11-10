package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

class APICheckBuild(private val repo: KtorRepository) : BuildType({
    id(repo.id("KtorMatrixCore_APICheck".toId()))
    name = repo.name("Check ABI")
    artifactRules = formatArtifacts(memoryReportArtifact)

    vcs {
        root(repo.vcsRoot)
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
            tasks = "checkLegacyAbi"
            gradleParams = "$GradleParams"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux)
    }
})
