package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

class JDKBuild(
    private val osJdkEntry: OSJDKEntry,
    private val addTriggers: Boolean = true,
) : BuildType({
    id("KtorMatrixCore_${osJdkEntry.osEntry.id}${osJdkEntry.jdkEntry.name}".toId())
    name = "${osJdkEntry.jdkEntry.name} on ${osJdkEntry.osEntry.id}"
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
            tasks = "cleanJvmTest jvmTest --no-parallel --continue --no-daemon -s"
            jdkHome = "%env.${osJdkEntry.jdkEntry.env}%"
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(linux)
    }
    if (osJdkEntry.osEntry == linux && osJdkEntry.jdkEntry == javaLTS) {
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
