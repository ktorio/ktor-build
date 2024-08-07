package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.defaultTimeouts
import subprojects.build.*
import subprojects.release.publishing.*

object ProjectCore : Project({
    id("ProjectKtorCore")
    name = "Core"
    description = "Ktor Core Framework"

    params {
        defaultTimeouts()
    }

    val osJdks: List<OSJDKEntry> = listOf(
        OSJDKEntry(linux, java8),
        OSJDKEntry(linux, java11),
        OSJDKEntry(linux, java17),
        OSJDKEntry(windows, java11),
        OSJDKEntry(macOS, java8),
        OSJDKEntry(macOS, java11)
    )

    val jpmsCheck = JPMSCheckBuild
    val apiCheck = APICheckBuild
    val osJdkBuilds = osJdks.map(::CoreBuild)
    val nativeBuilds = operatingSystems.map(::NativeBuild)
    val javaScriptBuilds = javaScriptEngines.map(::JavaScriptBuild)
    val wasmJsBuilds = javaScriptEngines.map(::WasmJsBuild)
    val stressTestBuilds = stressTests.map(::StressTestBuild)

    val allBuilds = osJdkBuilds + nativeBuilds + javaScriptBuilds + apiCheck + jpmsCheck + wasmJsBuilds
    val allBuildsWithStress = allBuilds + stressTestBuilds

    allBuildsWithStress.forEach(::buildType)

    buildType(DependenciesCheckBuild())

    buildType {
        allowExternalStatus = true
        createCompositeBuild("KtorCore_All", "Build All Core", VCSCore, allBuilds, withTrigger = true)

        features {
            githubCommitStatusPublisher(VCSSamples.id.toString())
        }
    }

    buildType(CodeStyleVerify)
})

fun BuildType.createCompositeBuild(
    buildId: String,
    buildName: String,
    vcsRoot: VcsRoot,
    builds: List<BuildType>,
    withTrigger: Boolean = false
) {
    id(buildId)
    name = buildName
    type = BuildTypeSettings.Type.COMPOSITE
    buildNumberPattern = releaseVersion

    vcs {
        root(vcsRoot)
    }
    triggers {
        if (withTrigger) {
            onChangeAllBranchesTrigger()
        }
    }
    dependencies {
        builds.mapNotNull { it.id }.forEach { id ->
            snapshot(id) {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
    }
}

fun Requirements.require(os: String, osarch: String? = null, minMemoryMB: Int = -1) {
    contains("teamcity.agent.jvm.os.name", os)
    if (osarch != null) {
        contains("teamcity.agent.jvm.os.arch", osarch)
    }

    if (minMemoryMB != -1) {
        noLessThan("teamcity.agent.hardware.memorySizeMb", minMemoryMB.toString())
    }
}
