package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

val windowsSoftware = """
                ${'$'}env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine")
                rm -fo C:\Tools\msys64\var\lib\pacman\db.lck
                C:\Tools\msys64\usr\bin\pacman -S --noconfirm --noprogressbar mingw-w64-x86_64-curl
                C:\Tools\msys64\usr\bin\pacman -S --noconfirm --noprogressbar mingw-w64-x86_64-ca-certificates
""".trimIndent()

val linuxSoftware = """
        sudo apt-get update
        sudo apt-get install -y libncurses5 libncursesw5 libtinfo5
        sudo apt-get install -y libcurl4-openssl-dev
""".trimIndent()

val macSoftware = """
    brew install curl ca-certificates
    brew reinstall libidn2
""".trimIndent()

class NativeBuild(private val osEntry: OSEntry, addTriggers: Boolean = true) : BuildType({
    id("KtorMatrixNative_${osEntry.name}_${osEntry.osArch ?: "x64"}".toId())
    name = "Native on ${osEntry.name} ${osEntry.osArch ?: "x64"}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.klib", "+:**/build/**/*.exe", "+:**/build/**/*.kexe")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    if (addTriggers) {
        triggers {
            onBuildTargetChanges(BuildTarget.Native(osEntry))
        }
    }

    steps {
        when (osEntry) {
            windows -> {
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
                        content = windowsSoftware.trimIndent()
                    }
                }
                defineTCPPortRange()
            }

            linux -> script {
                name = "Obtain Library Dependencies"
                scriptContent = linuxSoftware
            }

            macOS -> script {
                name = "Obtain Library Dependencies"
                scriptContent = macSoftware
            }
        }
        gradle {
            name = "Build and Run Tests"
            tasks = "${osEntry.testTaskName} --info"
            jdkHome = "%env.JDK_11%"
            buildFile = "build.gradle.kts"
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        require(os = osEntry.agentString, osEntry.osArch, minMemoryMB = 7000)
    }
    when (osEntry) {
        macOS -> nativeMacOSBuild = this
        windows -> nativeWindowsBuild = this
        linux -> nativeLinuxBuild = this
    }
})
