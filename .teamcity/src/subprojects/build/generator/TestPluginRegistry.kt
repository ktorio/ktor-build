package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import subprojects.VCSPluginRegistry

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
        vcs {
            branchFilter = """
                +:refs/pull/*
            """.trimIndent()
        }
    }
})