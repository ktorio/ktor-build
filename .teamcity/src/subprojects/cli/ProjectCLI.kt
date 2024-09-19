package subprojects.cli

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*

object ProjectCLI : Project({
    id("ProjectKtorCLI")
    name = "Ktor CLI"

    buildType(BuildCLI)
    buildType(PackMsiInstaller)
    buildType(ReleaseGithub)
})

object ReleaseGithub: BuildType({
    id("ReleaseGithubCLI")
    name = "Create GitHub release"

    vcs {
        root(VCSKtorCLI)
    }

    dependencies {
        artifacts(BuildCLI.id!!) {
            artifactRules = "./build => ./build"
        }

        artifacts(PackMsiInstaller.id!!) {
            artifactRules = "./*.msi => ./build/windows/amd64/"
        }
    }

    steps {
        script {
            name = "Create GitHub release"
            scriptContent = """
python3 -m pip install requests

python3 <<EOF
import datetime
import sys
from pathlib import Path
import requests
import re

def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)

owner = "ktorio"
repo = "ktor-cli"
token = "$VCSToken"
tag = "${'$'}(git describe --tags --contains --abbrev=7 2>/dev/null)"

if not re.match(r"\d+\.\d+\.\d+", tag):
    eprint(f"Expected tag in the format *.*.*, got '{tag}'")
    exit(1)

print(f"Releasing {tag}...")

response = requests.post(f"https://api.github.com/repos/{owner}/{repo}/releases", headers={
    "Accept": "application/vnd.github+json",
    "Authorization": f"Bearer {token}",
    "X-GitHub-Api-Version": "2022-11-28",
}, json={
    "tag_name": f"{tag}",
    "name": f"{tag}",
    "body": f"> Published {datetime.datetime.now().strftime('%d %B %Y')}",
    "draft": False,
    "prerelease": False,
    "generate_release_notes": False
})

if response.status_code != 201:
    eprint(f"GitHub release creation failed with {response.status_code} status, expected 201")
    eprint(response.text)
    sys.exit(1)

json_response = response.json()
release_id = json_response["id"]
release_url = json_response["html_url"]

print(f"Release {release_id} has been created")
print("Uploading assets...")

assets = {
    f"ktor_linux_amd64": 'build/linux/amd64/ktor',
    f"ktor_linux_arm64": 'build/linux/arm64/ktor',
    f"ktor_macos_amd64": 'build/darwin/amd64/ktor',
    f"ktor_macos_arm64": 'build/darwin/arm64/ktor',
    f"ktor.exe": 'build/windows/amd64/ktor.exe',
    f"ktor_installer.msi": 'build/windows/amd64/ktor-installer.msi',
}

for name, filepath in assets.items():
    response = requests.post(
        f"https://uploads.github.com/repos/{owner}/{repo}/releases/{release_id}/assets?name={name}", headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/octet-stream",
        }, data=Path(filepath).read_bytes())

    if response.status_code != 201:
        eprint(f"Assets upload failed with status {response.status_code}, expected 201")
        eprint(response.text)
        sys.exit(1)

    print(f"Asset {name} has been uploaded")

print(f"Release {release_url} has been successfully created")

EOF
            """.trimIndent()
            workingDir = "."
        }
    }

    requirements {
        require(os = linux.agentString)
    }
})

object PackMsiInstaller : BuildType({
    id("PackMsiInstallerCLI")
    name = "Pack MSI installer"

    vcs {
        root(VCSKtorCLI)
    }

    artifactRules = """
        +:./*.msi
        +:./checksum.txt
    """.trimIndent()

    dependencies {
        artifacts(BuildCLI.id!!) {
            artifactRules = "./build/windows/amd64/ktor.exe => ./build/"
        }
    }

    steps {
        script {
            name = "Install Wix toolset"
            scriptContent = """
                dotnet tool install --tool-path=wixToolset wix               
                .\wixToolset\wix.exe extension add -g WixToolset.UI.wixext
            """.trimIndent()
            workingDir = "."
        }

        script {
            name = "Pack installer"
            scriptContent = """
                powershell -file packInstaller.ps1 -wixExe "wixToolset\wix.exe" -toolPath "build\ktor.exe" -outPath "ktor-installer.msi"
            """.trimIndent()
            workingDir = "."
        }
    }

    requirements {
        require(os = windows.agentString)
    }
})

object BuildCLI: BuildType({
    id("BuildCLI")
    name = "Build"

    vcs {
        root(VCSKtorCLI)
    }

    artifactRules = "+:./build => ./build"

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
        require(os = linux.agentString)
    }

    features {
        perfmon {
        }

        githubPullRequestsLoader(VCSKtorCLI.id.toString())
        githubCommitStatusPublisher(VCSKtorCLI.id.toString())
    }
})

private fun BuildSteps.buildFor(os: String, arch: String) {
    val ext = if (os == "windows") ".exe" else ""
    dockerCommand {
        name = "Build for $os $arch"

        commandType = other {
            subCommand = "run"
            commandArgs = goCommand(
                "go build -v -ldflags=-X\\ main.Version=\$(git describe --tags --contains --always --abbrev=7)" +
                    " -o build/$os/$arch/ktor$ext github.com/ktorio/ktor-cli/cmd/ktor",
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
