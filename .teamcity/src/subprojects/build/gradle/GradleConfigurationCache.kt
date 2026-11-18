package subprojects.build.gradle

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*


/**
 * A configuration cache entry is valid only together with outputs of included builds it was stored with.
 * Wildcards are not supported in build cache rules.
 */
private val configurationCacheRules = listOf(
    ".gradle/configuration-cache",
    "build-logic/.gradle",
    "build-logic/build",
    "build-settings-logic/.gradle",
    "build-settings-logic/build",
    "ktor-test-server/.gradle",
    "ktor-test-server/build",
)

/**
 * Keeps the Gradle configuration cache between builds of this build type.
 *
 * An entry can be reused only by a build running the same Gradle command in the same environment,
 * so the cache is not shared between build types. TeamCity keeps a cache per branch.
 * Until a branch publishes its own cache, the cache of the default branch is used.
 *
 * The Gradle user home should be at the same path in all builds, so it is enabled by [useGradleEnvironment].
 * `GRADLE_ENCRYPTION_KEY` should be the same in all builds.
 */
internal fun BuildType.gradleConfigurationCache() {
    params {
        // Stops TeamCity from passing build parameters to Gradle (TW-99117).
        // They change from build to build and invalidate the configuration cache.
        // Parameters with the `system.` prefix are not passed to Gradle anymore.
        param("teamcity.internal.gradle.runner.doNotPopulateGradleProperties", "true")
    }

    features {
        buildCache {
            // The name is used as a file name, so it shouldn't contain branch names with slashes
            name = "configuration-cache-%system.teamcity.buildType.id%"
            use = true
            publish = true
            publishOnlyChanged = true
            rules = configurationCacheRules.joinToString("\n")
        }
    }
}
