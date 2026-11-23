package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import kotlin.io.path.*

private const val runnerInitScriptParameter = "gradle.runner.initScript"
private const val runnerInitScriptPath = "%system.teamcity.build.tempDir%/gradle-runner-init.gradle"
private const val writeRunnerInitScriptStepName = "Write Gradle runner init script"

private val buildTypesWithRunnerInitScript = mutableListOf<BuildType>()

/**
 * Adds `.teamcity/gradle-runner/init_v2.gradle` as a parameter, written by [writeGradleRunnerInitScript].
 * It is stored once for all build types, as embedding it into each of them would add megabytes to the settings.
 */
fun ParametrizedWithType.gradleRunnerInitScriptParameter() {
    text(
        runnerInitScriptParameter,
        readFileForStep(Path("gradle-runner/init_v2.gradle")),
        display = ParameterDisplay.HIDDEN,
    )
}

/**
 * Writes the replacement for the init script of TeamCity's Gradle runner, used by Gradle steps after [useGradleRunnerInitScript].
 * TeamCity's script adds `doFirst` actions to compile and test tasks, which become part of their build cache keys,
 * so builds on CI and locally never share build cache entries.
 */
internal fun BuildType.writeGradleRunnerInitScript(os: Agents.OS) {
    val initScript = "%$runnerInitScriptParameter%"
    steps {
        if (os == Agents.OS.Windows) {
            powerShell {
                name = writeRunnerInitScriptStepName
                scriptMode = script { content = powerShellWriteFile(runnerInitScriptPath, initScript) }
            }
        } else {
            script {
                name = writeRunnerInitScriptStepName
                scriptContent = bashWriteFile(runnerInitScriptPath, initScript)
            }
        }
    }
    buildTypesWithRunnerInitScript += this
}

/**
 * Makes Gradle steps of build types using [writeGradleRunnerInitScript] use the written init script.
 * The runner reads it only from step parameters, so this should be called after all build types are created.
 */
fun useGradleRunnerInitScript() {
    for (buildType in buildTypesWithRunnerInitScript) {
        buildType.steps.items.filterIsInstance<GradleBuildStep>().forEach { step ->
            step.param("ui.gradleRunner.gradle.init.script", runnerInitScriptPath)
        }
    }
}
