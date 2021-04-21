package subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*
import subprojects.release.publishing.*

data class SampleProjectSettings(val projectName: String, val vcsRoot: VcsRoot)

val gradleProjects = listOf(
    SampleProjectSettings("client-mpp", VCSSamples),
    SampleProjectSettings("fullstack-mpp", VCSSamples),
    SampleProjectSettings("generic", VCSSamples),
    SampleProjectSettings("ktor-get-started", VCSGetStartedSample),
    SampleProjectSettings("ktor-gradle-sample", VCSGradleSample),
    SampleProjectSettings("ktor-maven-sample", VCSMavenSample),
    SampleProjectSettings("ktor-http-api-sample", VCSHttpApiSample),
    SampleProjectSettings("ktor-websockets-chat-sample", VCSWebSocketsChatSample),
    SampleProjectSettings("ktor-website-sample", VCSWebsiteSample)
)

object ProjectSamples : Project({
    id("ProjectKtorSamples")
    name = "Samples"
    description = "Code samples"

    val projects = gradleProjects.map(::SampleProject)
    projects.forEach(::buildType)

    samplesBuild = buildType {
        createCompositeBuild("KtorSamplesValidate_All", "Validate all samples", VCSSamples, projects)
    }
})

class SampleProject(sample: SampleProjectSettings): BuildType({
    id("KtorSamplesValidate_${sample.projectName.replace('-', '_')}")
    name = "Validate ${sample.projectName} sample"

    vcs {
        root(sample.vcsRoot)
    }

    defaultBuildFeatures(sample.vcsRoot.id.toString())

    steps {
        acceptAndroidSDKLicense()
        validateSamples(sample.projectName)
    }
})

fun BuildSteps.validateSamples(relativeDir: String) {
    gradle {
        name = "Build"
        tasks = "clean build"
        workingDir = relativeDir
    }
    gradle {
        name = "Test"
        tasks = "allTests"
        workingDir = relativeDir
    }
}

fun BuildSteps.acceptAndroidSDKLicense() = script {
    name = "Accept Android SDK license"
    scriptContent = "yes | JAVA_HOME=%env.JDK_18% %env.ANDROID_SDK_HOME%/tools/bin/sdkmanager --licenses"
}
