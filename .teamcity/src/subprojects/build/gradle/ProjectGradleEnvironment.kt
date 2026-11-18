package subprojects.build.gradle

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.*
import subprojects.*
import subprojects.build.*

object ProjectGradleEnvironment : Project({
    id("ProjectGradleEnvironment")
    name = "Ktor Gradle Caches"
    description = "Build reusable Gradle caches for Ktor"

    params {
        defaultGradleParams()
    }

    gradleEnvironments.forEach(::buildType)

    buildType {
        id("GradleEnvironment")
        name = "Build Gradle Environment"
        description = "Build Gradle environments for all platforms"
        type = Type.COMPOSITE

        vcs {
            root(VCSCore)
        }

        dependencies {
            for (environment in gradleEnvironments) {
                snapshot(environment) { onDependencyFailure = FailureAction.FAIL_TO_START }
            }
        }
    }
})
