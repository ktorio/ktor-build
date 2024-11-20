package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
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
            tasks = "resolvePlugins detekt test buildRegistry"
            jdkHome = "%env.JDK_11%"
        }
    }

    defaultBuildFeatures(VCSPluginRegistry.id.toString())

    triggers {
        vcs {}
    }
})
