package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*

data class PublishingData(val targetPlatform: String, val build: BuildType?, val gradleTasks: List<String>, val operatingSystem: String)

val publishingEntries = listOf(
    PublishingData(
        "JVM", jvmBuild,
        listOf(
            "publishJvmPublicationToMavenRepository",
            "publishKotlinMultiplatformPublicationToMavenRepository",
            "publishMetadataPublicationToMavenRepository"
        ), linux.agentString
    ),
    PublishingData(
        "JavaScript", jsBuild,
        listOf(
            "publishJsPublicationToMavenRepository",
            "publishMetadataPublicationToMavenRepository"
        ), linux.agentString
    ),
    PublishingData(
        "Windows", nativeWindowsBuild,
        listOf(
            "publishMingwX64PublicationToMavenRepository",
            "publishMetadataPublicationToMavenRepository"
        ), windows.agentString
    ),
    PublishingData(
        "Linux", nativeLinuxBuild,
        listOf(
            "publishLinuxX64PublicationToMavenRepository",
            "publishMetadataPublicationToMavenRepository"
        ), linux.agentString
    ),
    PublishingData(
        "macOS", nativeMacOSBuild,
        listOf(
            "publishIosArm32PublicationToMavenRepository",
            "publishIosArm64PublicationToMavenRepository",
            "publishIosX64PublicationToMavenRepository",
            "publishMacosX64PublicationToMavenRepository",
            "publishTvosArm64PublicationToMavenRepository",
            "publishTvosX64PublicationToMavenRepository",
            "publishWatchosArm32PublicationToMavenRepository",
            "publishWatchosArm64PublicationToMavenRepository",
            "publishWatchosX86PublicationToMavenRepository",
            "publishMetadataPublicationToMavenRepository"
        ), macOS.agentString
    )
)

object ProjectPublishing : Project({
    id("ProjectPublishing")
    name = "Publishing"
    description = "Publish artifacts to repositories"

    val allBuilds = publishingEntries.map(::PublishMavenBuild)

    allBuilds.forEach(::buildType)

    buildType {
        createCompositeBuild("KtorPublish_All", "Publish All", VCSCore, allBuilds)
    }
})
