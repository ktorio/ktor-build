package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

class JPMSCheckBuild(private val repo: KtorRepository) : BuildType({
    id(repo.id("JPMSCheck".toId()))
    name = repo.name("Check JPMS")

    vcs {
        root(repo.vcsRoot)
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
