package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.core.*
import kotlin.io.path.*

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
 * Build steps fetch [preparedBranches] of [repo] without the credentials of its VCS root,
 * so agents need their own Git credentials for a private repository.
 *
 * @see [useGradleEnvironment].
 */
class GradleEnvironment(
    val os: Agents.OS,
    val arch: Agents.Arch,
    val versions: Set<GradleUserHome>,
    repo: KtorRepository,
) : BuildType({
    id(repo.id("GradleEnvironment_${os.id}"))
    name = repo.name("Gradle Environment (${os.id})")

    vcs {
        root(repo.vcsRoot)
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

    runGradleWithRunnerInitScript()

    steps {
        with(platform) {
            resetGradleUserHome()

            prepareGradleUserHome(repo, GradleUserHome.Light, versions) { branch ->
                resolveDependenciesWithoutNativeTargets()
                environmentGradle(name = "Configure all targets ($branch)", tasks = "jvmTest", params = "--dry-run")
            }

            // Prepared on top of the Light user home
            prepareGradleUserHome(repo, GradleUserHome.Full, versions) {
                resolveDependenciesAndInstallKotlinNative()
            }
        }
    }

    artifactRules = platform.artifactRules(versions)
    publishArtifacts = PublishMode.SUCCESSFUL

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
internal val preparedBranches = listOf(DEFAULT_BRANCH, "release/3.x")

internal fun createGradleEnvironments(repo: KtorRepository) = listOf(
    GradleEnvironment(Agents.OS.Linux, Agents.Arch.X64, setOf(GradleUserHome.Light, GradleUserHome.Full), repo),
    GradleEnvironment(Agents.OS.MacOS, Agents.Arch.Arm64, setOf(GradleUserHome.Full), repo),
    GradleEnvironment(Agents.OS.Windows, Agents.Arch.X64, setOf(GradleUserHome.Full), repo),
)

/**
 * Runs the build on an agent with the given [os], [arch] and [hardwareCapacity],
 * using the Gradle user home published by [GradleEnvironment] of the [repo] for it.
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
    repo: KtorRepository = Core,
) {
    check(steps.items.isEmpty()) { "useGradleEnvironment must be called before adding build steps" }
    val environment = repo.gradleEnvironments.find { it.os == os && it.arch == arch && version in it.versions }

    requirements {
        agent(os, arch, hardwareCapacity)
    }

    params {
        setupGradleParams()
    }

    steps {
        if (environment == null) resetGradleUserHome()
        addGradleProperties(os, deriveJvmArgs, configurationCache)
    }
    runGradleWithRunnerInitScript()

    if (environment == null) return

    dependencies {
        artifacts(environment) {
            buildRule = lastSuccessful(BranchFilter.AllBranches)
            artifactRules = "?:${version.artifact}!** => %env.GRADLE_USER_HOME%"
        }
    }

    if (configurationCache) gradleConfigurationCache(os)
}

/** Runs the build on the given [agent], using the Gradle user home published by [GradleEnvironment] for it. */
fun BuildType.useGradleEnvironment(
    agent: AgentSpec,
    version: GradleUserHome,
    configurationCache: Boolean = true,
    deriveJvmArgs: Boolean = true,
    hardwareCapacity: String = Agents.MEDIUM,
    repo: KtorRepository = Core,
) {
    useGradleEnvironment(agent.os, version, configurationCache, deriveJvmArgs, agent.arch, hardwareCapacity, repo)
}

private fun ParametrizedWithType.setupGradleParams() {
    // The agent work directory has the same path on all agents with the same OS
    param("env.GRADLE_USER_HOME", "%teamcity.agent.work.dir%/gradle-user-home")
    // Kotlin/Native moves its bundle here from the user home, which is fast only on the same disk.
    // Inside the user home, the installation is packed into the environment, so builds don't install it again.
    param("env.KONAN_DATA_DIR", "%teamcity.agent.work.dir%/gradle-user-home/konan")
}

/**
 * Init scripts from `.teamcity/gradle.init.d`, by file name.
 * They are stored with the `.txt` extension, as the TeamCity DSL compiles all `.kts` files in the project.
 * The `%` symbol is escaped, as TeamCity would otherwise take it for a parameter reference.
 */
internal val initScripts: Map<String, String> = dslFile("gradle.init.d").listDirectoryEntries("*.txt").sorted()
    .associate { file -> file.name.removeSuffix(".txt") to readFileForStep(file) }

/**
 * Points Gradle to the JDKs installed on agents, so it doesn't download its own.
 * On Windows agents, Gradle doesn't detect them by itself.
 */
private const val javaInstallationsProperty = "org.gradle.java.installations.fromEnv=JDK_1_8,JDK_11_0,JDK_17_0,JDK_21_0"

/** Files written into the user home when it is reset, by path relative to it. */
private val userHomeFiles: Map<String, String> =
    initScripts.mapKeys { (name, _) -> "init.d/$name" } +
        (runnerInitScriptPathInUserHome to runnerInitScript) +
        ("gradle.properties" to javaInstallationsProperty)

/** Starts with an empty user home with [userHomeFiles]. */
private fun BuildSteps.resetGradleUserHome() = step(
    writeFilesStep(
        name = "Reset Gradle user home",
        files = userHomeFiles.mapKeys { (path, _) -> "\$env:GRADLE_USER_HOME/$path" },
        prepare = """
            if (Test-Path ${'$'}env:GRADLE_USER_HOME) { Remove-Item -Recurse -Force ${'$'}env:GRADLE_USER_HOME }
            """,
    )
)

/** Steps and settings of [GradleEnvironment] that differ between operating systems. */
private sealed interface EnvironmentPlatform {
    fun BuildSteps.checkOutBranch(repo: KtorRepository, branch: String)
    fun BuildSteps.removeKotlinNativeBundle()

    /** Packs the user home after [version] is prepared, so later steps don't change the archive. */
    fun BuildSteps.packGradleUserHome(version: GradleUserHome)
    fun artifactRules(versions: Set<GradleUserHome>): String

    /** Runs [prepare] for each of [preparedBranches] of [repo] checked out, then packs the [version] of the user home, if it is in [versions]. */
    fun BuildSteps.prepareGradleUserHome(
        repo: KtorRepository,
        version: GradleUserHome,
        versions: Set<GradleUserHome>,
        prepare: BuildSteps.(branch: String) -> Unit,
    ) {
        if (version !in versions) return

        for (branch in preparedBranches) {
            checkOutBranch(repo, branch)
            prepare(branch)
        }

        // Removed only after all branches are prepared, as preparing installs Kotlin/Native from the bundle
        if (version == GradleUserHome.Full) removeKotlinNativeBundle()
        packGradleUserHome(version)
    }

    fun BuildSteps.environmentGradle(name: String, tasks: String, params: String = "") {
        gradle {
            this.name = name
            this.tasks = tasks
            gradleParams = params
            jdkHome = Env.JDK_LTS
        }
    }

    fun BuildSteps.resolveDependenciesWithoutNativeTargets() {
        // Targets can be disabled only in gradle.properties
        script {
            name = "Disable native targets"
            scriptContent = bashScript(
                """
                echo "target.posix=false" >> gradle.properties
                """
            )
        }

        // Dependencies for native source sets are declared in build scripts even if native targets are disabled
        environmentGradle(
            name = "Resolve dependencies (without native targets)",
            tasks = "help",
            params = "--write-verification-metadata sha256 --dry-run -Pktorbuild.ignoreExtraSourceSets=true",
        )

        script {
            name = "Enable native targets"
            scriptContent = bashScript(
                """
                git checkout gradle.properties
                """
            )
        }
    }

    /** Resolving dependencies configures all targets anyway, so Kotlin/Native is installed in the same invocation. */
    fun BuildSteps.resolveDependenciesAndInstallKotlinNative() {
        environmentGradle(
            name = "Resolve dependencies and install Kotlin/Native",
            tasks = "downloadKotlinNativeDistribution",
            params = "--write-verification-metadata sha256",
        )
    }

    companion object {
        fun of(os: Agents.OS): EnvironmentPlatform = if (os == Agents.OS.Windows) WindowsPlatform else UnixPlatform
    }
}

private object UnixPlatform : EnvironmentPlatform {
    // Outside the checkout, as checking out the next branch removes untracked files
    private const val archivesDir = "%system.teamcity.build.tempDir%"

    override fun BuildSteps.checkOutBranch(repo: KtorRepository, branch: String) {
        script {
            name = "Check out $branch"
            scriptContent = bashScript(
                """
                git init --quiet
                git fetch --depth=1 ${repo.vcsRoot.url} "refs/heads/$branch"
                git switch --force -C "$branch" FETCH_HEAD
                git clean -fdx
                """
            )
        }
    }

    override fun BuildSteps.removeKotlinNativeBundle() {
        script {
            name = "Remove Kotlin/Native bundle"
            scriptContent = bashScript(
                """
                rm -rf "${'$'}GRADLE_USER_HOME/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-native-prebuilt"
                # Removes whole transform workspaces, so Gradle doesn't find them incomplete
                for unpacked in "${'$'}GRADLE_USER_HOME"/caches/*/transforms/*/transformed/extracted/kotlin-native-prebuilt-*; do
                    if [[ -d ${'$'}unpacked ]]; then rm -rf "${'$'}{unpacked%/transformed/extracted/*}"; fi
                done
                """
            )
        }
    }

    override fun BuildSteps.packGradleUserHome(version: GradleUserHome) {
        script {
            name = "Pack Gradle user home ($version)"
            scriptContent = bashScript(
                """
                # TeamCity unpacks hard links as empty files, which breaks Kotlin/Native toolchain binaries (e.g. ar),
                # so hard-linked files are replaced with copies. BSD tar on macOS has no --hard-dereference.
                find "${'$'}GRADLE_USER_HOME" -type f -links +1 -exec sh -c 'for file; do cp -p "${'$'}file" "${'$'}file.copy" && mv -f "${'$'}file.copy" "${'$'}file"; done' sh {} +

                # The archive is unpacked in every build using the environment, so it is worth compressing it better.
                # zstd is run explicitly as tar implementations pass the compression level to it differently.
                # --long finds duplicates across the whole archive. 27 (128 MB window) is the maximum
                # that can be unpacked with default settings, larger values need the same option on unpacking.
                tar -C "${'$'}GRADLE_USER_HOME" --exclude=./daemon -cf - . | zstd -9 --long=27 -T0 -q -o "$archivesDir/${version.artifact}"
                """
            )
        }
    }

    override fun artifactRules(versions: Set<GradleUserHome>) =
        formatArtifacts(*versions.map { "$archivesDir/${it.artifact}" }.toTypedArray())
}

private object WindowsPlatform : EnvironmentPlatform {
    override fun BuildSteps.checkOutBranch(repo: KtorRepository, branch: String) {
        powerShell {
            name = "Check out $branch"
            scriptMode = script {
                // Failing native commands don't stop PowerShell scripts, so exit codes are checked explicitly
                content = powerShellScript(
                    """
                    function Invoke-Git { git @args; if (${'$'}LASTEXITCODE -ne 0) { exit ${'$'}LASTEXITCODE } }
                    Invoke-Git init --quiet
                    Invoke-Git fetch --depth=1 ${repo.vcsRoot.url} "refs/heads/$branch"
                    Invoke-Git switch --force -C "$branch" FETCH_HEAD
                    Invoke-Git clean -fdx
                    """
                )
            }
        }
    }

    override fun BuildSteps.removeKotlinNativeBundle() {
        powerShell {
            name = "Remove Kotlin/Native bundle"
            scriptMode = script {
                content = powerShellScript(
                    """
                    ${'$'}caches = "${'$'}env:GRADLE_USER_HOME\caches"
                    Remove-Item -Recurse -Force -ErrorAction Ignore "${'$'}caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-native-prebuilt"
                    # Removes whole transform workspaces, so Gradle doesn't find them incomplete
                    Get-ChildItem -Directory -Path "${'$'}caches\*\transforms\*\transformed\extracted\kotlin-native-prebuilt-*" |
                        ForEach-Object { [IO.Directory]::Delete("\\?\" + ${'$'}_.Parent.Parent.Parent.FullName, ${'$'}true) }
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
