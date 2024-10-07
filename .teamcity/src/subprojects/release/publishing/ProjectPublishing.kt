package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.*
import subprojects.*
import subprojects.build.core.*
import subprojects.release.*

object ProjectPublishing : Project({
    id("ProjectKtorPublishing")
    name = "Publishing"
    description = "Publish artifacts to repositories"

    val builds = listOf(
        PublishJvmToMaven,
        PublishJSToMaven,
        PublishWasmJsToMaven,
        PublishWindowsNativeToMaven,
        PublishLinuxNativeToMaven,
        PublishMacOSNativeToMaven,
        PublishAndroidNativeToMaven,
    )
    builds.forEach(::buildType)

    buildType(PublishCustomTaskToMaven)

    params {
        configureReleaseVersion()
    }

    publishAllBuild = buildType {
        createCompositeBuild(
            "KtorPublish_All",
            "Publish All",
            VCSCore,
            builds,
            withTrigger = TriggerType.NONE,
            buildNumber = releaseVersion,
        )
    }
})
