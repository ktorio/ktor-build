package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.core.*
import subprojects.release.*

object ProjectPublishing : Project({
    id("ProjectKtorPublishing")
    name = "Publishing"
    description = "Publish artifacts to repositories"

    buildType(PublishJvmToMaven)
    buildType(PublishJSToMaven)
    buildType(PublishWasmJsToMaven)
    buildType(PublishWindowsNativeToMaven)
    buildType(PublishLinuxNativeToMaven)
    buildType(PublishMacOSNativeToMaven)

    buildType(PublishCustomTaskToMaven)

    params {
        configureReleaseVersion()
    }

    publishAllBuild = buildType {
        createCompositeBuild(
            "KtorPublish_All",
            "Publish All",
            VCSCore,
            listOf(
                PublishJvmToMaven,
                PublishJSToMaven,
                PublishWasmJsToMaven,
                PublishWindowsNativeToMaven,
                PublishLinuxNativeToMaven,
                PublishMacOSNativeToMaven
            )
        )
    }
})
