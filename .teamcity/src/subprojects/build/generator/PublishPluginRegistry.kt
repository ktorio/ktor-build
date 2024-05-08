package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import subprojects.VCSPluginRegistry

object PublishPluginRegistry : BuildType({
    id("KtorPluginRegistry")
    name = "Publish plugin registry"
    vcs {
        root(VCSPluginRegistry)
    }
    params {
        password("env.PUBLISHING_TOKEN", value = "%space.packages.publish.token%")
    }

    steps {
        gradle {
            name = "Build plugin registry"
            tasks = "packageRegistry"
            buildFile = "build.gradle.kts"
            jdkHome = "%env.JDK_11%"
        }
        script {
            name = "Upload registry archive to Space"
            scriptContent = """
                curl -i \
                  -H "Authorization: Bearer %env.PUBLISHING_TOKEN%" \
                  https://packages.jetbrains.team/files/p/ktor/files/plugin-registry/ \
                  --upload-file build/distributions/registry.tar.gz
            """
        }
    }

    triggers {
        vcs {
            branchFilter = "+:<default>"
        }
    }
})