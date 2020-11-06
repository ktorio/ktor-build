package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

class CoreBuild(private val osJdkEntry: OSJDKEntry) : BuildType({
    id("KtorMatrixCore_${osJdkEntry.osEntry.name}${osJdkEntry.jdkEntry.name}".toExtId())
    name = "${osJdkEntry.jdkEntry.name} on ${osJdkEntry.osEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
    vcs {
        root(VCSCore)
    }
    triggers {
        setupDefaultVcsTrigger()
    }
    steps {
        gradle {
            name = "Build and Run Tests"
            tasks = "jvmTest --no-parallel --continue --info"
            jdkHome = "%env.${osJdkEntry.jdkEntry.env}%"
        }
    }
    features {
        monitorPerformance()
    }
    requirements {
        require(os = osJdkEntry.osEntry.agentString, minMemoryMB = 7000)
    }
    if (osJdkEntry.osEntry == linux && osJdkEntry.jdkEntry == java11) {
        jvmBuild = this
    }
})

fun formatArtifacts(vararg artifacts: String): String {
    return artifacts.joinToString("\n")
}
