package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

class JavaScriptBuild(private val jsEntry: JSEntry) : BuildType({
    id("KtorMatrixJavaScript_${jsEntry.name}".toId())
    name = "JavaScript on ${jsEntry.name}"
    val artifactsToPublish = formatArtifacts(
        "+:**/build/**/*.jar",
        "**/build/*/package-lock.json",
    )
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    cancelPreviousBuilds()
    steps {
        gradle {
            name = "Build Js"
            tasks = "cleanJsTest jsTest --continue --info -Penable-js-tests"
            setupDockerForJavaScriptTests(jsEntry)
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(Agents.OS.Linux)
    }
})

internal fun GradleBuildStep.setupDockerForJavaScriptTests(jsEntry: JSEntry) {
    dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
    dockerPull = true
    dockerImage = jsEntry.dockerContainer
}
