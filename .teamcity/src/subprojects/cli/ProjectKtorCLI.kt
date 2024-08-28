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

    artifactRules = "+:./build/**/ktor*"

    steps {
        script {
            name = "Create build directories"
            scriptContent = """
                mkdir -p build/darwin/amd64
                mkdir -p build/darwin/arm64
                mkdir -p build/linux/amd64
                mkdir -p build/linux/arm64
                mkdir -p build/windows/amd64
            """.trimIndent()
            workingDir = "."
        }

        buildFor("darwin", "amd64")
        buildFor("darwin", "arm64")
        buildFor("linux", "amd64")
        buildFor("linux", "arm64")
        buildFor("windows", "amd64")

        dockerCommand {
            name = "Run unit tests"

            commandType = other {
                subCommand = "run"
                commandArgs = goCommand("go test -v ./internal...")
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

private fun BuildSteps.buildFor(os: String, arch: String) {
    val ext = if (os == "windows") ".exe" else ""
    dockerCommand {
        name = "Build for $os $arch"

        commandType = other {
            subCommand = "run"
            commandArgs = goCommand(
                "go build -v -o build/$os/$arch/ktor$ext github.com/ktorio/ktor-cli/cmd/ktor",
                mapOf("GOOS" to os, "GOARCH" to arch, "CGO_ENABLED" to "0")
            )
        }
    }
}

private fun goCommand(command: String, env: Map<String, String> = mapOf()): String {
    val dockerEnv = buildString {
        for ((name, value) in env) {
            append("-e $name=$value")
            append(" ")
        }
    }

    val dockerPart = "--rm $dockerEnv -v .:/usr/src/app -w /usr/src/app golang:1.21 " +
            "/bin/bash -c \"git config --global --add safe.directory /usr/src/app; "
    return dockerPart + command + "\""
}