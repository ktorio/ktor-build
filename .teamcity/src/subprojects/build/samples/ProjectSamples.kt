package subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSSamples

val gradleProjects = listOf("client-mpp", "fullstack-mpp", "generic")

object ProjectSamples : Project({
    id("ProjectKtorSamples")
    name = "Samples"
    description = "Code samples"

    vcsRoot(VCSSamples)

    val projects = gradleProjects.map(::SampleProject)
    projects.forEach(::buildType)

    buildType {
        id("KtorSamplesValidate_All")
        name = "Validate all samples"

        dependencies {
            projects.mapNotNull { it.id }.forEach { id ->
                snapshot(id) {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
        }
    }
})

class SampleProject(projectName: String): BuildType({
    id("KtorSamplesValidate_${projectName.replace('-', '_')}")
    name = "Validate $projectName sample"

    vcs {
        root(VCSSamples)
    }

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
        tasks = "test"
        workingDir = relativeDir
    }
}

fun BuildSteps.acceptAndroidSDKLicense() = script {
    name = "Accept Android SDK license"
    scriptContent = "yes | JAVA_HOME=%env.JDK_18% %env.ANDROID_SDK_HOME%/tools/bin/sdkmanager --licenses"
}