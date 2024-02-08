package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.nodeJS
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import subprojects.VCSKtorGeneratorWebsite
import subprojects.VCSPluginRegistry
import subprojects.build.defaultBuildFeatures
import subprojects.build.java11
import subprojects.onChangeAllBranchesTrigger

object ProjectGenerator : Project({
    id("ProjectKtorGenerator")
    name = "Project Generator"
    description = "Code for start.ktor.io"
    params {
        password("env.PUBLISHING_TOKEN", value = "%space.packages.publish.token%")
    }

    buildType {
        id("KtorPluginRegistry")
        name = "Publish plugin registry"
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

        triggers {
            vcs {
                branchFilter = "+:<default>"
            }
        }
    }

    buildType {
        id("KtorPluginRegistryVerify")
        name = "Test plugin registry"
        vcs {
            root(VCSPluginRegistry)
        }

        steps {
            gradle {
                name = "Test plugin registry"
                tasks = "resolvePlugins detekt test buildRegistry"
                buildFile = "build.gradle.kts"
                jdkHome = "%env.${java11.env}%"
            }
        }

        defaultBuildFeatures(VCSPluginRegistry.id.toString())

        triggers {
            onChangeAllBranchesTrigger()
        }
    }

    buildType {
        id("KtorGeneratorWebsite_Test")
        name = "Test generator website"

        vcs {
            root(VCSKtorGeneratorWebsite, "+:.=>ktor-generator-website")
        }

        steps {
            nodeJS {
                name = "Node.js test"
                shellScript = """
                    npm ci
                    npm run lint
                """.trimIndent()
            }
        }

        defaultBuildFeatures(VCSKtorGeneratorWebsite.id.toString())

        triggers {
            onChangeAllBranchesTrigger()
        }
    }

})