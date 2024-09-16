package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

class CoreBuild(private val osJdkEntry: OSJDKEntry) : BuildType({
    id("KtorMatrixCore_${osJdkEntry.osEntry.name}${osJdkEntry.jdkEntry.name}".toId())
    name = "${osJdkEntry.jdkEntry.name} on ${osJdkEntry.osEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
    vcs {
        root(VCSCore)
    }
    steps {
        when (osJdkEntry.osEntry) {
            windows -> {
                script {
                    name = "Obtain Library Dependencies"
                    scriptContent = windowsSoftware
                }
                defineTCPPortRange()
            }
            linux -> {
                script {
                    name = "Obtain Library Dependencies"
                    scriptContent = linuxSoftware
                }
            }
            macOS -> {
                script {
                    name = "Obtain Library Dependencies"
                    scriptContent = macSoftware
                }
            }
        }

        gradle {
            name = "Build and Run Tests"
            buildFile = "build.gradle.kts"
            tasks = "cleanJvmTest jvmTest --no-parallel --continue --no-daemon -s"
            jdkHome = "%env.${osJdkEntry.jdkEntry.env}%"
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
