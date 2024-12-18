package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.Agents.OS
import subprojects.build.*

val windowsSoftware = """
    ${'$'}env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine")
    rm -fo C:\Tools\msys64\var\lib\pacman\db.lck
    C:\Tools\msys64\usr\bin\pacman -S --noconfirm --noprogressbar mingw-w64-x86_64-curl
    C:\Tools\msys64\usr\bin\pacman -S --noconfirm --noprogressbar mingw-w64-x86_64-ca-certificates
""".trimIndent()

val linuxSoftware = """
    sudo apt-get update
    sudo apt-get install -y libcurl4-openssl-dev libncurses-dev
""".trimIndent()

val macSoftware = """
    brew install curl ca-certificates
    brew reinstall libidn2
""".trimIndent()

class NativeBuild(private val entry: NativeEntry, addTriggers: Boolean = true) : BuildType({
    id("KtorMatrixNative_${entry.id}".toId())
    name = "${entry.name} ${entry.arch}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.klib", "+:**/build/**/*.exe", "+:**/build/**/*.kexe")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    if (addTriggers) {
        triggers {
            onBuildTargetChanges(BuildTarget.Native(entry.os))
        }
    }

    steps {
        when (entry.os) {
            OS.Windows -> {
                powerShell {
                    name = "Remove git from PATH"
                    scriptMode = script {
                        content = """
                            ${'$'}oldPath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
                            ${'$'}newPath = (${'$'}oldPath.Split(';') | Where-Object { ${'$'}_ -ne "C:\Program Files\Git\usr\bin" }) -join ';'
                            [Environment]::SetEnvironmentVariable('Path', ${'$'}newPath, 'Machine')
                        """.trimIndent()
                    }
                }
                powerShell {
                    name = "Get dependencies and environment ready"
                    scriptMode = script {
                        content = windowsSoftware
                    }
                }
                defineTCPPortRange()
            }

            OS.Linux -> script {
                name = "Obtain Library Dependencies"
                scriptContent = linuxSoftware
            }

            OS.MacOS -> script {
                name = "Obtain Library Dependencies"
                scriptContent = macSoftware
            }
        }
        gradle {
            name = "Build and Run Tests"
            tasks = "${entry.testTasks} --info"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(entry)
    }
})
