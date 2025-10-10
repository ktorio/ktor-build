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
    val standalone: Boolean = false,
    val withAndroidSdk: Boolean = false,
)

val sampleProjects = listOf(
    SampleProjectSettings("chat", VCSSamples),
    SampleProjectSettings("client-mpp", VCSSamples, withAndroidSdk = true),
    SampleProjectSettings("client-multipart", VCSSamples),
    SampleProjectSettings("client-tools", VCSSamples),
    SampleProjectSettings("di-kodein", VCSSamples),
    SampleProjectSettings("filelisting", VCSSamples),
    SampleProjectSettings("fullstack-mpp", VCSSamples),
    SampleProjectSettings("graalvm", VCSSamples),
    SampleProjectSettings("httpbin", VCSSamples),
    SampleProjectSettings("ktor-client-wasm", VCSSamples, withAndroidSdk = true),
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

object ProjectSamples : Project({
    id("ProjectKtorSamples")
    name = "Samples"
    description = "Code samples"

    val projects = sampleProjects.map(::SampleProject)
    projects.forEach(::buildType)

    samplesBuild = buildType {
        createCompositeBuild(
            "KtorSamplesValidate_All",
            "Validate all samples",
            VCSSamples,
            projects,
            withTrigger = TriggerType.ALL_BRANCHES
        )
    }
})

class SampleProject(sample: SampleProjectSettings) : BuildType({
    id("KtorSamplesValidate_${sample.projectName.replace('-', '_')}")
    name = "Validate ${sample.projectName} sample"

    vcs {
        root(sample.vcsRoot)
    }

    if (sample.withAndroidSdk) configureAndroidHome()
    defaultBuildFeatures(sample.vcsRoot.id.toString())

    steps {
        if (sample.withAndroidSdk) acceptAndroidSDKLicense()

        when (sample.buildSystem) {
            BuildSystem.MAVEN -> buildMavenSample(sample.projectName)
            BuildSystem.GRADLE -> buildGradleSample(sample.projectName, sample.standalone)
        }
    }
})

fun BuildSteps.buildGradleSample(relativeDir: String, standalone: Boolean) {
    script {
        name = "Check KTOR_VERSION and prepare environment"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            mkdir -p %system.teamcity.build.tempDir%
            
            # Create a flag file to indicate if KTOR_VERSION is set
            if [ -n "%env.KTOR_VERSION%" ] && [ "%env.KTOR_VERSION%" != "%%env.KTOR_VERSION%%" ]; then
                echo "KTOR_VERSION is set to %env.KTOR_VERSION%"
                touch %system.teamcity.build.tempDir%/ktor_version_set
            else
                echo "KTOR_VERSION is not set, using default versions"
                rm -f %system.teamcity.build.tempDir%/ktor_version_set
            fi
        """.trimIndent()
    }

    script {
        name = "Create Gradle init script for EAP"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            # Create the EAP init script (will only be used if the flag file exists)
            cat > %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts << 'EOL'
            gradle.allprojects {
                repositories {
                    maven { 
                        name = "KtorEAP"
                        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") 
                    }
                }
                
                configurations.all {
                    resolutionStrategy.eachDependency {
                        if (requested.group == "io.ktor") {
                            useVersion(System.getenv("KTOR_VERSION"))
                        }
                    }
                }
                
                afterEvaluate {
                    logger.lifecycle("Project " + project.name + ": Using Ktor EAP version " + System.getenv("KTOR_VERSION"))
                }
            }
            EOL
            
            # Create a simple no-op init script for the non-EAP case
            echo "// No EAP version set - default init script" > %system.teamcity.build.tempDir%/ktor-default.init.gradle.kts
        """.trimIndent()
    }

    script {
        name = "Build Sample"
        workingDir = if (standalone) "" else relativeDir
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
        scriptContent = """
            # Check if KTOR_VERSION is set by looking for the flag file
            if [ -f %system.teamcity.build.tempDir%/ktor_version_set ]; then
                echo "Using EAP init script with Ktor version %env.KTOR_VERSION%"
                ./gradlew build --init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts
            else
                echo "Using default init script (no EAP version)"
                ./gradlew build --init-script=%system.teamcity.build.tempDir%/ktor-default.init.gradle.kts
            fi
        """.trimIndent()
    }
}

fun BuildSteps.buildMavenSample(relativeDir: String) {
    script {
        name = "Prepare Maven settings"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            # Only create settings.xml if KTOR_VERSION is set
            if [ -n "%env.KTOR_VERSION:-%" ] && [ "%env.KTOR_VERSION:-%" != "%" ]; then
                mkdir -p %system.teamcity.build.tempDir%/.m2
                cat > %system.teamcity.build.tempDir%/.m2/settings.xml << EOF
                <settings>
                  <profiles>
                    <profile>
                      <id>ktor-eap</id>
                      <repositories>
                        <repository>
                          <id>ktor-eap</id>
                          <url>https://maven.pkg.jetbrains.space/public/p/ktor/eap</url>
                        </repository>
                      </repositories>
                      <properties>
                        <ktor.version>%env.KTOR_VERSION:-%</ktor.version>
                      </properties>
                    </profile>
                  </profiles>
                  <activeProfiles>
                    <activeProfile>ktor-eap</activeProfile>
                  </activeProfiles>
                </settings>
                EOF
                echo "Created Maven settings with EAP repository and Ktor version %env.KTOR_VERSION:-%"
            else
                echo "KTOR_VERSION not set, using default Maven settings"
            fi
        """.trimIndent()
    }

    maven {
        name = "Test"
        goals = "test"
        workingDir = relativeDir
        pomLocation = "$relativeDir/pom.xml"

        userSettingsPath = "%system.teamcity.build.tempDir%/.m2/settings.xml"

        runnerArgs = "-Dktor.version=%env.KTOR_VERSION:-%"
    }
}

fun BuildType.configureAndroidHome() {
    params {
        param("env.ANDROID_HOME", "%android-sdk.location%")
    }
}

fun BuildSteps.acceptAndroidSDKLicense() = script {
    name = "Accept Android SDK license"
    scriptContent = "yes | JAVA_HOME=${Env.JDK_LTS} %env.ANDROID_SDKMANAGER_PATH% --licenses"
}
