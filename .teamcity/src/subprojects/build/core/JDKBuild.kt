package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.Agents.OS
import subprojects.build.*

class JDKBuild(
    private val osJdkEntry: OSJDKEntry,
    private val addTriggers: Boolean = true,
) : BuildType({
    id("KtorMatrixCore_${osJdkEntry.id}".toId())
    name = "${osJdkEntry.jdkEntry.name} on ${osJdkEntry.os.id}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    if (addTriggers) {
        triggers {
            onBuildTargetChanges(BuildTarget.JVM)
        }
    }

    steps {
        when (osJdkEntry.os) {
            OS.Windows -> {
                script {
                    name = "Obtain Library Dependencies"
                    scriptContent = windowsSoftware
                }
                defineTCPPortRange()
            }
            OS.Linux -> {
                script {
                    name = "Obtain Library Dependencies"
                    scriptContent = linuxSoftware
                }
            }
            OS.MacOS -> {
                script {
                    name = "Obtain Library Dependencies"
                    scriptContent = macSoftware
                }
            }
        }

        gradle {
            name = "Build and Run Tests"
            tasks = "cleanJvmTest jvmTest --no-parallel --continue --no-daemon -s -Ptest.jdk=${osJdkEntry.jdkEntry.version}"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(OS.Linux)
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
