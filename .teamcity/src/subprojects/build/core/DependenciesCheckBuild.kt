package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

class DependenciesCheckBuild : BuildType({
    id("KtorDependenciesCheckBuildId".toId())
    name = "Ktor Dependencies Check"

    vcs {
        root(VCSCore)
    }

    steps {
        gradle {
            name = "Check Dependencies"
            tasks = "snyk-test"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(Agents.OS.Linux)
    }
})
