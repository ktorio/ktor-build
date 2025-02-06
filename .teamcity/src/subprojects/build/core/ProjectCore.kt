package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.*

object ProjectCore : Project({
    id("ProjectKtorCore")
    name = "Core"
    description = "Ktor Core Framework"

    params {
        defaultGradleParams()
    }

    val jpmsCheck = JPMSCheckBuild
    val apiCheck = APICheckBuild
    val osJdkBuilds = osJdks.map(::JDKBuild)
    val nativeBuilds = NativeEntry.All.map(::NativeBuild)
    val javaScriptBuilds = javaScriptEngines.map(::JavaScriptBuild)
    val wasmJsBuilds = javaScriptEngines.map(::WasmJsBuild)
    val stressTestBuilds = stressTests.map(::StressTestBuild)

    val allBuilds = osJdkBuilds + nativeBuilds + javaScriptBuilds + apiCheck + jpmsCheck + wasmJsBuilds
    val allBuildsWithStress = allBuilds + stressTestBuilds

    allBuildsWithStress.forEach(::buildType)

    // Builds to be run manually on demand
    buildType(DependenciesCheckBuild())
    buildType(JDKBuild(OSJDKEntry(Agents.OS.Windows, JDKEntry.Java11)))

    buildType {
        allowExternalStatus = true
        createCompositeBuild(
            buildId = "KtorCore_All",
            buildName = "Build All Core",
            vcsRoot = VCSCore,
            builds = allBuilds,
            withTrigger = TriggerType.VERIFICATION,
            additionalTriggers = {
                // We want to detect flaky tests.
                // A test is marked as flaky if its result changes after retry.
                retryBuild {
                    attempts = 3
                    branchFilter = BranchFilter.DefaultBranch
                }
            }
        )
    }

    buildType(CodeStyleVerify)
})

enum class TriggerType {
    ALL_BRANCHES,
    VERIFICATION,
    NONE,
}

fun BuildType.createCompositeBuild(
    buildId: String,
    buildName: String,
    vcsRoot: VcsRoot,
    builds: List<BuildType>,
    withTrigger: TriggerType = TriggerType.NONE,
    additionalTriggers: Triggers.() -> Unit = {},
    buildNumber: String = "%build.counter%-%teamcity.build.branch%",
) {
    id(buildId)
    name = buildName
    type = BuildTypeSettings.Type.COMPOSITE
    buildNumberPattern = buildNumber

    vcs {
        root(vcsRoot)
    }

    triggers {
        when (withTrigger) {
            TriggerType.ALL_BRANCHES -> onChangeAllBranchesTrigger()
            TriggerType.VERIFICATION -> onChangeDefaultOrPullRequest()
            TriggerType.NONE -> {}
        }
        apply(additionalTriggers)
    }

    dependencies {
        builds.mapNotNull { it.id }.forEach { id ->
            snapshot(id) {
                onDependencyFailure = FailureAction.ADD_PROBLEM
                onDependencyCancel = FailureAction.CANCEL
                reuseBuilds = ReuseBuilds.SUCCESSFUL
            }
        }
    }

    defaultBuildFeatures(vcsRoot.id.toString())
}
