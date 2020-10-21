package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*

class JavaScriptBuild(private val javaScriptEngine: JavaScriptEngine) : BuildType({
    id("KtorMatrixJavaScript_${javaScriptEngine.name}".toExtId())
    name = "JavaScript on ${javaScriptEngine.name}"
    artifactRules = "+:**/build/**/*.jar\n$reportArtifacts"
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
            setupDockerForJavaScriptTests(javaScriptEngine)
        }
        gradle {
            name = "Build"
            tasks = "clean build --no-parallel --continue --info -Penable-js-tests"
            setupDockerForJavaScriptTests(javaScriptEngine)
        }
    }
    features {
        monitorPerformance()
    }
    requirements {
        require(os = "Linux", minMemoryDB = 7000)
    }
})

private fun GradleBuildStep.setupDockerForJavaScriptTests(javaScriptEngine: JavaScriptEngine) {
    dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
    dockerPull = true
    dockerImage = javaScriptEngine.dockerContainer
}
