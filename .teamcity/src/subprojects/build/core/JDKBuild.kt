package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.Agents.OS
import subprojects.build.*

class JDKBuild(
    private val osJdkEntry: OSJDKEntry,
) : BuildType({
    id("KtorMatrixCore_${osJdkEntry.id}".toId())
    name = "${osJdkEntry.jdkEntry.name} on ${osJdkEntry.os.id}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    cancelPreviousBuilds()

    params {
        param("env.KTOR_RUST_COMPILATION", "true")
    }

    steps {
        if (osJdkEntry.os == OS.Windows) {
            defineTCPPortRange()
        }

        gradle {
            name = "Build and Run Tests"
            tasks = "cleanJvmTest jvmTest --continue -Ptest.jdk=${osJdkEntry.jdkEntry.version}"
            jdkHome = Env.JDK_LTS
            enableStacktrace = true
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
