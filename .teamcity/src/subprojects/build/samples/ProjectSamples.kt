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
    val buildSystem: BuildSystem = BuildSystem.GRADLE
)

val sampleProjects = listOf(
    SampleProjectSettings("chat", VCSSamples),
    SampleProjectSettings("client-mpp", VCSSamples),
    SampleProjectSettings("client-multipart", VCSSamples),
    SampleProjectSettings("client-tools", VCSSamples),
    SampleProjectSettings("css-dsl", VCSSamples),
    SampleProjectSettings("di-kodein", VCSSamples),
    SampleProjectSettings("docker-image", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("file-listing", VCSSamples),
    SampleProjectSettings("fullstack-mpp", VCSSamples),
    SampleProjectSettings("graalvm", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("httpbin", VCSSamples),
    SampleProjectSettings("kweet", VCSSamples),
    SampleProjectSettings("location-header", VCSSamples),
    SampleProjectSettings("maven-google-appengine-standard", VCSSamples, buildSystem = BuildSystem.MAVEN),
    SampleProjectSettings("maven-netty", VCSSamples, buildSystem = BuildSystem.MAVEN),
    SampleProjectSettings("multiple-connectors", VCSSamples),
    SampleProjectSettings("native-client", VCSSamples),
    SampleProjectSettings("proguard", VCSSamples),
    SampleProjectSettings("redirect-with-exception", VCSSamples),
    SampleProjectSettings("reverse-proxy", VCSSamples),
    SampleProjectSettings("reverse-proxy-ws", VCSSamples),
    SampleProjectSettings("rx", VCSSamples),
    SampleProjectSettings("simulate-slow-server", VCSSamples),
    SampleProjectSettings("sse", VCSSamples),
    SampleProjectSettings("structured-logging", VCSSamples),
    SampleProjectSettings("version-diff", VCSSamples, "build.gradle.kts"),
    SampleProjectSettings("youkube", VCSSamples),

    SampleProjectSettings("get-started", VCSGetStartedSample),
    SampleProjectSettings("gradle-sample", VCSGradleSample),
    SampleProjectSettings("maven-sample", VCSMavenSample),
    SampleProjectSettings("http-api-sample", VCSHttpApiSample),
    SampleProjectSettings("websockets-chat-sample", VCSWebSocketsChatSample),
    SampleProjectSettings("website-sample", VCSWebsiteSample),
)

object ProjectSamples : Project({
    id("ProjectKtorSamples")
    name = "Samples"
    description = "Code samples"

    val projects = sampleProjects.map(::SampleProject)
    projects.forEach(::buildType)

    samplesBuild = buildType {
        createCompositeBuild("KtorSamplesValidate_All", "Validate all samples", VCSSamples, projects)
    }
})

class SampleProject(sample: SampleProjectSettings) : BuildType({
    id("KtorSamplesValidate_${sample.projectName.replace('-', '_')}")
    name = "Validate ${sample.projectName} sample"

    vcs {
        root(sample.vcsRoot)
    }

    defaultBuildFeatures(sample.vcsRoot.id.toString())

    steps {
        acceptAndroidSDKLicense()

        when (sample.buildSystem) {
            BuildSystem.MAVEN -> buildMavenSample(sample.projectName)
            BuildSystem.GRADLE -> buildGradleSample(sample.projectName, sample.buildFile)
        }
    }
})

fun BuildSteps.buildGradleSample(relativeDir: String, gradleFile: String) {
    gradle {
        buildFile = gradleFile
        name = "Build"
        tasks = "build"
        workingDir = relativeDir
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
