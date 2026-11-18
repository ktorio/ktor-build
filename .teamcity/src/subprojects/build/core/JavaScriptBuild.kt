package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
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
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(repo.vcsRoot)
    }

    params {
        extraGradleParams()
    }

    cancelPreviousBuilds()
    useGradleEnvironment(Agents.OS.Linux, GradleUserHome.Light)

    steps {
        gradle {
            name = "Build Js"
            tasks = "cleanJsTest jsTest -x:ktor-client-auth:jsBrowserTest"
            gradleParams = "-Penable-js-tests --continue --info $GradleParams"
            setupDockerForJavaScriptTests(jsEntry)
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux)
    }
})

internal fun GradleBuildStep.setupDockerForJavaScriptTests(jsEntry: JSEntry) {
    dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
    dockerPull = true
    dockerImage = jsEntry.dockerContainer
    dockerRunParameters = "-v %env.GRADLE_USER_HOME%:%env.GRADLE_USER_HOME%"
    gradleParams = "$gradleParams -g %env.GRADLE_USER_HOME%"
}
