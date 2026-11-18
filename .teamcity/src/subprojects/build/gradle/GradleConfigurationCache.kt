package subprojects.build.gradle

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*


/** The parameter is set by TeamCity only for pull requests. */
private const val PULL_REQUEST_TARGET_BRANCH = "teamcity.pullRequest.target.branch"
private const val DEFAULT_BRANCH = "main"

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
    "build-settings-logic/develocity-patched/build",
    "ktor-test-server/.gradle",
    "ktor-test-server/build",
)

/**
 * Keeps the Gradle configuration cache between builds of this build type.
 *
 * An entry can be reused only by a build running the same Gradle command in the same environment,
 * so the cache is not shared between build types. Each branch has its own cache. Until it publishes one,
 * it uses the cache of the pull request target branch, or of the default branch if it is not a pull request.
 * Builds can't publish to caches of other branches.
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

        // The value for builds that are not pull requests
        param(PULL_REQUEST_TARGET_BRANCH, DEFAULT_BRANCH)
    }

    features {
        // Caches are unpacked in the declaration order, so the branch cache overrides the cache of its base branch
        configurationCache(branch = "%$PULL_REQUEST_TARGET_BRANCH%", publish = false)
        configurationCache(branch = "%teamcity.build.branch%", publish = true)
    }
}

private fun BuildFeatures.configurationCache(branch: String, publish: Boolean) {
    buildCache {
        name = "Gradle Configuration Cache [%system.teamcity.buildType.id%] [$branch]"
        use = true
        this.publish = publish
        publishOnlyChanged = true
        rules = configurationCacheRules.joinToString("\n")
    }
}
