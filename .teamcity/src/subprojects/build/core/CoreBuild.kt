package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
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
        onChangeAllBranchesTrigger()
    }
    steps {
        if (osJdkEntry.osEntry == windows) {
            defineTCPPortRange()
        } else if (osJdkEntry.osEntry == linux) {
            script {
                name = "Obtain Library Dependencies"
                scriptContent = libSoftware
            }
        }
        gradle {
            name = "Build and Run Tests"
            tasks = "cleanJvmTest jvmTest --no-parallel --continue --info"
            jdkHome = "%env.${osJdkEntry.jdkEntry.env}%"
            buildFile = "build.gradle"
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        require(os = osJdkEntry.osEntry.agentString, minMemoryMB = 7000)
    }
    if (osJdkEntry.osEntry == linux && osJdkEntry.jdkEntry == java11) {
        jvmBuild = this
    }
})

fun BuildSteps.defineTCPPortRange() {
    script {
        scriptContent = "netsh int ipv4 set dynamicport tcp start=1024 num=64510"
    }
}

fun formatArtifacts(vararg artifacts: String): String {
    return artifacts.joinToString("\n")
}
