package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

class JavaScriptBuild(private val jsEntry: JSEntry) : BuildType({
    id("KtorMatrixJavaScript_${jsEntry.name}".toId())
    name = "JavaScript on ${jsEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    triggers {
        onBuildTargetChanges(BuildTarget.JS)
    }

    steps {
        gradle {
            name = "Build Js"
            tasks = "cleanJsTest jsTest --no-parallel --continue --info -Penable-js-tests"
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
