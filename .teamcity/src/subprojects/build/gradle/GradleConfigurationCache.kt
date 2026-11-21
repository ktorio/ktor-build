package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import subprojects.*


/**
 * Paths restored by the configuration cache.
 * An entry is valid only together with outputs of included builds it was stored with,
 * so builds should exclude these paths from artifacts they unpack into the checkout.
 * Wildcards are not supported in build cache rules.
 */
internal val configurationCacheRules = listOf(
    ".gradle/configuration-cache",
    "build-logic/build",
    "build-settings-logic/build",
    "ktor-test-server/build",
)

private const val SNAPSHOT_DIR = "%system.teamcity.build.tempDir%/configuration-cache-snapshot"

/**
 * Keeps the Gradle configuration cache between builds of this build type.
 *
 * An entry can be reused only by a build running the same Gradle command in the same environment,
 * so the cache is not shared between build types. TeamCity keeps a cache per branch.
 * Until a branch publishes its own cache, the cache of the default branch is used.
 *
 * The Gradle user home should be at the same path in all builds, so it is enabled by [useGradleEnvironment].
 * `GRADLE_ENCRYPTION_KEY` should be the same in all builds.
 *
 * Entries refer to dependencies resolved at execution time by their paths in the user home and don't download them again,
 * so builds that downloaded dependencies missing from the environment shouldn't publish the cache,
 * see [checkConfigurationCacheDependencies].
 */
internal fun BuildType.gradleConfigurationCache(os: Agents.OS) {
    params {
        // Stops TeamCity from passing build parameters to Gradle (TW-99117).
        // They change from build to build and invalidate the configuration cache.
        // Parameters with the `system.` prefix are not passed to Gradle anymore.
        param("teamcity.internal.gradle.runner.doNotPopulateGradleProperties", "true")
    }

    steps {
        configurationCacheScript("Snapshot configuration cache and mark dependencies", os, "mark")
    }
    buildTypesToCheck[this] = os

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

private val buildTypesToCheck = mutableMapOf<BuildType, Agents.OS>()

/**
 * Adds a step to build types using [gradleConfigurationCache],
 * restoring [configurationCacheRules] as they were downloaded from the build cache,
 * if the build downloaded dependencies missing from the Gradle environment.
 * Later builds would reuse entries stored by this build on agents without these dependencies and fail,
 * so the cache is left unchanged and isn't published.
 * All paths are restored, as entries are valid only together with outputs of included builds they were stored with.
 *
 * The step should run after all Gradle steps, so this should be called once all build types are created.
 */
fun checkConfigurationCacheDependencies() {
    for ((buildType, os) in buildTypesToCheck) {
        buildType.steps {
            configurationCacheScript(
                name = "Restore configuration cache if dependencies were downloaded",
                os = os,
                function = "check",
            )
        }
    }
}

private fun BuildSteps.configurationCacheScript(
    name: String,
    os: Agents.OS,
    function: String,
) {
    platformScript(
        name = name,
        os = os,
        unixScript = "configuration_cache.sh",
        windowsScript = "configuration_cache.ps1",
        suffix = "$function \"$SNAPSHOT_DIR\" ${configurationCacheRules.joinToString(" ")}",
    )
}
