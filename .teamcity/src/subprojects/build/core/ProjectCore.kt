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

    val OsJdk = operatingSystems.flatMap { os ->
        jdkVersions.map { jdk -> OSJDKEntry(os, jdk) }
    }

    val apiCheck = APICheckBuild
    val osJdkBuilds = OsJdk.map(::CoreBuild)
    val nativeBuilds = operatingSystems.map(::NativeBuild)
    val javaScriptBuilds = javaScriptEngines.map(::JavaScriptBuild)
    val stressTestBuilds = stressTests.map(::StressTestBuild)

    val allBuilds = osJdkBuilds + nativeBuilds + javaScriptBuilds + stressTestBuilds + apiCheck

    allBuilds.forEach(::buildType)

    buildType {
        allowExternalStatus = true
        createCompositeBuild("KtorCore_All", "Build All Core", VCSCore, allBuilds, withTrigger = true)

        features {
            githubCommitStatusPublisher(VCSSamples.id.toString())
        }
    }

    buildType(CodeStyleVerify)
})

fun BuildType.createCompositeBuild(buildId: String, buildName: String, vcsRoot: VcsRoot, builds: List<BuildType>, withTrigger: Boolean = false) {
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

fun Requirements.require(os: String, minMemoryMB: Int = -1) {
    contains("teamcity.agent.jvm.os.name", os)

    if (minMemoryMB != -1) {
        noLessThan("teamcity.agent.hardware.memorySizeMb", minMemoryMB.toString())
    }
}
