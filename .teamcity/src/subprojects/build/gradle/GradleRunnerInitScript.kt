package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

/** Path of the runner init script in the Gradle user home, relative to it. Not `init.gradle`, which Gradle applies by itself. */
internal const val runnerInitScriptPathInUserHome = "gradle-runner-init.gradle"
private const val runnerInitScriptPath = "%env.GRADLE_USER_HOME%/$runnerInitScriptPathInUserHome"

/**
 * The replacement for the init script of TeamCity's Gradle runner.
 * TeamCity's script adds `doFirst` actions to compile and test tasks, which become part of their build cache keys,
 * so builds on CI and locally never share build cache entries.
 */
internal val runnerInitScript: String = readFileForStep(dslFile("gradle-runner/init_v2.gradle"))

private val buildTypesWithRunnerInitScript = mutableListOf<BuildType>()

/** Makes Gradle steps of this build type use the runner init script from the Gradle user home, after [useGradleRunnerInitScript]. */
internal fun BuildType.runGradleWithRunnerInitScript() {
    buildTypesWithRunnerInitScript += this
}

/** Whether this build type uses the Gradle user home with the runner init script, which also has the `gradle.init.d` scripts. */
internal fun BuildType.runsGradleWithRunnerInitScript(): Boolean = this in buildTypesWithRunnerInitScript

/**
 * Makes Gradle steps of build types using [runGradleWithRunnerInitScript] use the runner init script.
 * The runner reads it only from step parameters, so this should be called after all build types are created.
 */
fun useGradleRunnerInitScript() {
    for (buildType in buildTypesWithRunnerInitScript) {
        buildType.steps.items.filterIsInstance<GradleBuildStep>().forEach { step ->
            step.param("ui.gradleRunner.gradle.init.script", runnerInitScriptPath)
        }
    }
}
