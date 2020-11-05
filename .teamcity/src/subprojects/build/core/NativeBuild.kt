package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

class NativeBuild(private val osEntry: OSEntry) : BuildType({
    id("KtorMatrixNative_${osEntry.name}".toExtId())
    name = "Native on ${osEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.klib")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
    vcs {
        root(VCSCore)
    }
    triggers {
        setupDefaultVcsTrigger()
    }
    steps {
        if (osEntry == windows) {
            script {
                name = "Get dependencies and environment ready"
                scriptContent = """
                C:\Tools\msys64\usr\bin\pacman -S --noconfirm --noprogressbar mingw-w64-x86_64-curl
                C:\Tools\msys64\usr\bin\pacman -S mingw-w64-x86_64-ca-certificates
            """.trimIndent()
            }
        }
        gradle {
            name = "Build and Run Tests"
            tasks = "${osEntry.taskName} --info"
            jdkHome = "%env.JDK_11%"
        }
    }
    features {
        monitorPerformance()
    }
    requirements {
        require(os = osEntry.agentString, minMemoryMB =  7000)
    }
    when (osEntry) {
        macOS -> nativeMacOSBuild = this
        windows -> nativeWindowsBuild = this
        linux -> nativeLinuxBuild = this
    }
})
