package subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*

data class SampleProjectSettings(val projectName: String, val vcsRoot: VcsRoot, val gradleFile: String = "build.gradle")

val gradleProjects = listOf(
    SampleProjectSettings("client-mpp", VCSSamples),
    SampleProjectSettings("fullstack-mpp", VCSSamples),
    SampleProjectSettings("generic", VCSSamples),
    SampleProjectSettings("get-started", VCSGetStartedSample),
    SampleProjectSettings("gradle-sample", VCSGradleSample),
    SampleProjectSettings("maven-sample", VCSMavenSample),
    SampleProjectSettings("http-api-sample", VCSHttpApiSample),
    SampleProjectSettings("websockets-chat-sample", VCSWebSocketsChatSample),
    SampleProjectSettings("website-sample", VCSWebsiteSample),
    SampleProjectSettings("graalvm", VCSSamples, "build.gradle.kts")
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
        validateSamples(sample.projectName, sample.gradleFile)
    }
})

fun BuildSteps.validateSamples(relativeDir: String, gradleFile: String) {
    gradle {
        buildFile = gradleFile
        name = "Build"
        tasks = "clean build"
        workingDir = relativeDir
    }
    gradle {
        buildFile = gradleFile
        name = "Test"
        tasks = "allTests"
        workingDir = relativeDir
    }
}

fun BuildSteps.acceptAndroidSDKLicense() = script {
    name = "Accept Android SDK license"
    scriptContent = "yes | JAVA_HOME=%env.JDK_18% %env.ANDROID_SDK_HOME%/tools/bin/sdkmanager --licenses"
}
