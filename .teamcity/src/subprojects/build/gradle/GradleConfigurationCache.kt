package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*


/** The parameter is set by TeamCity only for pull requests. */
private const val PULL_REQUEST_TARGET_BRANCH = "teamcity.pullRequest.target.branch"
private const val DEFAULT_BRANCH = "main"

/** Set to "true" when Gradle has stored a new configuration cache entry, so the cache should be published. */
private const val PUBLISH_CACHE = "gradle.configurationCache.publish"

/** A file created before Gradle steps. Entries stored by them are newer than this file. */
private const val BUILD_START_MARKER = "%system.teamcity.build.tempDir%/gradle-configuration-cache-marker"

/**
 * A configuration cache entry is valid only together with outputs of included builds it was stored with.
 * Wildcards are not supported in build cache rules.
 */
private val configurationCacheRules = listOf(
    ".gradle",
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
 * The cache is published only if Gradle has stored a new entry.
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

        // The parameter should be defined to be referenced. An empty value means "false"
        param(PUBLISH_CACHE, "")
    }

    // The check for new entries should run after all Gradle steps
    require(steps.items.any { it is GradleBuildStep }) {
        "Configuration cache should be enabled after Gradle steps are declared"
    }
    steps {
        markBuildStart()
        // The marker should be created before Gradle steps
        items.add(0, items.removeAt(items.lastIndex))
        publishCacheIfNewEntriesStored()
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
        // Gradle changes its lock files in every build, so the cache always looks changed for TeamCity.
        // To not publish the same entries after every build, publishing is controlled by a parameter.
        param("publish-cache", if (publish) "%$PUBLISH_CACHE%" else "")
        rules = configurationCacheRules.joinToString("\n")
    }
}

private fun BuildSteps.markBuildStart() {
    script {
        name = "Mark the start of the build for Gradle configuration cache"
        scriptContent = bashScript(
            """
            touch "$BUILD_START_MARKER"
            """
        )
    }
}

/** Allows publishing the cache if Gradle steps have stored a new configuration cache entry. */
private fun BuildSteps.publishCacheIfNewEntriesStored() {
    script {
        name = "Check for new Gradle configuration cache entries"
        // An entry is stored before tasks are executed, so it is valid even if the build has failed
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = bashScript(
            """
            # Gradle stores each entry in its own directory. Entries restored from caches are older than the marker
            newEntries="${'$'}(find .gradle/configuration-cache -mindepth 1 -maxdepth 1 -type d -newer "$BUILD_START_MARKER" 2> /dev/null || true)"
            if [ -n "${'$'}newEntries" ]; then
                echo "New configuration cache entries, the cache will be published:"
                echo "${'$'}newEntries"
                echo "##teamcity[setParameter name='$PUBLISH_CACHE' value='true']"
            else
                echo "No new configuration cache entries, the cache will not be published"
            fi
            """
        )
    }
}
