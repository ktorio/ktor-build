package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

class WasmJsBuild(private val jsEntry: JSEntry) : BuildType({
    id("KtorMatrixWasmJs_${jsEntry.name}".toId())
    name = "WasmJS on ${jsEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    params {
        extraGradleParams()
    }

    cancelPreviousBuilds()
    steps {
        gradle {
            name = "Build Wasm Js"
            tasks = "cleanWasmJsTest wasmJsTest"
            gradleParams = "--continue --info -Penable-js-tests $GradleParams"
            setupDockerForJavaScriptTests(jsEntry)
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux)
    }
})
