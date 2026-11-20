package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*

val GradleParams = ParameterRef("gradle_params")

fun ParametrizedWithType.extraGradleParams() {
    text(
        name = GradleParams.name,
        label = "Gradle Parameters",
        description = "Additional Gradle parameters to pass to the build",
        value = "",
        display = ParameterDisplay.NORMAL,
        allowEmpty = true
    )
}

/**
 * Whether to compile in a separate Gradle invocation before running tests.
 * Can be disabled when starting a build to measure running everything in one invocation.
 */
fun ParametrizedWithType.splitStepsParam() {
    param("splitSteps", "true")
}

fun BuildStep.ifSplitSteps() {
    conditions { equals("splitSteps", "true") }
}
