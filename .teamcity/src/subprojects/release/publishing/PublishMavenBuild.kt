package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import subprojects.build.core.*
import java.lang.RuntimeException

class PublishMavenBuild(private val build: Build) : BuildType({
    id("KtorPublishMavenBuild_${build.name}".toExtId())
    name = "Publish ${build.name} to Maven"

    dependencies {
        val buildId = build.build?.id
        if (buildId != null) {
            artifacts(buildId) {
                buildRule = lastSuccessful()
                artifactRules = stripReportArtifacts(artifactRules)
            }
        } else {
            throw RuntimeException("Build ID not found for entry ${build.name}")
        }

    }
})

fun stripReportArtifacts(artifacts: String): String {
    return artifacts.replace("$junitReportArtifact\n", "")
        .replace("$memoryReportArtifact\n","")
}
