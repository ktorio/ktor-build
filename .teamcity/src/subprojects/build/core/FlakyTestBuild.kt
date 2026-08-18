package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.*

/**
 * External TeamCity id of [CoreFlakyTestBuild].
 *
 * The id declared via `id(...)` is relative, while triggers and the REST API need the
 * project-prefixed external id. Kept as a constant so the trigger in [CoreFlakyTestNotifier] and
 * the `quarantine.build.type` parameter read by `collect_quarantine.sh` cannot drift apart.
 */
const val FLAKY_TEST_BUILD_EXTERNAL_ID = "Ktor_KtorCore_FlakyTest"

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
 *    `flakyTest` task sets `enable.flaky.tests` and runs the JVM suite with those tests enabled.
 *  - A `_flaky` token in the test name is a Gradle test filter, so it works on every target.
 *    `-Pktor.tests.flaky=only` selects exactly those tests and nothing else.
 *
 * Native coverage is limited to Linux targets, because Kotlin/Native tests only run on a matching
 * host. Quarantined tests specific to macOS or MinGW need an agent-specific variant of this build.
 */
object CoreFlakyTestBuild : BuildType({
    id("KtorCore_FlakyTest")
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
        // `flakyTest` is exempt from the name-based filter, so this covers both the annotated and
        // the `_flaky`-named tests on the JVM.
        gradle {
            name = "Run quarantined tests (JVM)"
            tasks = "flakyTest"
            gradleParams = "--continue --info -Ptest.jdk=${JDKEntry.JavaLTS.version} $GradleParams"
            jdkHome = Env.JDK_LTS
            enableStacktrace = true
        }

        gradle {
            name = "Run quarantined tests (JS, WasmJs)"
            tasks = "jsNodeTest wasmJsNodeTest"
            gradleParams = "-Pktor.tests.flaky=only -Penable-js-tests --continue --info $GradleParams"
            setupDockerForJavaScriptTests(js)
        }

        gradle {
            name = "Run quarantined tests (Native)"
            tasks = "linuxX64Test"
            gradleParams = "-Pktor.tests.flaky=only --continue --info $GradleParams"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux)
    }
})
