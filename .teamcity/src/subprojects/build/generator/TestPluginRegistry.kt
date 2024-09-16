package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*

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
            buildFile = "build.gradle.kts"
            jdkHome = "%env.JDK_11%"
        }
    }

    features {
        pullRequests {
            vcsRootExtId = VCSPluginRegistry.id.toString()
            provider = github {
                authType = token {
                    token = "%github.token%"
                }
                filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
            }
        }
        commitStatusPublisher {
            vcsRootExtId = VCSPluginRegistry.id.toString()

            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "%github.token%"
                }
            }
        }
    }

    triggers {
        vcs {}
    }
})
