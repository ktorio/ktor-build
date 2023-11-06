package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.toExtId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.powerShell
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSCore
import subprojects.build.*
import subprojects.release.nativeLinuxBuild
import subprojects.release.nativeMacOSBuild
import subprojects.release.nativeWindowsBuild

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

class NativeBuild(private val osEntry: OSEntry) : BuildType({
    id("KtorMatrixNative_${osEntry.name}".toExtId())
    name = "Native on ${osEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.klib", "+:**/build/**/*.exe", "+:**/build/**/*.kexe")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
    vcs {
        root(VCSCore)
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
        require(os = osEntry.agentString, minMemoryMB = 7000)
    }
    when (osEntry) {
        macOS -> nativeMacOSBuild = this
        windows -> nativeWindowsBuild = this
        linux -> nativeLinuxBuild = this
    }
})
