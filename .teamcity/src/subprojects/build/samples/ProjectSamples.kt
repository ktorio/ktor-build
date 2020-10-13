package subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSSamples

val gradleProjects = listOf("client-mpp", "fullstack-mpp", "generic")

object ProjectSamples : Project({
    id("ProjectKtorSamples")
    name = "Samples"
    description = "Code samples"

    vcsRoot(VCSSamples)

    for (name in gradleProjects) {
        buildType(SampleProject(name))
    }
})

class SampleProject(projectName: String): BuildType({
    id("KtorSamplesValidate_${projectName}")
    name = "Build and test $projectName sample"

    vcs {
        root(VCSSamples)
    }

    steps {
        acceptAndroidSDKLicense()
        validateSample(projectName)
    }
})

fun BuildSteps.validateSample(relativeDir: String) {
    gradle {
        name = "Build"
        tasks = "clean build"
        workingDir = relativeDir
    }
    gradle {
        name = "Test"
        tasks = "test"
        workingDir = relativeDir
    }
}

fun BuildSteps.acceptAndroidSDKLicense() = script {
    name = "Accept Android SDK license"
    scriptContent = "yes | %env.ANDROID_SDK_HOME%/tools/bin/sdkmanager --licenses"
}