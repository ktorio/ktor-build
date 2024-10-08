package subprojects.plugins

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object ProjectGradlePlugin : Project({
    id("ProjectKtorGradlePlugin")
    name = "Ktor Gradle Plugin"
    description = "Publish Ktor Gradle Plugin"

    params {
        defaultTimeouts()
        password("env.PUBLISHING_USER", value = "%space.packages.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.secret%")
        param("env.PUBLISHING_URL", value = "%space.packages.url%")
        param("env.BUILD_NUMBER", value = "%build.counter%")
    }

    buildType {
        id("PublishGradlePlugin")
        name = "Build and publish Ktor Gradle Plugin EAP to Gradle Plugin Portal"

        vcs {
            root(VCSKtorBuildPlugins)
        }

        steps {
            gradle {
                name = "Publish"
                tasks = ":plugin:publishPlugins"
                buildFile = "build.gradle.kts"
                gradleParams = "-PversionSuffix=eap-%build.counter%"
            }
        }
    }

    buildType {
        id("PublishGradlePluginRelease")
        name = "Build and publish Ktor Gradle Plugin Release to Gradle Plugin Portal"

        vcs {
            root(VCSKtorBuildPlugins)
        }

        steps {
            gradle {
                name = "Publish"
                tasks = ":plugin:publishPlugins"
                buildFile = "build.gradle.kts"
            }
        }
    }

    buildType {
        id("PublishGradlePluginBeta")
        name = "Build and publish Ktor Gradle Plugin Beta to Gradle Plugin Portal"

        vcs {
            root(VCSKtorBuildPlugins)
        }

        steps {
            gradle {
                name = "Publish"
                tasks = ":plugin:publishPlugins"
                buildFile = "build.gradle.kts"
                gradleParams = "-PversionSuffix=beta-%build.counter%"
            }
        }
    }

    buildType {
        id("PublishGradleEAPPlugin")
        name = "Build and publish Ktor Gradle EAP Plugin to Space Packages"

        vcs {
            root(VCSKtorBuildPlugins)
            branchFilter = BranchFilter.DefaultBranch
        }

        triggers {
            nightlyEAPBranchesTrigger()
        }

        steps {
            gradle {
                name = "Publish to Space Packages"
                tasks = ":plugin:publish"
                buildFile = "build.gradle.kts"
                gradleParams = "-Pspace -PversionSuffix=eap-%build.counter%"
            }
        }
    }

    buildType {
        id("TestGradlePlugin")
        name = "Test Ktor Gradle Plugin"

        vcs {
            root(VCSKtorBuildPlugins)
        }

        triggers {
            onChangeAllBranchesTrigger()
        }

        features {
            githubCommitStatusPublisher(VCSKtorBuildPlugins.id.toString())
        }

        steps {
            gradle {
                name = "Run tests"
                tasks = ":plugin:test test"
                buildFile = "build.gradle.kts"
            }
        }
    }
})
