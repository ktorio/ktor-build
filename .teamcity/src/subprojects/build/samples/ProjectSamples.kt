package subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*

enum class BuildSystem {
    MAVEN,
    GRADLE
}

data class SampleProjectSettings(
    val projectName: String,
    val vcsRoot: VcsRoot,
    val buildFile: String = "build.gradle",
    val buildSystem: BuildSystem = BuildSystem.GRADLE,
    val standalone: Boolean = false
)

val sampleProjects = listOf(
    SampleProjectSettings("chat", VCSSamples),
    SampleProjectSettings("client-mpp", VCSSamples),
    SampleProjectSettings("client-multipart", VCSSamples),
    SampleProjectSettings("client-tools", VCSSamples),
    SampleProjectSettings("di-kodein", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("filelisting", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("fullstack-mpp", VCSSamples),
    SampleProjectSettings("graalvm", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("httpbin", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("kweet", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("location-header", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("maven-google-appengine-standard", VCSSamples, buildSystem = BuildSystem.MAVEN),
    SampleProjectSettings("redirect-with-exception", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("reverse-proxy", VCSSamples,"build.gradle.kts"),
    SampleProjectSettings("reverse-proxy-ws", VCSSamples,"build.gradle.kts"),
    SampleProjectSettings("rx", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("sse", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("structured-logging", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("version-diff", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("youkube", VCSSamples, "build.gradle.kts")
)

/**
 * Custom Samples
 */
val WebSocketChatSample = SampleProjectSettings("websockets-chat-sample", VCSWebSocketsChatSample, standalone = true)

object ProjectSamples : Project({
    id("ProjectKtorSamples")
    name = "Samples"
    description = "Code samples"

    val projects = sampleProjects.map(::SampleProject)
    projects.forEach(::buildType)

    buildType(WebSocketSample)

    samplesBuild = buildType {
        createCompositeBuild(
            "KtorSamplesValidate_All",
            "Validate all samples",
            VCSSamples,
            projects + WebSocketSample,
            withTrigger = TriggerType.ALL_BRANCHES
        )
    }
})

object WebSocketSample: BuildType({
    val sample = WebSocketChatSample
    id("KtorSamplesValidate_${sample.projectName.replace('-', '_')}")
    name = "Validate ${sample.projectName} sample"

    vcs {
        root(sample.vcsRoot)
    }

    params {
        param("env.ANDROID_SDK_HOME", "%android-sdk.location%")
    }

    defaultBuildFeatures(sample.vcsRoot.id.toString())

    steps {
        acceptAndroidSDKLicense()

        gradle {
            buildFile = sample.buildFile
            name = "Build Server"
            tasks = "build"
            workingDir = "server"
        }

        gradle {
            buildFile = sample.buildFile
            name = "Build Client"
            tasks = "build"
            workingDir = "client"
        }
    }
})

class SampleProject(sample: SampleProjectSettings) : BuildType({
    id("KtorSamplesValidate_${sample.projectName.replace('-', '_')}")
    name = "Validate ${sample.projectName} sample"

    vcs {
        root(sample.vcsRoot)
    }

    params {
        param("env.ANDROID_SDK_HOME", "%android-sdk.location%")
    }

    defaultBuildFeatures(sample.vcsRoot.id.toString())

    steps {
        acceptAndroidSDKLicense()

        when (sample.buildSystem) {
            BuildSystem.MAVEN -> buildMavenSample(sample.projectName)
            BuildSystem.GRADLE -> buildGradleSample(sample.projectName, sample.buildFile, sample.standalone)
        }
    }
})

fun BuildSteps.buildGradleSample(relativeDir: String, gradleFile: String, standalone: Boolean) {
    gradle {
        buildFile = gradleFile
        name = "Build"
        tasks = "build"

        if (!standalone) {
            workingDir = relativeDir
        }
    }
}


fun BuildSteps.buildMavenSample(relativeDir: String) {
    maven {
        name = "Test"
        goals = "test"
        workingDir = relativeDir
    }
}

fun BuildSteps.acceptAndroidSDKLicense() = script {
    name = "Accept Android SDK license"
    scriptContent = "yes | JAVA_HOME=%env.JDK_18% %env.ANDROID_SDK_HOME%/tools/bin/sdkmanager --licenses"
}
