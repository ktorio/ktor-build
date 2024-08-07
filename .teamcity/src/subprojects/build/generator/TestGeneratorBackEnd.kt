package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import subprojects.VCSKtorGeneratorBackend

object TestGeneratorBackEnd : BuildType({
    id("KtorGeneratorBackendVerify")
    name = "Test generator backend"
    params {
        password("env.SPACE_USERNAME", value = "%space.packages.apl.user%")
        password("env.SPACE_PASSWORD", value = "%space.packages.apl.token%")
    }
    vcs {
        root(VCSKtorGeneratorBackend)
    }

    steps {
        gradle {
            name = "Test generator backend"
            tasks = "test"
            buildFile = "build.gradle.kts"
            jdkHome = "%env.JDK_11%"
        }
    }

    features {
        pullRequests {
            vcsRootExtId = VCSKtorGeneratorBackend.id.toString()
            provider = github {
                authType = token {
                    token = "%github.token%"
                }
                filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
            }
        }
        commitStatusPublisher {
            vcsRootExtId = VCSKtorGeneratorBackend.id.toString()

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