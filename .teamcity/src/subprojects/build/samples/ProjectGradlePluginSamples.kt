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
        name = "Prepare and run Gradle"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            mkdir -p %system.teamcity.build.tempDir%
            
            # Only create non-empty init script if KTOR_VERSION is set
            if [ -n "%env.KTOR_VERSION:-%" ] && [ "%env.KTOR_VERSION:-%" != "%" ]; then
                echo "Creating Gradle init script for Ktor EAP version %env.KTOR_VERSION:-%"
                # Create the EAP init script
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
            else
                echo "// No EAP version set - empty init script" > %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts
            fi
            
            # Now run Gradle with the init script
            cd ${if (standalone) "." else "samples/$relativeDir"}
            ./gradlew build --init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts
        """.trimIndent()
    }
}
