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
            buildFile = "build.gradle.kts"
            tasks = "snyk-test"
            jdkHome = "%env.${javaLTS.env}%"
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
    }
})
