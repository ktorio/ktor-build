package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger.DAY.*
import subprojects.*
import kotlin.time.Duration.Companion.hours

private const val userHomeArtifact = "gradle-user-home.tar.zst"

// The agent work directory has the same path on all agents with the same OS
private const val userHome = "%teamcity.agent.work.dir%/gradle-user-home"

/**
 * Publishes a warmed-up Gradle user home, so builds on fresh agents don't start with an empty one.
 * @see [useGradleEnvironment].
 */
class GradleEnvironment(val os: Agents.OS, arch: Agents.Arch) : BuildType({
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
        script {
            name = "Clean Gradle user home"
            scriptContent = bashScript("""
                rm -rf "${'$'}GRADLE_USER_HOME"
            """)
        }

        gradle {
            name = "Resolve dependencies"
            tasks = "help"
            gradleParams = "--write-verification-metadata sha256 --dry-run --no-configuration-cache"
            jdkHome = Env.JDK_LTS
        }

        script {
            name = "Pack Gradle user home"
            scriptContent = bashScript("""
                # macOS agents don't have zstd installed
                if ! command -v zstd > /dev/null; then
                    HOMEBREW_NO_AUTO_UPDATE=1 brew install zstd
                fi

                # The archive is unpacked in every build using the environment, so it is worth compressing it better.
                # zstd is run explicitly as tar implementations pass the compression level to it differently
                tar -C "${'$'}GRADLE_USER_HOME" --exclude=./daemon -cf - . | zstd -9 -T0 -q -o $userHomeArtifact
            """)
        }
    }

    artifactRules = userHomeArtifact
    publishArtifacts = PublishMode.SUCCESSFUL

    triggers {
        vcs {
            branchFilter = BranchFilter.DefaultBranch
            triggerRules = """
                +:root=${VCSCore.id}:gradle/libs.versions.toml
                +:root=${VCSCore.id}:gradle/wrapper/gradle-wrapper.properties
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
    GradleEnvironment(Agents.OS.Linux, Agents.Arch.X64),
    GradleEnvironment(Agents.OS.MacOS, Agents.Arch.Arm64),
)

/**
 * Uses the Gradle user home published by [GradleEnvironment] for the given [os].
 * The build continues with an empty Gradle user home if the environment is not available.
 */
fun BuildType.useGradleEnvironment(os: Agents.OS) {
    val environment = gradleEnvironments.find { it.os == os } ?: return

    params {
        setupGradleParams()
    }

    dependencies {
        artifacts(environment) {
            buildRule = lastSuccessful()
            artifactRules = "?:$userHomeArtifact!** => $userHome"
        }
    }
}

private fun ParametrizedWithType.setupGradleParams() {
    param("env.GRADLE_USER_HOME", userHome)
}
