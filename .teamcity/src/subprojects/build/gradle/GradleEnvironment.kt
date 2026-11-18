package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger.DAY.*
import subprojects.*
import subprojects.build.core.*
import kotlin.time.Duration.Companion.hours

/** Versions of the Gradle user home published by [GradleEnvironment]. */
enum class GradleUserHome(internal val artifact: String) {
    /** Without dependencies of native targets. It is enough for builds that don't run native tasks. */
    Light("gradle-user-home-light.tar.zst"),

    /** With dependencies of all targets. */
    Full("gradle-user-home-full.tar.zst"),
}

// The agent work directory has the same path on all agents with the same OS
private const val userHome = "%teamcity.agent.work.dir%/gradle-user-home"

/**
 * Publishes a warmed-up Gradle user home, so builds on fresh agents don't start with an empty one.
 * Only [versions] used by builds on the given [os] should be published.
 *
 * @see [useGradleEnvironment].
 */
class GradleEnvironment(
    val os: Agents.OS,
    arch: Agents.Arch,
    val versions: Set<GradleUserHome>,
) : BuildType({
    id("GradleEnvironment_${os.id}")
    name = "Gradle Environment (${os.id})"

    vcs {
        root(VCSCore)
        checkoutMode = CheckoutMode.ON_AGENT
        cleanCheckout = true
    }

    params {
        setupGradleParams()
    }

    steps {
        // zstd is needed to pack the user home, and macOS agents don't have it installed
        if (os == Agents.OS.MacOS) {
            script {
                name = "Install zstd"
                scriptContent = bashScript(
                    """
                    command -v zstd > /dev/null || HOMEBREW_NO_AUTO_UPDATE=1 brew install zstd
                    """
                )
            }
        }

        script {
            name = "Clean Gradle user home"
            scriptContent = bashScript(
                """
                rm -rf "${'$'}GRADLE_USER_HOME"
                """
            )
        }

        if (GradleUserHome.Light in versions) {
            resolveDependencies(native = false)

            gradle {
                name = "Configure all targets"
                tasks = "jvmTest"
                gradleParams = "--dry-run"
                jdkHome = Env.JDK_LTS
            }

            packGradleUserHome(GradleUserHome.Light)
        }

        if (GradleUserHome.Full in versions) {
            resolveDependencies(native = true)
            packGradleUserHome(GradleUserHome.Full)
        }
    }

    artifactRules = formatArtifacts(*versions.map { it.artifact }.toTypedArray())
    publishArtifacts = PublishMode.SUCCESSFUL

    triggers {
        vcs {
            branchFilter = BranchFilter.DefaultBranch
            // Changes in settings and included builds invalidate the instrumented build logic classpath cached in the user home
            triggerRules = """
                +:root=${VCSCore.id}:gradle/libs.versions.toml
                +:root=${VCSCore.id}:gradle/wrapper/gradle-wrapper.properties
                +:root=${VCSCore.id}:gradle/gradle-daemon-jvm.properties
                +:root=${VCSCore.id}:settings.gradle.kts
                +:root=${VCSCore.id}:build-logic/**
                +:root=${VCSCore.id}:build-settings-logic/**
                +:root=${VCSCore.id}:ktor-test-server/**
            """.trimIndent()

            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
            quietPeriod = 1.hours.inWholeSeconds.toInt()
        }

        schedule {
            schedulingPolicy = weekly {
                dayOfWeek = Sunday
                hour = 18
            }
            branchFilter = BranchFilter.DefaultBranch
            withPendingChangesOnly = true
            triggerBuild = always()
        }
    }

    requirements {
        agent(os, arch)
    }

    features {
        perfmon {}
    }

    cleanup {
        baseRule {
            artifacts(builds = 1)
        }
    }
})

val gradleEnvironments = listOf(
    // Used by JvmCompileBuild and JDKBuild
    GradleEnvironment(Agents.OS.Linux, Agents.Arch.X64, setOf(GradleUserHome.Light)),
    // Used by the API docs build
    GradleEnvironment(Agents.OS.MacOS, Agents.Arch.Arm64, setOf(GradleUserHome.Full)),
)

/**
 * Uses the Gradle user home published by [GradleEnvironment] for the given [os].
 * The build continues with an empty Gradle user home if the environment is not available.
 */
fun BuildType.useGradleEnvironment(os: Agents.OS, version: GradleUserHome) {
    val environment = gradleEnvironments.find { it.os == os } ?: return
    require(version in environment.versions) { "${environment.name} doesn't publish $version Gradle user home" }

    params {
        setupGradleParams()
    }

    dependencies {
        artifacts(environment) {
            buildRule = lastSuccessful()
            artifactRules = "?:${version.artifact}!** => $userHome"
        }
    }
}

private fun ParametrizedWithType.setupGradleParams() {
    param("env.GRADLE_USER_HOME", userHome)
}

private fun BuildSteps.resolveDependencies(native: Boolean) {
    // Targets can be disabled only in gradle.properties
    if (!native) {
        script {
            name = "Disable native targets"
            scriptContent = bashScript(
                """
                echo "target.posix=false" >> gradle.properties
                """
            )
        }
    }

    gradle {
        name = "Resolve dependencies (${if (native) "all targets" else "without native targets"})"
        tasks = "help"
        // Dependencies for native source sets are declared in build scripts even if native targets are disabled
        val ignoreExtraSourceSets = if (native) "" else " -Pktorbuild.ignoreExtraSourceSets=true"
        gradleParams = "--write-verification-metadata sha256 --dry-run$ignoreExtraSourceSets"
        jdkHome = Env.JDK_LTS
    }

    if (!native) {
        script {
            name = "Enable native targets"
            scriptContent = bashScript(
                """
                git checkout gradle.properties
                """
            )
        }
    }
}

private fun BuildSteps.packGradleUserHome(version: GradleUserHome) {
    script {
        name = "Pack Gradle user home ($version)"
        scriptContent = bashScript(
            """
            # The archive is unpacked in every build using the environment, so it is worth compressing it better.
            # zstd is run explicitly as tar implementations pass the compression level to it differently.
            # --long finds duplicates across the whole archive. 27 (128 MB window) is the maximum
            # that can be unpacked with default settings, larger values need the same option on unpacking
            tar -C "${'$'}GRADLE_USER_HOME" --exclude=./daemon -cf - . | zstd -9 --long=27 -T0 -q -o ${version.artifact}
            """
        )
    }
}
