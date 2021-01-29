package subprojects.eap

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*
import subprojects.release.publishing.*

object ProjectPublishEAPToSpace : Project({
    id("ProjectKtorPublishEAPToSpace")
    name = "Release Ktor EAP"
    description = "Publish on a nightly basis EAP branches to Space"


    params {
        defaultTimeouts()
        param("env.SIGN_KEY_ID", value = "")
        password("env.PUBLISHING_USER", value = "%space.packages.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.secret%")
        param("env.PUBLISHING_URL", value = "%space.packages.url%")
    }

    buildType(PublishJvmToSpace)
    buildType(PublishJSToSpace)
    buildType(PublishWindowsNativeToSpace)
    buildType(PublishLinuxNativeToSpace)
    buildType(PublishMacOSNativeToSpace)

    publishAllEAPBuild = buildType {
        id("KtorPublish_AllEAP")
        name = "Publish All EAPs"
        type = BuildTypeSettings.Type.COMPOSITE
        buildNumberPattern = eapVersion
        vcs {
            root(VCSCoreEAP)
        }
        triggers {
            nightlyEAPBranchesTrigger()
        }
        dependencies {
            val builds = listOf(
                PublishJvmToSpace,
                PublishJSToSpace,
                PublishWindowsNativeToSpace,
                PublishLinuxNativeToSpace,
                PublishMacOSNativeToSpace
            )
            builds.mapNotNull { it.id }.forEach { id ->
                snapshot(id) {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
        }
    }
})


