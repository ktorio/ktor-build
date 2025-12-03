package subprojects.plugins

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.Agents.OS
import subprojects.build.*
import subprojects.release.*
import subprojects.release.publishing.*

private const val PublishCompilerPlugin = ":ktor-compiler-plugin:publish"
private const val PublishPluginsToPluginPortal = ":plugin:publishPlugins $PublishCompilerPlugin"
private const val PublishPluginsToSpace = ":plugin:publish $PublishCompilerPlugin"

object ProjectGradlePlugin : Project({
    id("ProjectKtorGradlePlugin")
    name = "Ktor Gradle Plugin"
    description = "Publish Ktor Gradle Plugin"

    vcsRoot(VCSKtorBuildPlugins)
    vcsRoot(VCSKtorBuildPluginsEAP)

    params {
        defaultGradleParams()
        password("env.PUBLISHING_USER", value = "%space.packages.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.secret%")
        param("env.PUBLISHING_URL", value = "%space.packages.url%")
        param("env.BUILD_NUMBER", value = "%build.counter%")
        param("env.GIT_BRANCH", value = "%teamcity.build.branch%")
    }

    publishToPluginPortal(
        id = "PublishGradlePlugin",
        name = "Build and publish Ktor Gradle Plugin EAP to Gradle Plugin Portal",
        gradleParams = "-PversionSuffix=eap-%build.counter%",
    )

    publishToPluginPortal(
        id = "PublishGradlePluginRelease",
        name = "Build and publish Ktor Gradle Plugin Release to Gradle Plugin Portal",
    )

    publishToPluginPortal(
        id = "PublishGradlePluginBeta",
        name = "Build and publish Ktor Gradle Plugin Beta to Gradle Plugin Portal",
        gradleParams = "-PversionSuffix=beta-%build.counter%",
    )

    buildType {
        id("PublishGradleEAPPlugin")
        name = "Build and publish Ktor Gradle EAP Plugin to Space Packages"

        vcs {
            root(VCSKtorBuildPluginsEAP)
        }

        triggers {
            nightlyEAPBranchesTrigger()
        }

        steps {
            gradle {
                name = "Publish to Space Packages"
                tasks = PublishPluginsToSpace
                gradleParams = "-Pspace -PversionSuffix=eap-%build.counter%"
                jdkHome = Env.JDK_LTS
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
            onChangeDefaultOrPullRequest()
        }

        features {
            githubCommitStatusPublisher()
        }

        steps {
            gradle {
                name = "Run tests"
                tasks = ":plugin:test test"
                jdkHome = Env.JDK_LTS
            }
        }
    }
})

private fun Project.publishToPluginPortal(
    id: String,
    name: String,
    gradleParams: String? = null,
): BuildType = buildType {
    id(id)
    this.name = name

    vcs {
        root(VCSKtorBuildPlugins)
    }

    requirements {
        agent(OS.Linux, hardwareCapacity = Agents.ANY)
    }

    params {
        publishingCredentials()
    }

    steps {
        prepareKeyFile(OS.Linux.name)
        gradle {
            this.name = "Publish"
            tasks = PublishPluginsToPluginPortal
            this.gradleParams = listOfNotNull(
                gradleParams,
                GPG_DEFAULT_GRADLE_ARGS,
                "--no-configuration-cache",
                "--info",
                "--stacktrace",
            ).joinToString(" ")
            jdkHome = Env.JDK_LTS
        }
        cleanupKeyFile(OS.Linux.name)
    }
}
