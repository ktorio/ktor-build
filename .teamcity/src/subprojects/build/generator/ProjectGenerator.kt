package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.nodeJS
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import subprojects.VCSKtorGeneratorBackend
import subprojects.VCSKtorGeneratorWebsite
import subprojects.VCSPluginRegistry

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
                jdkHome = "%env.JDK_11%"
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
    }

    buildType {
        id("KtorGeneratorBackendVerify")
        name = "Test generator backend"
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
    }

    buildType {
        id("KtorGeneratorWebsite_Test")
        name = "Test generator website"

        vcs {
            root(VCSKtorGeneratorWebsite)
        }

        steps {
            nodeJS {
                name = "Node.js test"
                shellScript = """
                    npm install
                    npm ci
                    npm run lint
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
            vcs {}
        }
    }

})