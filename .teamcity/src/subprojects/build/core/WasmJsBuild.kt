package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

class WasmJsBuild(private val jsEntry: JSEntry) : BuildType({
    id("KtorMatrixWasmJs_${jsEntry.name}".toId())
    name = "WasmJS on ${jsEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    triggers {
        onBuildTargetChanges(BuildTarget.WasmJS)
    }

    steps {
        gradle {
            name = "Build Wasm Js"
            tasks = "cleanWasmJsTest wasmJsTest --no-parallel --continue --info -Penable-js-tests"
            setupDockerForJavaScriptTests(jsEntry)
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(linux)
    }
    if (jsEntry == js) {
        wasmJsBuild = this
    }
})
