package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.core.*

data class BuildData(val id: Id, val artifacts: String)
data class PublishingData(val buildName: String, val buildData: BuildData, val gradleTasks: List<String>, val operatingSystem: String)

object ProjectPublishing : Project({
    id("ProjectPublishing")
    name = "Publishing"
    description = "Publish artifacts to repositories"

    val publishingEntries = listOf(
        PublishingData(
            "JVM", buildData("${linux.name}${java11.name}"),
            listOf(
                "publishJvmPublicationToMavenRepository",
                "publishKotlinMultiplatformPublicationToMavenRepository",
                "publishMetadataPublicationToMavenRepository"
            ), linux.agentString
        ),
        PublishingData(
            "JavaScript", buildData(js.name),
            listOf(
                "publishJsPublicationToMavenRepository",
                "publishMetadataPublicationToMavenRepository"
            ), linux.agentString
        ),
        PublishingData(
            "Windows", buildData(windows.name),
            listOf(
                "publishMingwX64PublicationToMavenRepository",
                "publishMetadataPublicationToMavenRepository"
            ), windows.agentString
        ),
        PublishingData(
            "Linux", buildData(linux.name),
            listOf(
                "publishLinuxX64PublicationToMavenRepository",
                "publishMetadataPublicationToMavenRepository"
            ), linux.agentString
        ),
        PublishingData(
            "macOS", buildData(macOS.name),
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

    val allBuilds = publishingEntries.map(::PublishMavenBuild)

    allBuilds.forEach(::buildType)

    buildType {
        createCompositeBuild("KtorPublish_All", "Publish All", VCSCore, allBuilds)
    }
})

private fun buildData(buildConfiguration: String): BuildData {
    return generatedBuilds[buildConfiguration]
        ?: throw RuntimeException("Cannot find build data for $buildConfiguration")
}