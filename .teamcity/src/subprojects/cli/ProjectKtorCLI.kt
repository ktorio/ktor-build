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

    val platforms = listOf(linux)
    platforms.forEach(::buildCLI)
})

fun Project.buildCLI(os: OSEntry): BuildType = buildType {
    id("BuildCLIon${os.name}")
    name = "Build CLI on ${os.name}"

    vcs {
        root(VCSKtorCLI)
    }

    artifactRules = "+:./ktor"

    steps {
        dockerCommand {
            name = "Build application"

            commandType = other {
                subCommand = "run"
                commandArgs = goCommand("go build -buildvcs=false -v github.com/ktorio/ktor-cli/cmd/ktor")
            }
        }

        dockerCommand {
            name = "Run unit tests"

            commandType = other {
                subCommand = "run"
                commandArgs = goCommand("go test -buildvcs=false -v ./internal...")
            }
        }
    }

    requirements {
        require(os = os.agentString)
    }

    features {
        perfmon {
        }

        githubPullRequestsLoader(VCSKtorCLI.id.toString())
        githubCommitStatusPublisher(VCSKtorCLI.id.toString())
    }
}

private fun goCommand(command: String): String {
    val dockerPart = "--rm -v .:/usr/src/app -w /usr/src/app golang:1.21 "
    return dockerPart + command
}