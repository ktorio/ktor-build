package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.publishing.*

class JavaScriptBuild(private val JSEntry: JSEntry) : BuildType({
    id("KtorMatrixJavaScript_${JSEntry.name}".toExtId())
    name = "JavaScript on ${JSEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
    vcs {
        root(VCSCore)
    }
    triggers {
        setupDefaultVcsTrigger()
    }
    steps {
        gradle {
            name = "Parallel assemble"
            tasks = "assemble --info -Penable-js-tests"
            setupDockerForJavaScriptTests(JSEntry)
        }
        gradle {
            name = "Build"
            tasks = "clean build --no-parallel --continue --info -Penable-js-tests"
            setupDockerForJavaScriptTests(JSEntry)
        }
    }
    features {
        monitorPerformance()
    }
    requirements {
        require(os = "Linux", minMemoryDB = 7000)
    }
    generatedBuilds[JSEntry.name] = BuildData(this.id!!, artifactsToPublish)
})

private fun GradleBuildStep.setupDockerForJavaScriptTests(JSEntry: JSEntry) {
    dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
    dockerPull = true
    dockerImage = JSEntry.dockerContainer
}
