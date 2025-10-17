package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*

public fun ParametrizedWithType.extraGradleParams() {
    text(
        name = "gradle_params",
        label = "Gradle Parameters",
        description = "Additional Gradle parameters to pass to the build",
        value = "",
        display = ParameterDisplay.NORMAL,
        allowEmpty = true
    )
}
