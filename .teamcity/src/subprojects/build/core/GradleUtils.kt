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
