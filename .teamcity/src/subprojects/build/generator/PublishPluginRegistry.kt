package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.core.*

private const val initScriptCacheRedirector = "--init-script gradle/cache-redirector.init.gradle.kts"
internal const val PluginRegistryGradleParams = "--no-configuration-cache $initScriptCacheRedirector"

object PublishPluginRegistry : BuildType({
    id("KtorPluginRegistry")
    name = "Publish plugin registry"
    vcs {
        root(VCSPluginRegistry)
    }
    params {
        password("env.PUBLISHING_TOKEN", value = "%space.packages.publish.token%")
        extraGradleParams()
    }

    steps {
        gradle {
            name = "Build plugin registry"
            tasks = "kslExportToCbor"
            this.gradleParams = listOfNotNull(
                gradleParams,
                "--info",
                "--stacktrace",
                PluginRegistryGradleParams,
            ).joinToString(" ")
            jdkHome = Env.JDK_LTS
        }
        script {
            name = "Upload registry archive to Space"
            scriptContent = """
                tar -czf repository.tar.gz -C export .
                curl -i \
                  -H "Authorization: Bearer %env.PUBLISHING_TOKEN%" \
                  https://packages.jetbrains.team/files/p/ktor/files/plugin-registry/ \
                  --upload-file ./repository.tar.gz
            """
        }
    }

    triggers {
        vcs {
            branchFilter = BranchFilter.DefaultBranch
        }
    }
})
