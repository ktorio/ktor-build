package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
import subprojects.*
import subprojects.build.*

internal fun BuildType.setupBuildFeatures() {
    features {
        monitorPerformance()

        pullRequests {
            vcsRootExtId = VCSCore.id.toString()
            provider = github {
                authType = token {
                    token = VCSToken
                }
                filterTargetBranch = """
            +:*
            -:pull/*
        """.trimIndent()
                filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
            }
        }

        commitStatusPublisher {
            vcsRootExtId = VCSCore.id.toString()

            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = VCSToken
                }
            }
        }
    }
}