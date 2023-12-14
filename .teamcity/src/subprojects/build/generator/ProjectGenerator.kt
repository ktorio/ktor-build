package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSPluginRegistry
import subprojects.build.java11

object ProjectGenerator : Project({
    id("ProjectKtorGenerator")
    name = "Project Generator"
    description = "Code for start.ktor.io"
    params {
        password("env.PUBLISHING_TOKEN", value = "%space.packages.publish.token%")
    }

    buildType {
        name = "Build plugin registry"
        vcs {
            root(VCSPluginRegistry)
        }
        steps {
            gradle {
                name = "Build plugin registry"
                tasks = "packageRegistry"
                buildFile = "build.gradle.kts"
                jdkHome = "%env.${java11.env}%"
            }
            script {
                name = "Push registry archive to Space"
                scriptContent = """
                    curl -i \
                      -H "Authorization: Bearer %env.PUBLISHING_TOKEN%" \
                      https://packages.jetbrains.team/files/p/ktor/files/plugin-registry/ \
                      --upload-file build/distributions/registry.tar.gz
                """
            }
        }
    }

})