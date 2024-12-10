package subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
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
    val buildSystem: BuildSystem = BuildSystem.GRADLE,
    val standalone: Boolean = false
)

val sampleProjects = listOf(
    SampleProjectSettings("chat", VCSSamples),
    SampleProjectSettings("client-mpp", VCSSamples),
    SampleProjectSettings("client-multipart", VCSSamples),
    SampleProjectSettings("client-tools", VCSSamples),
    SampleProjectSettings("di-kodein", VCSSamples),
    SampleProjectSettings("filelisting", VCSSamples),
    SampleProjectSettings("fullstack-mpp", VCSSamples),
    SampleProjectSettings("graalvm", VCSSamples),
    SampleProjectSettings("httpbin", VCSSamples),
    SampleProjectSettings("kweet", VCSSamples),
    SampleProjectSettings("location-header", VCSSamples),
    SampleProjectSettings("maven-google-appengine-standard", VCSSamples, buildSystem = BuildSystem.MAVEN),
    SampleProjectSettings("redirect-with-exception", VCSSamples),
    SampleProjectSettings("reverse-proxy", VCSSamples),
    SampleProjectSettings("reverse-proxy-ws", VCSSamples),
    SampleProjectSettings("rx", VCSSamples),
    SampleProjectSettings("sse", VCSSamples),
    SampleProjectSettings("structured-logging", VCSSamples),
    SampleProjectSettings("version-diff", VCSSamples),
    SampleProjectSettings("youkube", VCSSamples)
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
            name = "Build Server"
            tasks = "build"
            workingDir = "server"
        }

        gradle {
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
            BuildSystem.GRADLE -> buildGradleSample(sample.projectName, sample.standalone)
        }
    }
})

fun BuildSteps.buildGradleSample(relativeDir: String, standalone: Boolean) {
    gradle {
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
        pomLocation = "$relativeDir/pom.xml"
    }
}

fun BuildSteps.acceptAndroidSDKLicense() = script {
    name = "Accept Android SDK license"
    scriptContent = "yes | JAVA_HOME=%env.JDK_18% %env.ANDROID_SDK_HOME%/tools/bin/sdkmanager --licenses"
}
