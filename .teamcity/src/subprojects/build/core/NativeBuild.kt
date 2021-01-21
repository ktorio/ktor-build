package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

val libcurlSoftware = """
                rm -fo C:\Tools\msys64\var\lib\pacman\db.lck 
                C:\Tools\msys64\usr\bin\pacman -S --noconfirm --noprogressbar mingw-w64-x86_64-curl
                C:\Tools\msys64\usr\bin\pacman -S --noconfirm --noprogressbar mingw-w64-x86_64-ca-certificates
""".trimIndent()

class NativeBuild(private val osEntry: OSEntry) : BuildType({
    id("KtorMatrixNative_${osEntry.name}".toExtId())
    name = "Native on ${osEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.klib")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
    vcs {
        root(VCSCore)
    }
    triggers {
        onChangeAllBranchesTrigger()
    }
    steps {
        if (osEntry == windows) {
            powerShell {
                name = "Get dependencies and environment ready"
                scriptMode = script {
                    content = libcurlSoftware.trimIndent()
                }
            }
            defineTCPPortRange()
        }
        gradle {
            name = "Build and Run Tests"
            tasks = "${osEntry.taskName} --info"
            jdkHome = "%env.JDK_11%"
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
