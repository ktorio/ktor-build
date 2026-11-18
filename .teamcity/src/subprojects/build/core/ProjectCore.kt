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

    features {
        feature {
            type = "ReportTab"
            param("type", "BuildReportTab")
            param("title", "Flaky Tests")
            param("startPage", "flaky-report.html")
        }
    }

    val verificationBuilds = createCoreVerificationBuilds(Core)
    val stressTestBuilds = stressTests.map(::StressTestBuild)

    (verificationBuilds.builds + stressTestBuilds).forEach(::buildType)

    buildType(verificationBuilds.jvmCompileBuild)
    // Builds to be run manually on demand
    buildType(DependenciesCheckBuild())
    buildType(NativeBuild(NativeEntry.MacOSX64))
    buildType(JDKBuild(
        OSJDKEntry(Agents.OS.Windows, JDKEntry.Java11),
        Core,
        verificationBuilds.jvmCompileBuild,
    ))

    buildType {
        allowExternalStatus = true
        createCompositeBuild(
            buildId = "KtorCore_All",
            buildName = "Build All Core",
            vcsRoot = VCSCore,
            builds = verificationBuilds.builds,
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
    buildType(CoreFlakyTestBuild)
    QUARANTINE_NATIVE_ENTRIES.forEach { buildType(CoreNativeFlakyTestBuild(it)) }
    buildType(CoreFlakyTestNotifier)
})

data class CoreVerificationBuilds(
    val jvmCompileBuild: JvmCompileBuild,
    val builds: List<BuildType>,
)

fun createCoreVerificationBuilds(
    repo: KtorRepository,
    gradleEnvironmentRepo: KtorRepository = repo,
): CoreVerificationBuilds {
    val jvmCompileBuild = JvmCompileBuild(repo, gradleEnvironmentRepo)
    val apiCheck = APICheckBuild(repo)
    val jpmsCheck = JPMSCheckBuild(repo)
    val osJdkBuilds = osJdks.map { JDKBuild(it, repo, jvmCompileBuild, gradleEnvironmentRepo) }
    val nativeBuilds = NativeEntry.Default.map { NativeBuild(it, repo) }
    val javaScriptBuilds = javaScriptEngines.map { JavaScriptBuild(it, repo) }
    val wasmJsBuilds = javaScriptEngines.map { WasmJsBuild(it, repo) }
    val builds = osJdkBuilds + nativeBuilds + javaScriptBuilds + apiCheck + jpmsCheck + wasmJsBuilds

    return CoreVerificationBuilds(jvmCompileBuild, builds)
}

enum class TriggerType {
    ALL_BRANCHES,
    VERIFICATION,
    PULL_REQUESTS,
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
            TriggerType.PULL_REQUESTS -> onChangePullRequests()
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

    defaultBuildFeatures()
}
