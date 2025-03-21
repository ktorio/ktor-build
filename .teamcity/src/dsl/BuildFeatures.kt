package dsl

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*

/**
 * Enables Gradle Configuration Cache restoring.
 *
 * The same encryption key should be provided in GRADLE_ENCRYPTION_KEY across multiple Gradle runs
 * to make it possible to reuse configuration cache
 * https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:secrets:configuring_encryption_key
 */
fun BuildType.gradleConfigurationCache() {
    params {
        // Workaround for https://youtrack.jetbrains.com/issue/TW-92745
        param("teamcity.internal.gradle.runner.read.all.params", "true")
        param("system.teamcity.internal.gradle.runner.read.all.params", "true")
        //param("system.org.gradle.configuration-cache.inputs.unsafe.ignore.file-system-checks", "**/teamcity.build.parameters.static")
    }

    features {
        buildCache {
            name = "gradle-configuration-cache"
            publish = true
            publishOnlyChanged = true
            use = true
            rules = ".gradle/configuration-cache/"
        }
    }
}
