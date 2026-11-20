package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.gradle.*

class WasmJsBuild(
    private val jsEntry: JSEntry,
    private val repo: KtorRepository = Core,
) : BuildType({
    id(repo.id("KtorMatrixWasmJs_${jsEntry.name}".toId()))
    name = repo.name("WasmJS on ${jsEntry.name}")
    val artifactsToPublish = formatArtifacts(
        "+:**/build/**/*.jar",
        "+:**/build/**/yarn.lock",
    )
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact, testServerArtifact, kotlinDaemonGcLogArtifact)

    vcs {
        root(repo.vcsRoot)
    }

    params {
        extraGradleParams()
        splitStepsParam()
    }

    useGradleEnvironment(Agents.OS.Linux, GradleUserHome.Light)
    cancelPreviousBuilds()

    runInContainer(jsEntry)

    steps {
        val commonGradleParams = "--info $GradleParams"

        // Compile separately to not run Kotlin daemon and browsers at the same time
        gradle {
            name = "Compile Wasm Js tests"
            tasks = "wasmJsTestTestDevelopmentExecutableCompileSync"
            gradleParams = commonGradleParams
            ifSplitSteps()
        }
        gradle {
            name = "Test Wasm Js"
            tasks = "cleanWasmJsTest wasmJsTest -x:ktor-client-auth:wasmJsBrowserTest"
            gradleParams = "--continue -Penable-js-tests $commonGradleParams"
        }
    }

    defaultBuildFeatures()
})
