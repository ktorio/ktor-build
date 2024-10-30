package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.*

object TestGeneratorBackEnd : BuildType({
    id("KtorGeneratorBackendVerify")
    name = "Test generator backend"
    params {
        password("env.SPACE_USERNAME", value = "%space.packages.apl.user%")
        password("env.SPACE_PASSWORD", value = "%space.packages.apl.token%")
    }
    vcs {
        root(VCSKtorGeneratorBackend)
    }

    steps {
        gradle {
            name = "Test generator backend"
            tasks = "test"
            buildFile = "build.gradle.kts"
            jdkHome = "%env.JDK_17_0%"
        }
    }

    defaultBuildFeatures(VCSKtorGeneratorBackend.id.toString())

    triggers {
        vcs {}
    }
})
