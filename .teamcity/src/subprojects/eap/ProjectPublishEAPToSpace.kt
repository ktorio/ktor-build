package subprojects.eap

import jetbrains.buildServer.configs.kotlin.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

object ProjectPublishEAPToSpace : Project({
    id("ProjectKtorPublishEAPToSpace")
    name = "Release Ktor EAP"
    description = "Publish on a nightly basis EAP branches to Space"


    params {
        defaultGradleParams()
        param("env.SIGN_KEY_ID", value = "")
        password("env.PUBLISHING_USER", value = "%space.packages.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.secret%")
        param("env.PUBLISHING_URL", value = "%space.packages.url%")
    }

    buildType(SetBuildNumber)
    buildType(PublishCustomTaskToSpace)

    val builds = listOf(
        PublishJvmToSpace,
        PublishJSToSpace,
        PublishWindowsNativeToSpace,
        PublishLinuxNativeToSpace,
        PublishMacOSNativeToSpace,
        PublishAndroidNativeToSpace,
    )
    builds.forEach(::buildType)

    publishAllEAPBuild = buildType {
        id("KtorPublish_AllEAP")
        name = "Publish All EAPs"
        type = BuildTypeSettings.Type.COMPOSITE
        buildNumberPattern = SetBuildNumber.depParamRefs.buildNumber.ref
        vcs {
            root(VCSCoreEAP)
        }
        triggers {
            nightlyEAPBranchesTrigger()
        }
        dependencies {
            builds.mapNotNull { it.id }.forEach { id ->
                snapshot(id) {
                    reuseBuilds = ReuseBuilds.NO
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                }
            }
        }
    }
})


