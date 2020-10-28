package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.core.*
import kotlin.require

class PublishMavenBuild(private val publishingData: PublishingData) : BuildType({
    id("KtorPublishMavenBuild_${publishingData.buildName}".toExtId())
    name = "Publish ${publishingData.buildName} to Maven"
    vcs {
        root(VCSCore)
    }
    steps {
        gradle {
            name = "Parallel assemble"
            tasks = publishingData.gradleTasks.joinToString(" ")
        }
    }
    dependencies {
        val buildId = publishingData.buildData.id
        artifacts(buildId) {
            buildRule = lastSuccessful()
            artifactRules = publishingData.buildData.artifacts
        }
    }
    requirements {
        require(publishingData.operatingSystem)
    }
})

