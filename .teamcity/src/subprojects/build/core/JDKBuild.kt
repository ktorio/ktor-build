package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.Agents.OS
import subprojects.build.*
import subprojects.build.gradle.*

class JDKBuild(
    private val osJdkEntry: OSJDKEntry,
    private val repo: KtorRepository,
    private val jvmCompileBuild: BuildType,
) : BuildType({
    id(repo.id("KtorMatrixCore_${osJdkEntry.id}".toId()))
    name = repo.name("${osJdkEntry.jdkEntry.name} on ${osJdkEntry.os.id}")
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact, testServerArtifact)

    vcs {
        root(repo.vcsRoot)
    }

    params {
        extraGradleParams()
    }

    useGradleEnvironment(osJdkEntry, GradleUserHome.Light)
    cancelPreviousBuilds()
    enableRustForRelevantChanges(osJdkEntry.os)

    dependencies {
        snapshot(jvmCompileBuild) {
            reuseBuilds = ReuseBuilds.SUCCESSFUL
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        artifacts(jvmCompileBuild) {
            buildRule = sameChainOrLastFinished()
            artifactRules = formatArtifacts(
                "+:jvm-build.zip!** => .",
                // Restored by the configuration cache together with its entries
                *configurationCacheRules.map { "-:jvm-build.zip!$it/**" }.toTypedArray(),
            )
        }
    }

    steps {
        if (osJdkEntry.os == OS.Windows) {
            defineTCPPortRange()
        }

        if (osJdkEntry.os == OS.Linux) {
            aptInstall("pulseaudio", name = "Install pulseaudio for WebRTC tests")
        }

        gradle {
            name = "Run Tests"
            tasks = "jvmTest"
            gradleParams = "--continue -Ptest.jdk=${osJdkEntry.jdkEntry.version} $GradleParams"
            jdkHome = Env.JDK_LTS
            enableStacktrace = true
        }
    }

    defaultBuildFeatures()
})

fun BuildSteps.defineTCPPortRange() {
    script {
        name = "Widen dynamic TCP port range"
        scriptContent = "netsh int ipv4 set dynamicport tcp start=1024 num=64510"
    }
}

fun formatArtifacts(vararg artifacts: String): String {
    return artifacts.joinToString("\n")
}
