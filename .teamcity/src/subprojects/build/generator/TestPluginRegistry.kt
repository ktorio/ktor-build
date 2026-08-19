package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.extraGradleParams

object TestPluginRegistry : BuildType({
    id("KtorPluginRegistryVerify")
    name = "Test plugin registry"
    vcs {
        root(VCSPluginRegistry)
    }

    params {
        extraGradleParams()
    }

    steps {
        gradle {
            name = "Test plugin registry"
            tasks = ":test:test"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
    }

    triggers {
        onChangeDefaultOrPullRequest()
    }
})
