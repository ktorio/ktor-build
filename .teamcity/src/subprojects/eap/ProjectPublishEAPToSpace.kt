package subprojects.eap

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*
import subprojects.release.publishing.*

object ProjectPublishEAPToSpace : Project( {
    id("ProjectKtorPublishEAPToSpace")
    name = "Release Ktor EAP"
    description = "Publish on a nightly basis EAP branches to Space"

    params {
        defaultTimeouts()
    }

    buildType(PublishJvmToSpace)
    buildType(PublishJSToSpace)
    buildType(PublishWindowsNativeToSpace)
    buildType(PublishLinuxNativeToSpace)
    buildType(PublishMacOSNativeToSpace)

    publishAllEAPBuild = buildType {
        createCompositeBuild(
            "KtorPublish_AllEAP",
            "Publish All EAPs",
            VCSCore,
            listOf(
                PublishJvmToSpace,
                PublishJSToSpace,
                PublishWindowsNativeToSpace,
                PublishLinuxNativeToSpace,
                PublishMacOSNativeToSpace
            )
        )
    }
})
