package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

class WasmJsBuild(private val jsEntry: JSEntry) : BuildType({
    id("KtorMatrixWasmJs_${jsEntry.name}".toExtId())
    name = "WasmJS on ${jsEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
    vcs {
        root(VCSCore)
    }
    steps {

        gradle {
            name = "Build Wasm Js"
            tasks = "cleanWasmJsTest wasmJsTest --no-parallel --continue --info -Penable-js-tests"
            buildFile = "build.gradle.kts"
            setupDockerForJavaScriptTests(jsEntry)
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        require(os = "Linux", minMemoryMB = 7000)
    }
    if (jsEntry == js) {
        wasmJsBuild = this
    }
})