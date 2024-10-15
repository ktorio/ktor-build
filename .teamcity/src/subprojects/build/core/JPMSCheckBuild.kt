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

    triggers {
        onChangeDefaultOrPullRequest()
    }

    cancelPreviousBuilds()
    steps {
        gradle {
            name = "Check JPMS build"
            tasks = ":ktor-java-modules-test:compileJava"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(Agents.OS.Linux)
    }
})
