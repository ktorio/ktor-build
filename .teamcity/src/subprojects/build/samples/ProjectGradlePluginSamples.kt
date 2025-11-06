package subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.VCSKtorBuildPlugins
import subprojects.build.core.TriggerType
import subprojects.build.core.createCompositeBuild
import subprojects.build.defaultBuildFeatures

data class BuildPluginSampleSettings(
    val projectName: String,
    val vcsRoot: VcsRoot,
)

val buildPluginSamples = listOf(
    BuildPluginSampleSettings("ktor-docker-sample", VCSKtorBuildPlugins),
    BuildPluginSampleSettings("ktor-fatjar-sample", VCSKtorBuildPlugins),
    BuildPluginSampleSettings("ktor-native-image-sample", VCSKtorBuildPlugins),
    BuildPluginSampleSettings("ktor-openapi-sample", VCSKtorBuildPlugins)
)

object ProjectBuildPluginSamples : Project({
    id("ProjectKtorBuildPluginSamples")
    name = "Build Plugin Samples"
    description = "Ktor Build Plugin Samples"

    val projects = buildPluginSamples.map(::BuildPluginSampleProject)
    projects.forEach(::buildType)

    buildType {
        createCompositeBuild(
            "KtorBuildPluginSamplesValidate_All",
            "Validate all build plugin samples",
            VCSKtorBuildPlugins,
            projects,
            withTrigger = TriggerType.ALL_BRANCHES
        )
    }
})

class BuildPluginSampleProject(sample: BuildPluginSampleSettings) : BuildType({
    id("KtorBuildPluginSamplesValidate_${sample.projectName.replace('-', '_')}")
    name = "Validate ${sample.projectName} sample"

    vcs {
        root(sample.vcsRoot)
    }

    defaultBuildFeatures(sample.vcsRoot.id.toString())

    steps {
        buildGradlePluginSample(sample.projectName)
    }
})

fun BuildSteps.buildGradlePluginSample(relativeDir: String) {
    gradle {
        name = "Build $relativeDir sample"
        tasks = "build"
        param("org.gradle.project.includeBuild", "samples/$relativeDir")
        workingDir = "."
    }
}
