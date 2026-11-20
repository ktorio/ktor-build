package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger.DAY.*
import subprojects.*
import subprojects.build.core.*
import kotlin.time.Duration.Companion.minutes

/** Versions of the Gradle user home published by [GradleEnvironment]. */
enum class GradleUserHome(internal val artifact: String) {
    /** Without dependencies of native targets. It is enough for builds that don't run native tasks. */
    Light("gradle-user-home-light.tar.zst"),

    /** With dependencies of all targets. */
    Full("gradle-user-home-full.tar.zst"),
}

private const val DEFAULT_BRANCH = "main"

/**
 * Publishes a warmed-up Gradle user home, so builds on fresh agents don't start with an empty one.
 * Only [versions] used by builds on the given [os] should be published.
 *
 * @see [useGradleEnvironment].
 */
class GradleEnvironment(
    val os: Agents.OS,
    val arch: Agents.Arch,
    val versions: Set<GradleUserHome>,
) : BuildType({
    id("GradleEnvironment_${os.id}")
    name = "Gradle Environment (${os.id})"

    vcs {
        root(VCSCore)
        checkoutMode = CheckoutMode.MANUAL
        cleanCheckout = true
    }

    params {
        setupGradleParams()
    }

    val platform = EnvironmentPlatform.of(os)
    require(platform != WindowsPlatform || GradleUserHome.Light !in versions) {
        "Light Gradle user home is not supported on Windows"
    }

    steps {
        if (os == Agents.OS.Windows) excludeFromDefender()

        with(platform) {
            resetGradleUserHome()

            prepareGradleUserHome(GradleUserHome.Light, versions) { branch ->
                resolveDependencies(native = false)
                environmentGradle(name = "Configure all targets ($branch)", tasks = "jvmTest", params = "--dry-run")
            }

            // Prepared on top of the Light user home
            prepareGradleUserHome(GradleUserHome.Full, versions) {
                resolveDependencies(native = true)
            }
        }
    }

    artifactRules = platform.artifactRules(versions)
    publishArtifacts = PublishMode.SUCCESSFUL

    triggers {
        vcs {
            branchFilter = preparedBranches.joinToString("\n") { "+:$it" }
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
            quietPeriod = 10.minutes.inWholeSeconds.toInt()
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

/**
 * Branches whose dependencies and build logic are prepared in the Gradle user home,
 * so builds of these branches and pull requests targeting them find them there, whichever branch the environment is built on.
 * The configuration cache can be reused only if the user home contains transformed jars of the same included builds.
 */
private val preparedBranches = listOf(DEFAULT_BRANCH, "release/3.x")

val gradleEnvironments = listOf(
    GradleEnvironment(Agents.OS.Linux, Agents.Arch.X64, setOf(GradleUserHome.Light, GradleUserHome.Full)),
    GradleEnvironment(Agents.OS.MacOS, Agents.Arch.Arm64, setOf(GradleUserHome.Full)),
    GradleEnvironment(Agents.OS.Windows, Agents.Arch.X64, setOf(GradleUserHome.Full)),
)

/**
 * Runs the build on an agent with the given [os], [arch] and [hardwareCapacity],
 * using the Gradle user home published by [GradleEnvironment] for it.
 * The build continues with an empty Gradle user home if no environment publishes this [version] for the agent.
 *
 * @param configurationCache Whether to keep the Gradle configuration cache between builds.
 *   It can be reused only when the Gradle user home is the same, so it is available only with the environment.
 * @param deriveJvmArgs Whether to replace the project's heap sizes with smaller ones for CI agents.
 */
fun BuildType.useGradleEnvironment(
    os: Agents.OS,
    version: GradleUserHome,
    configurationCache: Boolean = true,
    deriveJvmArgs: Boolean = true,
    arch: Agents.Arch = Agents.Arch.X64,
    hardwareCapacity: String = Agents.MEDIUM,
) {
    check(steps.items.isEmpty()) { "useGradleEnvironment must be called before adding build steps" }
    val environment = gradleEnvironments.find { it.os == os && it.arch == arch && version in it.versions }

    requirements {
        agent(os, arch, hardwareCapacity)
    }

    params {
        setupGradleParams()
    }

    steps {
        if (os == Agents.OS.Windows) excludeFromDefender()
        if (environment == null) with(EnvironmentPlatform.of(os)) { resetGradleUserHome() }
        addGradleProperties(os, deriveJvmArgs, configurationCache)
    }

    if (environment == null) return

    dependencies {
        artifacts(environment) {
            buildRule = lastSuccessful()
            artifactRules = "?:${version.artifact}!** => %env.GRADLE_USER_HOME%"
        }
    }

    if (configurationCache) gradleConfigurationCache()
}

/** Runs the build on the given [agent], using the Gradle user home published by [GradleEnvironment] for it. */
fun BuildType.useGradleEnvironment(
    agent: AgentSpec,
    version: GradleUserHome,
    configurationCache: Boolean = true,
    deriveJvmArgs: Boolean = true,
    hardwareCapacity: String = Agents.MEDIUM,
) {
    useGradleEnvironment(agent.os, version, configurationCache, deriveJvmArgs, agent.arch, hardwareCapacity)
}

private fun ParametrizedWithType.setupGradleParams() {
    // The agent work directory has the same path on all agents with the same OS
    param("env.GRADLE_USER_HOME", "%teamcity.agent.work.dir%/gradle-user-home")
    // Kotlin/Native moves its bundle here from the user home, which is fast only on the same disk
    param("env.KONAN_DATA_DIR", "%teamcity.agent.work.dir%/konan")
}

private const val disableCacheCleanupFile = "disable-cache-cleanup.init.gradle.kts"

/**
 * Builds start with a user home unpacked from the environment and discard it, so cleaning up its caches only takes time.
 * The script is written in one line to be passed as a shell argument.
 */
private const val disableCacheCleanupScript = "beforeSettings { caches { cleanup = Cleanup.DISABLED } }"

/**
 * Points Gradle to the JDKs installed on agents, so it doesn't download its own.
 * On Windows agents, Gradle doesn't detect them by itself.
 */
private const val javaInstallationsProperty = "org.gradle.java.installations.fromEnv=JDK_1_8,JDK_11_0,JDK_17_0,JDK_21_0"

/** Steps and settings of [GradleEnvironment] that differ between operating systems. */
private sealed interface EnvironmentPlatform {
    /** Parameters required for Gradle steps of the environment build. */
    val requiredGradleParams: String

    /** Starts with an empty user home that doesn't clean up its caches, see [disableCacheCleanupScript]. */
    fun BuildSteps.resetGradleUserHome()
    fun BuildSteps.checkOutBranch(branch: String)

    /** Packs the user home after [version] is prepared, so later steps don't change the archive. */
    fun BuildSteps.packGradleUserHome(version: GradleUserHome)
    fun artifactRules(versions: Set<GradleUserHome>): String

    /** Runs [prepare] for each of [preparedBranches] checked out, then packs the [version] of the user home, if it is in [versions]. */
    fun BuildSteps.prepareGradleUserHome(
        version: GradleUserHome,
        versions: Set<GradleUserHome>,
        prepare: BuildSteps.(branch: String) -> Unit,
    ) {
        if (version !in versions) return

        for (branch in preparedBranches) {
            checkOutBranch(branch)
            prepare(branch)
        }

        packGradleUserHome(version)
    }

    fun BuildSteps.environmentGradle(name: String, tasks: String, params: String) {
        gradle {
            this.name = name
            this.tasks = tasks
            gradleParams = "$params $requiredGradleParams".trim()
            jdkHome = Env.JDK_LTS
        }
    }

    fun BuildSteps.resolveDependencies(native: Boolean) {
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

        // Dependencies for native source sets are declared in build scripts even if native targets are disabled
        val ignoreExtraSourceSets = if (native) "" else " -Pktorbuild.ignoreExtraSourceSets=true"
        environmentGradle(
            name = "Resolve dependencies (${if (native) "all targets" else "without native targets"})",
            tasks = "help",
            params = "--write-verification-metadata sha256 --dry-run$ignoreExtraSourceSets",
        )

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

    companion object {
        fun of(os: Agents.OS): EnvironmentPlatform = if (os == Agents.OS.Windows) WindowsPlatform else UnixPlatform
    }
}

private object UnixPlatform : EnvironmentPlatform {
    // Outside the checkout, as checking out the next branch removes untracked files
    private const val archivesDir = "%system.teamcity.build.tempDir%"

    override val requiredGradleParams = ""

    override fun BuildSteps.resetGradleUserHome() {
        script {
            name = "Reset Gradle user home"
            scriptContent = bashScript(
                """
                rm -rf "${'$'}GRADLE_USER_HOME"
                mkdir -p "${'$'}GRADLE_USER_HOME/init.d"
                echo '$disableCacheCleanupScript' > "${'$'}GRADLE_USER_HOME/init.d/$disableCacheCleanupFile"
                echo '$javaInstallationsProperty' > "${'$'}GRADLE_USER_HOME/gradle.properties"
                """
            )
        }
    }

    override fun BuildSteps.checkOutBranch(branch: String) {
        script {
            name = "Check out $branch"
            scriptContent = bashScript(
                """
                git init --quiet
                git fetch --depth=1 ${VCSCore.url} "refs/heads/$branch"
                git switch --force -C "$branch" FETCH_HEAD
                git clean -fdx
                """
            )
        }
    }

    override fun BuildSteps.packGradleUserHome(version: GradleUserHome) {
        script {
            name = "Pack Gradle user home ($version)"
            scriptContent = bashScript(
                """
                # The archive is unpacked in every build using the environment, so it is worth compressing it better.
                # zstd is run explicitly as tar implementations pass the compression level to it differently.
                # --long finds duplicates across the whole archive. 27 (128 MB window) is the maximum
                # that can be unpacked with default settings, larger values need the same option on unpacking
                tar -C "${'$'}GRADLE_USER_HOME" --exclude=./daemon -cf - . | zstd -9 --long=27 -T0 -q -o "$archivesDir/${version.artifact}"
                """
            )
        }
    }

    override fun artifactRules(versions: Set<GradleUserHome>) =
        formatArtifacts(*versions.map { "$archivesDir/${it.artifact}" }.toTypedArray())
}

private object WindowsPlatform : EnvironmentPlatform {
    // Windows doesn't allow reading files locked by a running daemon, so the user home couldn't be packed
    override val requiredGradleParams = "--no-daemon"

    override fun BuildSteps.resetGradleUserHome() {
        powerShell {
            name = "Reset Gradle user home"
            scriptMode = script {
                content = powerShellScript(
                    """
                    if (Test-Path ${'$'}env:GRADLE_USER_HOME) { Remove-Item -Recurse -Force -ErrorAction Stop ${'$'}env:GRADLE_USER_HOME }
                    New-Item -ItemType Directory -Force "${'$'}env:GRADLE_USER_HOME\init.d" | Out-Null
                    Set-Content -Path "${'$'}env:GRADLE_USER_HOME\init.d\$disableCacheCleanupFile" -Value '$disableCacheCleanupScript'
                    Set-Content -Path "${'$'}env:GRADLE_USER_HOME\gradle.properties" -Value '$javaInstallationsProperty'
                    """
                )
            }
        }
    }

    override fun BuildSteps.checkOutBranch(branch: String) {
        powerShell {
            name = "Check out $branch"
            scriptMode = script {
                // Failing native commands don't stop PowerShell scripts, so exit codes are checked explicitly
                content = powerShellScript(
                    """
                    function Invoke-Git { git @args; if (${'$'}LASTEXITCODE -ne 0) { exit ${'$'}LASTEXITCODE } }
                    Invoke-Git init --quiet
                    Invoke-Git fetch --depth=1 ${VCSCore.url} "refs/heads/$branch"
                    Invoke-Git switch --force -C "$branch" FETCH_HEAD
                    Invoke-Git clean -fdx
                    """
                )
            }
        }
    }

    // zstd is not installed on Windows agents, so the user home is packed by TeamCity.
    override fun BuildSteps.packGradleUserHome(version: GradleUserHome) = Unit

    override fun artifactRules(versions: Set<GradleUserHome>) = formatArtifacts(
        *versions.map { "+:%env.GRADLE_USER_HOME%/** => ${it.artifact}" }.toTypedArray(),
        "-:%env.GRADLE_USER_HOME%/daemon/**",
    )
}
