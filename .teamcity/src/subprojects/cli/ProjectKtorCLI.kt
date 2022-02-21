package subprojects.cli

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*

object ProjectKtorCLI : Project({
    id("ProjectKtorCLI")
    name = "Ktor CLI"

    val platforms = listOf(linux, macOS, windows, macOSArm)
    val builds = platforms.map(::buildCLI)

    buildType {
        id("BuildAllCLI")
        name = "Run all CLI builds"

        vcs {
            root(VCSKtorCLI)
        }

        artifactRules = "+:**/build/**/*.kexe"

        triggers {
            onChangeAllBranchesTrigger()
        }

        features {
            perfmon {
            }

            githubPullRequestsLoader(VCSKtorCLI.id.toString())
            githubCommitStatusPublisher(VCSKtorCLI.id.toString())
        }

        dependencies {
            builds.mapNotNull {it.id }.forEach {
                snapshot(it) {
                }
            }
        }
    }
})

fun Project.buildCLI(os: OSEntry): BuildType = buildType {
    id("BuildCLIon${os.name}")
    name = "Build CLI on ${os.name}"

    vcs {
        root(VCSKtorCLI)
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
