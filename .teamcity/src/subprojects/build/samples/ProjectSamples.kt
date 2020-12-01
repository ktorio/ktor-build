package subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*

val gradleProjects = listOf("client-mpp", "fullstack-mpp", "generic")

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

class SampleProject(projectName: String): BuildType({
    id("KtorSamplesValidate_${projectName.replace('-', '_')}")
    name = "Validate $projectName sample"

    vcs {
        root(VCSSamples)
    }

    defaultBuildFeatures(VCSSamples.id.toString())

    steps {
        acceptAndroidSDKLicense()
        validateSamples(projectName)
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
