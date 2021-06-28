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
    buildType(PublishJSToNPM)
    buildType(PublishWindowsNativeToMaven)
    buildType(PublishLinuxNativeToMaven)
    buildType(PublishMacOSNativeToMaven)

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
                PublishJSToNPM,
                PublishWindowsNativeToMaven,
                PublishLinuxNativeToMaven,
                PublishMacOSNativeToMaven
            )
        )
    }
})
