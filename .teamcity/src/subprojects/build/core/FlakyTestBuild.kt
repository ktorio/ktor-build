package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.Agents.OS
import subprojects.build.*

/** Prefix TeamCity prepends to the relative ids declared in this project. */
private const val PROJECT_PREFIX = "Ktor_"

/** Relative TeamCity id of the Linux [CoreFlakyTestBuild]. */
private const val FLAKY_TEST_ID = "KtorCore_FlakyTest"

/**
 * External TeamCity id of the Linux [CoreFlakyTestBuild].
 *
 * The id declared via `id(...)` is relative, while triggers and the REST API need the
 * project-prefixed external id. Kept as a constant so the trigger in [CoreFlakyTestNotifier] and
 * the `quarantine.build.type` parameter read by `collect_quarantine.sh` cannot drift apart.
 */
const val FLAKY_TEST_BUILD_EXTERNAL_ID = PROJECT_PREFIX + FLAKY_TEST_ID

/**
 * Native targets whose quarantined tests can only run on a matching host, so they get a dedicated
 * per-OS flaky build. Linux is covered by [CoreFlakyTestBuild] itself; these add macOS and Windows
 * so a macosArm64- or mingwX64-only flaky test is actually sampled instead of silently going dark.
 */
val QUARANTINE_NATIVE_ENTRIES = listOf(NativeEntry.MacOSArm64, NativeEntry.MingwX64)

/** Relative TeamCity id of the per-OS native flaky build for [entry]. */
private fun nativeFlakyId(entry: NativeEntry): String = "${FLAKY_TEST_ID}_${entry.id}".toId()

/** Project-prefixed external id of the per-OS native flaky build for [entry]. */
fun nativeFlakyExternalId(entry: NativeEntry): String = PROJECT_PREFIX + nativeFlakyId(entry)

/**
 * External ids of every quarantine build the notifier consolidates: the Linux build plus one per
 * native OS. `collect_quarantine.sh` reads this list (space-joined) and unions their results.
 */
val FLAKY_TEST_BUILD_EXTERNAL_IDS: List<String> =
    listOf(FLAKY_TEST_BUILD_EXTERNAL_ID) + QUARANTINE_NATIVE_ENTRIES.map(::nativeFlakyExternalId)

/**
 * Runs the quarantined tests that every other build skips, so they keep producing data instead of
 * going dark.
 *
 * Deliberately excluded from the `KtorCore_All` composite: a quarantined test is expected to fail
 * sometimes, so it must never gate a pull request. It runs on a schedule instead, and each run is
 * one sample of whether the test still flips — which is what [CoreFlakyTestNotifier] reports on.
 *
 * Ktor marks quarantined tests two ways, and this build needs both because they cover different
 * platforms:
 *  - `@Flaky("KTOR-1234")` is enforced by a JUnit `ExecutionCondition`, so it is JVM-only. The
 *    `flakyTest` task pins `ktor.tests.flaky=only` for the test JVM and runs *only* those tests.
 *  - A `_flaky` token in the test name is a Gradle test filter, so it works on every target.
 *    `-Pktor.tests.flaky=only` selects exactly those tests and nothing else.
 *
 * This Linux build covers JVM, JS, WasmJs and linuxX64. macOS and Windows native targets are covered
 * by [CoreNativeFlakyTestBuild] (see [QUARANTINE_NATIVE_ENTRIES]), because Kotlin/Native tests only
 * run on a matching host.
 */
object CoreFlakyTestBuild : BuildType({
    id(FLAKY_TEST_ID)
    name = "Flaky (Quarantined) Tests"
    description = "Runs @Flaky and _flaky-named tests that the regular builds exclude"
    artifactRules = formatArtifacts(junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    params {
        extraGradleParams()
    }

    triggers {
        schedule {
            schedulingPolicy = daily {
                hour = 4
                timezone = "Europe/Moscow"
            }
            branchFilter = BranchFilter.DefaultBranch
            // Sampling flakiness over time is the point, so run even without new commits.
            triggerBuild = always()
            param("revisionRuleBuildBranch", "<default>")
        }
    }

    steps {
        // `flakyTest` runs only the @Flaky-annotated tests (it pins ktor.tests.flaky=only itself),
        // covering the annotation-marked JVM tests; the `_flaky`-named ones are covered per target below.
        gradle {
            name = "Run quarantined tests (JVM)"
            tasks = "flakyTest"
            gradleParams = "--continue -Ptest.jdk=${JDKEntry.JavaLTS.version} $GradleParams"
            jdkHome = Env.JDK_LTS
            enableStacktrace = true
        }

        gradle {
            name = "Run quarantined tests (JS, WasmJs)"
            tasks = "jsNodeTest wasmJsNodeTest"
            gradleParams = "-Pktor.tests.flaky=only -Penable-js-tests --continue $GradleParams"
            setupDockerForJavaScriptTests(js)
        }

        gradle {
            name = "Run quarantined tests (Native)"
            tasks = "linuxX64Test"
            gradleParams = "-Pktor.tests.flaky=only --continue $GradleParams"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux)
    }
})

/**
 * Per-OS native quarantine build: runs the `_flaky`-named tests for a single native [entry] on a
 * matching host, so macosArm64/mingwX64 quarantined tests keep producing data (the Linux
 * [CoreFlakyTestBuild] can only run linuxX64). One instance per [QUARANTINE_NATIVE_ENTRIES] entry;
 * [CoreFlakyTestNotifier] consolidates them via [FLAKY_TEST_BUILD_EXTERNAL_IDS].
 */
class CoreNativeFlakyTestBuild(private val entry: NativeEntry) : BuildType({
    id(nativeFlakyId(entry))
    name = "Flaky (Quarantined) Tests ${entry.name} ${entry.arch}"
    description = "Runs _flaky-named ${entry.target} tests that the regular builds exclude"
    artifactRules = formatArtifacts(junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    params {
        extraGradleParams()
    }

    triggers {
        schedule {
            schedulingPolicy = daily {
                hour = 4
                timezone = "Europe/Moscow"
            }
            branchFilter = BranchFilter.DefaultBranch
            triggerBuild = always()
            param("revisionRuleBuildBranch", "<default>")
        }
    }

    steps {
        if (entry.os == OS.Windows) {
            powerShell {
                name = "Remove git from PATH"
                scriptMode = script {
                    content = """
                        ${'$'}oldPath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
                        ${'$'}newPath = (${'$'}oldPath.Split(';') | Where-Object { ${'$'}_ -ne "C:\Program Files\Git\usr\bin" }) -join ';'
                        [Environment]::SetEnvironmentVariable('Path', ${'$'}newPath, 'Machine')
                    """.trimIndent()
                }
            }
            defineTCPPortRange()
        }

        gradle {
            name = "Run quarantined tests (${entry.targetTask(suffix = "Test")})"
            tasks = entry.targetTask(suffix = "Test")
            gradleParams = "-Pktor.tests.flaky=only --continue $GradleParams"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(entry)
    }
})
