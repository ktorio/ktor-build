package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*

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
            branchFilter = BranchFilter.DefaultBranch
        }
    }
})
