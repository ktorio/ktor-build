package subprojects.plugins

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import subprojects.VCSKtorBuildPlugins
import subprojects.VCSKtorCLI
import subprojects.build.*
import subprojects.build.core.CodeStyleVerify
import subprojects.build.core.require
import subprojects.onChangeAllBranchesTrigger

object ProjectKtorGradlePlugin : Project({
    id("ProjectKtorGradlePlugin")
    name = "Ktor Gradle Plugin"
    description = "Publish Ktor Gradle Plugin"

    params {
        password("env.GRADLE_PUBLISH_KEY","%gradle.publish.key%" )
        password("env.GRADLE_PUBLISH_SECRET","%gradle.publish.secret%" )
    }

    buildType {
        id("PublishGradlePlugin")
        name = "Build and publish Ktor Gradle Plugin"

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
})

fun Project.buildCLI(os: OSEntry): BuildType = buildType {
    id("BuildGradlePluginOn${os.name}")
    name = "Build Gradle Plugin on ${os.name}"

    vcs {
        root(VCSKtorBuildPlugins)
    }

    artifactRules = "+:**/build/**/*.kexe"

    steps {
        gradle {
            name = "Build binary"
            tasks = os.binaryTaskName
            buildFile = "build.gradle.kts"
        }

        gradle {
            name = "Run tests"
            tasks = os.testTaskName
            buildFile = "build.gradle.kts"
        }
    }

    requirements {
        require(os = os.agentString, minMemoryMB = 7000)
    }

    features {
        perfmon {
        }

        githubPullRequestsLoader(VCSKtorCLI.id.toString())
        githubCommitStatusPublisher(VCSKtorCLI.id.toString())
    }
}