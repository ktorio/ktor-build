package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*
import subprojects.release.publishing.*

class CoreBuild(private val osJdkEntry: OSJDKEntry) : BuildType({
    id("KtorMatrixCore_${osJdkEntry.osEntry.name}${osJdkEntry.jdkEntry.name}".toExtId())
    name = "${osJdkEntry.jdkEntry.name} on ${osJdkEntry.osEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact, producedGradleCacheArtifact, producedBuildArtifact)
    vcs {
        root(VCSCore)
    }
    triggers {
        setupDefaultVcsTrigger()
    }
    steps {
        gradle {
            name = "Assemble"
            tasks = "assemble --info"
            jdkHome = "%env.${osJdkEntry.jdkEntry.env}%"
        }
        gradle {
            name = "Build and Run Tests"
            tasks = "clean jvmTest --no-parallel --continue --info"
            jdkHome = "%env.${osJdkEntry.jdkEntry.env}%"
        }
    }
    features {
        monitorPerformance()
    }
    requirements {
        require(os = osJdkEntry.osEntry.agentString, minMemoryDB = 7000)
    }
    if (osJdkEntry.osEntry == linux && osJdkEntry.jdkEntry == java11) {
        jvmBuild = this
    }
})

fun formatArtifacts(vararg artifacts: String): String {
    return artifacts.joinToString("\n")
}
