package subprojects.build.core

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

    steps {
        gradle {
            name = "Check JPMS build"
            tasks = ":ktor-java-modules-test:compileJava"
            jdkHome = "%env.${javaLTS.env}%"
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(linux)
    }
})
