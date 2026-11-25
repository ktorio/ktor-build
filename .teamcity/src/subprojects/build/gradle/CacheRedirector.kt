package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

private const val cacheRedirectorScriptName = "cache-redirector.init.gradle.kts"
private val cacheRedirectorScript = initScripts.getValue(cacheRedirectorScriptName)

private const val mavenCentralMirror = "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2"

/** Maven settings using cache-redirector as the Maven Central mirror. */
private val mavenSettings = """
    <settings>
      <mirrors>
        <mirror>
          <id>cache-redirector</id>
          <mirrorOf>central</mirrorOf>
          <url>$mavenCentralMirror</url>
        </mirror>
      </mirrors>
    </settings>
""".trimIndent()

/**
 * Redirects repositories through JetBrains cache-redirector in Gradle and Maven runs of all build types
 * in this project and its subprojects, including Gradle and Maven builds started by tests.
 * Build types using the Gradle environment have the Gradle init script in their user home already,
 * others get a first step installing it into the agent's Gradle user home, along with Maven settings.
 * It should be called after all build types are created.
 */
fun Project.useCacheRedirector() {
    for (buildType in buildTypes) {
        if (buildType.runsGradleWithRunnerInitScript()) continue
        val steps = buildType.steps.items
        if (steps.none { it is GradleBuildStep || it is MavenBuildStep }) continue
        steps.add(0, installCacheRedirectorStep())
    }
    subProjects.forEach { it.useCacheRedirector() }
}

// Maven settings are replaced rather than merged, like ExternalSamplesValidationStep does.
private fun installCacheRedirectorStep() = writeFilesStep(
    name = "Install cache-redirector configuration",
    files = mapOf(
        "\$userHome/init.d/$cacheRedirectorScriptName" to cacheRedirectorScript,
        "\$HOME/.m2/settings.xml" to mavenSettings,
    ),
    prepare = """
        ${'$'}userHome = if (${'$'}env:GRADLE_USER_HOME) { ${'$'}env:GRADLE_USER_HOME } else { Join-Path ${'$'}HOME .gradle }
        # Maven wrappers download Maven from this repository
        Write-Host "##teamcity[setParameter name='env.MVNW_REPOURL' value='$mavenCentralMirror']"
        """,
)
