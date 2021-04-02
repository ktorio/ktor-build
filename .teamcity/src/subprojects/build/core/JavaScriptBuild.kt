package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

class JavaScriptBuild(private val jsEntry: JSEntry) : BuildType({
    id("KtorMatrixJavaScript_${jsEntry.name}".toExtId())
    name = "JavaScript on ${jsEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
    vcs {
        root(VCSCore)
    }
    triggers {
        onChangeAllBranchesTrigger()
    }
    steps {
        gradle {
            name = "Build"
            tasks = "cleanJsIrTest cleanJsLegacyTest jsIrTest jsLegacyTest --no-parallel --continue --info -Penable-js-tests"
            setupDockerForJavaScriptTests(jsEntry)
            buildFile = "build.gradle"
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        require(os = "Linux", minMemoryMB = 7000)
    }
    if (jsEntry == js) {
        jsBuild = this
    }
})

private fun GradleBuildStep.setupDockerForJavaScriptTests(JSEntry: JSEntry) {
    dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
    dockerPull = true
    dockerImage = JSEntry.dockerContainer
}
