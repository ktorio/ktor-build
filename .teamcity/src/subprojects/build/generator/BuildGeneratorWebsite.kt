package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.nodeJS
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import subprojects.VCSKtorGeneratorWebsite

object BuildGeneratorWebsite : BuildType({
    id("KtorGeneratorWebsite_Test")
    name = "Build generator website"
    params {
        password("env.PUBLISHING_TOKEN", value = "%space.packages.publish.token%")
    }

    vcs {
        root(VCSKtorGeneratorWebsite)
    }

    steps {
        nodeJS {
            name = "Node.js test + build"
            shellScript = """
                npm ci
                npm run lint
                npm run build --verbose
            """.trimIndent()
        }
        script {
            name = "Upload test archive to Space"
            scriptContent = """
               tar -zcvf website.tar.gz -C build .
               curl -i \
                  -H "Authorization: Bearer %env.PUBLISHING_TOKEN%" \
                  https://packages.jetbrains.team/files/p/ktor/files/ktor-generator-website/test/ \
                  --upload-file website.tar.gz
            """.trimIndent()
        }
    }

    features {
        pullRequests {
            vcsRootExtId = VCSKtorGeneratorWebsite.id.toString()
            provider = github {
                authType = token {
                    token = "%github.token%"
                }
                filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
            }
        }
        commitStatusPublisher {
            vcsRootExtId = VCSKtorGeneratorWebsite.id.toString()

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