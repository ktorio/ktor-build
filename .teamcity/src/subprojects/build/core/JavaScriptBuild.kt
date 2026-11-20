package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.gradle.*

class JavaScriptBuild(
    private val jsEntry: JSEntry,
    private val repo: KtorRepository = Core,
) : BuildType({
    id(repo.id("KtorMatrixJavaScript_${jsEntry.name}".toId()))
    name = repo.name("JavaScript on ${jsEntry.name}")
    val artifactsToPublish = formatArtifacts(
        "+:**/build/**/*.jar",
        "+:**/build/**/yarn.lock",
    )
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact, testServerArtifact)

    vcs {
        root(repo.vcsRoot)
    }

    params {
        extraGradleParams()
    }

    cancelPreviousBuilds()
    useGradleEnvironment(Agents.OS.Linux, GradleUserHome.Light)

    runInContainer(jsEntry)

    steps {
        val commonGradleParams = "--info $GradleParams"

        // Compile separately to not run Kotlin daemon and browsers at the same time
        gradle {
            name = "Compile Js tests"
            tasks = "jsTestTestDevelopmentExecutableCompileSync"
            gradleParams = commonGradleParams
        }
        gradle {
            name = "Test Js"
            tasks = "cleanJsTest jsTest -x:ktor-client-auth:jsBrowserTest"
            gradleParams = "-Penable-js-tests --continue $commonGradleParams"
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux)
    }
})

private const val GradleUserHomeVolume = "-v %env.GRADLE_USER_HOME%:%env.GRADLE_USER_HOME%"

internal fun GradleBuildStep.setupDockerForJavaScriptTests(jsEntry: JSEntry) {
    dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
    dockerPull = true
    dockerImage = jsEntry.dockerContainer
    dockerRunParameters = GradleUserHomeVolume
    gradleParams = "$gradleParams -g %env.GRADLE_USER_HOME%"
}

/** Runs all build steps in a single container, so the Gradle daemon is shared between them. */
internal fun BuildType.runInContainer(jsEntry: JSEntry) {
    features {
        runInDocker {
            dockerImagePlatform = RunInDockerBuildFeature.ImagePlatform.Linux
            dockerPull = true
            dockerImage = jsEntry.dockerContainer
            dockerRunParameters = GradleUserHomeVolume
        }
    }
}
