package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*

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
            branchFilter = BranchFilter.PullRequest
        }
    }
})
