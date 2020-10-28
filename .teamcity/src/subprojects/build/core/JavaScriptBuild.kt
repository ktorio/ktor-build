package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*

class JavaScriptBuild(private val JSEntry: JSEntry) : BuildType({
    id("KtorMatrixJavaScript_${JSEntry.name}".toExtId())
    name = "JavaScript on ${JSEntry.name}"
    artifactRules = formatArtifactsString("+:**/build/**/*.jar", junitReportArtifact, memoryReportArtifact)
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
    generatedBuilds[JSEntry.name] = this
})

private fun GradleBuildStep.setupDockerForJavaScriptTests(JSEntry: JSEntry) {
    dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
    dockerPull = true
    dockerImage = JSEntry.dockerContainer
}
