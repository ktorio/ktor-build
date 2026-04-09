package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object TestPluginRegistry : BuildType({
    id("KtorPluginRegistryVerify")
    name = "Test plugin registry"
    vcs {
        root(VCSPluginRegistry)
    }

    steps {
        gradle {
            name = "Test plugin registry"
            tasks = ":test:kotest"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    triggers {
        onChangeDefaultOrPullRequest()
    }
})
