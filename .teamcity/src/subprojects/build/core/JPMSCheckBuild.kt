package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object JPMSCheckBuild: BuildType({
    id("JPMSCheck".toId())
    name = "Check JPMS"

    vcs {
        root(VCSCore)
    }

    cancelPreviousBuilds()
    steps {
        gradle {
            name = "Check JPMS build"
            tasks = ":ktor-java-modules-test:compileJava"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux)
    }
})
