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
    val standalone: Boolean = false
)

val buildPluginSamples = listOf(
    BuildPluginSampleSettings("ktor-docker-sample", VCSKtorBuildPlugins),
    BuildPluginSampleSettings("ktor-ksp-sample", VCSKtorBuildPlugins),
    BuildPluginSampleSettings("ktor-native-image-sample", VCSKtorBuildPlugins),
    BuildPluginSampleSettings("ktor-server-sample", VCSKtorBuildPlugins)
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
        buildGradlePluginSample(sample.projectName, sample.standalone)
    }
})

fun BuildSteps.buildGradlePluginSample(relativeDir: String, standalone: Boolean) {
    script {
        name = "Check KTOR_VERSION and prepare environment"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            mkdir -p %system.teamcity.build.tempDir%
            
            # Create a flag file to indicate if KTOR_VERSION is set
            if [ -n "%env.KTOR_VERSION%" ]; then
                echo "KTOR_VERSION is set to %env.KTOR_VERSION%"
                touch %system.teamcity.build.tempDir%/ktor_version_set
            else
                echo "KTOR_VERSION is not set, using default versions"
                rm -f %system.teamcity.build.tempDir%/ktor_version_set
            fi
        """.trimIndent()
    }
    
    script {
        name = "Create Gradle init scripts"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            # Create the EAP init script (will only be used if KTOR_VERSION is set)
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
        name = "Build Plugin Sample"
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
        scriptContent = """
            cd ${if (standalone) "." else "samples/$relativeDir"}
            
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
