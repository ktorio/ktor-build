package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.build.*
import subprojects.build.samples.*

object EapConstants {
    const val PUBLISH_EAP_BUILD_TYPE_ID = "KtorPublish_AllEAP"
    const val PUBLISH_BUILD_PLUGIN_TYPE_ID = "KtorGradleBuildPlugin_Publish"
}

interface EAPSampleConfig {
    val projectName: String
    fun createEAPBuildType(): BuildType
}

fun BuildSteps.createEAPGradleInitScript() {
    script {
        name = "Create EAP Gradle init script"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            mkdir -p %system.teamcity.build.tempDir%
            
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
        """.trimIndent()
    }
}

fun BuildSteps.buildEAPGradleProject(
    projectName: String,
    standalone: Boolean,
    isPluginSample: Boolean = false
) {
    createEAPGradleInitScript()

    gradle {
        name = "Build EAP ${if (isPluginSample) "Build Plugin " else ""}Sample"
        tasks = "build"
        workingDir = when {
            standalone -> ""
            isPluginSample && !standalone -> "samples/$projectName"
            !standalone -> projectName
            else -> ""
        }
        gradleParams = "--init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts"
        useGradleWrapper = false
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
    }
}

fun BuildSteps.buildEAPGradleSample(relativeDir: String, standalone: Boolean) {
    buildEAPGradleProject(relativeDir, standalone, isPluginSample = false)
}

fun BuildSteps.buildEAPGradlePluginSample(relativeDir: String, standalone: Boolean) {
    buildEAPGradleProject(relativeDir, standalone, isPluginSample = true)
}

fun BuildSteps.buildEAPMavenSample(relativeDir: String) {
    script {
        name = "Create EAP Maven settings"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
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
                    <ktor.version>%env.KTOR_VERSION%</ktor.version>
                  </properties>
                </profile>
              </profiles>
              <activeProfiles>
                <activeProfile>ktor-eap</activeProfile>
              </activeProfiles>
            </settings>
            EOF
        """.trimIndent()
    }

    maven {
        name = "Test EAP Maven Sample"
        goals = "test"
        workingDir = relativeDir
        pomLocation = "$relativeDir/pom.xml"
        userSettingsPath = "%system.teamcity.build.tempDir%/.m2/settings.xml"
        runnerArgs = "-Dktor.version=%env.KTOR_VERSION%"
    }
}

fun BuildPluginSampleSettings.asEAPSampleConfig(versionResolver: BuildType): EAPSampleConfig =
    object : EAPSampleConfig {
        override val projectName: String = this@asEAPSampleConfig.projectName
        override fun createEAPBuildType(): BuildType {
            return BuildType {
                id("EAP_KtorBuildPluginSamplesValidate_${projectName.replace('-', '_')}")
                name = "EAP Validate $projectName sample"

                vcs {
                    root(VCSKtorBuildPluginsEAP)
                }

                requirements {
                    contains("teamcity.agent.jvm.os.name", "Linux")
                }

                params {
                    param("teamcity.build.skipDependencyBuilds", "true")
                    param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
                }

                dependencies {
                    dependency(versionResolver) {
                        snapshot {
                            onDependencyFailure = FailureAction.FAIL_TO_START
                        }
                    }
                }

                steps {
                    buildEAPGradlePluginSample(this@asEAPSampleConfig.projectName, this@asEAPSampleConfig.standalone)
                }

                failureConditions {
                    failOnText {
                        conditionType = BuildFailureOnText.ConditionType.CONTAINS
                        pattern = "No agents available to run"
                        failureMessage =
                            "No compatible agents found for ${this@asEAPSampleConfig.projectName} build plugin sample"
                        stopBuildOnFailure = true
                    }
                    failOnText {
                        conditionType = BuildFailureOnText.ConditionType.CONTAINS
                        pattern = "Build queue timeout"
                        failureMessage = "Build timed out waiting for compatible agent"
                        stopBuildOnFailure = true
                    }
                    executionTimeoutMin = 10
                }

                defaultBuildFeatures(VCSKtorBuildPluginsEAP.id.toString())
            }
        }
    }

fun SampleProjectSettings.asEAPSampleConfig(versionResolver: BuildType): EAPSampleConfig = object : EAPSampleConfig {
    override val projectName: String = this@asEAPSampleConfig.projectName
    override fun createEAPBuildType(): BuildType {
        return BuildType {
            id("EAP_KtorSamplesValidate_${projectName.replace('-', '_')}")
            name = "EAP Validate $projectName sample"

            vcs {
                root(VCSSamples)
            }

            requirements {
                contains("teamcity.agent.jvm.os.name", "Linux")
                if (this@asEAPSampleConfig.withAndroidSdk) {
                    exists("env.ANDROID_HOME")
                }
            }

            params {
                param("teamcity.build.skipDependencyBuilds", "true")
                param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            }

            if (this@asEAPSampleConfig.withAndroidSdk) {
                params {
                    param("env.ANDROID_HOME", "%android-sdk.location%")
                }
            }

            dependencies {
                dependency(versionResolver) {
                    snapshot {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                    }
                }
            }

            steps {
                if (this@asEAPSampleConfig.withAndroidSdk) {
                    script {
                        name = "Accept Android SDK license"
                        scriptContent = "yes | JAVA_HOME=${Env.JDK_LTS} %env.ANDROID_SDKMANAGER_PATH% --licenses"
                    }
                }

                when (this@asEAPSampleConfig.buildSystem) {
                    BuildSystem.MAVEN -> buildEAPMavenSample(this@asEAPSampleConfig.projectName)
                    BuildSystem.GRADLE -> buildEAPGradleSample(
                        this@asEAPSampleConfig.projectName,
                        this@asEAPSampleConfig.standalone
                    )
                }
            }

            failureConditions {
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "No agents available to run"
                    failureMessage = "No compatible agents found for ${this@asEAPSampleConfig.projectName} sample"
                    stopBuildOnFailure = true
                }
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "Build queue timeout"
                    failureMessage = "Build timed out waiting for compatible agent"
                    stopBuildOnFailure = true
                }
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "No suitable agents"
                    failureMessage = "No suitable agents available for ${this@asEAPSampleConfig.projectName} sample"
                    stopBuildOnFailure = true
                }
                executionTimeoutMin = 10
            }

            defaultBuildFeatures(VCSSamples.id.toString())
        }
    }
}

object TriggerProjectSamplesOnEAP : Project({
    id("TriggerProjectSamplesOnEAP")
    name = "EAP Validation"
    description = "Validate samples against EAP versions of Ktor"

    params {
        param("ktor.eap.version", "KTOR_VERSION")
    }

    val versionResolver = buildType {
        id("KtorEAPVersionResolver")
        name = "Set EAP Version for Tests"
        description = "Determines the EAP version to use for sample validation"

        vcs {
            root(VCSCoreEAP)
        }

        requirements {
            contains("teamcity.agent.jvm.os.name", "Linux")
        }

        params {
            defaultGradleParams()
            param("teamcity.build.skipDependencyBuilds", "true")
            param("teamcity.runAsFirstBuild", "true")
        }

        steps {
            script {
                name = "Get latest EAP version from Maven metadata"
                scriptContent = """
            #!/bin/bash
            set -e
            
            # Fetch the latest EAP version from the Ktor BOM metadata
            METADATA_URL="https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml"
            echo "Fetching metadata from ${'$'}METADATA_URL"
            
            # Create a temporary file for the metadata
            TEMP_FILE=$(mktemp)
            
            # Download the metadata file
            if ! curl -s "${'$'}METADATA_URL" -o "${'$'}TEMP_FILE"; then
                echo "Failed to download metadata from ${'$'}METADATA_URL"
                rm -f "${'$'}TEMP_FILE"
                exit 1
            fi
            
            # Extract the latest version using grep and sed
            # This pattern looks for a <latest>version</latest> tag
            LATEST_VERSION=$(grep -o '<latest>[^<]*</latest>' "${'$'}TEMP_FILE" | sed 's/<latest>\(.*\)<\/latest>/\1/')
            
            # Clean up temp file
            rm -f "${'$'}TEMP_FILE"
            
            if [ -z "${'$'}LATEST_VERSION" ]; then
                echo "Failed to extract latest version from metadata"
                exit 1
            fi
            
            echo "Latest Ktor EAP version: ${'$'}LATEST_VERSION"
            
            # Set build parameter directly (will be propagated to dependent builds)
            echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}LATEST_VERSION']"
            
            # Also set project-level parameter for reference
            echo "##teamcity[setParameter name='ktor.eap.version' value='${'$'}LATEST_VERSION' level='project']"
            echo "##teamcity[buildStatus text='Using Ktor EAP version: ${'$'}LATEST_VERSION']"
            """.trimIndent()
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "[ERROR]"
                failureMessage = "Error detected in build log"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "No agents available to run"
                failureMessage = "No compatible agents found for EAP version resolver"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 10
        }
    }

    val allEAPSamples: List<EAPSampleConfig> = buildPluginSamples.map { it.asEAPSampleConfig(versionResolver) } +
        sampleProjects.map { it.asEAPSampleConfig(versionResolver) }

    val allSampleBuilds = allEAPSamples.map { it.createEAPBuildType() }
    allSampleBuilds.forEach(::buildType)

    val samplePairs = allEAPSamples.zip(allSampleBuilds)

    val buildPluginSampleNames = buildPluginSamples.map { it.projectName }.toSet()

    val buildPluginSampleBuilds = samplePairs
        .filter { (config, _) -> config.projectName in buildPluginSampleNames }
        .map { (_, build) -> build }

    val regularSampleBuilds = samplePairs
        .filter { (config, _) -> config.projectName !in buildPluginSampleNames }
        .map { (_, build) -> build }

    val buildPluginComposite = buildType {
        id("EAP_KtorBuildPluginSamplesValidate_All")
        name = "EAP Validate all build plugin samples"
        type = BuildTypeSettings.Type.COMPOSITE

        params {
            param("env.USE_LATEST_KTOR_GRADLE_PLUGIN", "true")
        }

        requirements {
            contains("teamcity.agent.jvm.os.name", "Linux")
        }

        triggers {
            finishBuildTrigger {
                buildType = EapConstants.PUBLISH_BUILD_PLUGIN_TYPE_ID
                successfulOnly = true
                branchFilter = "+:*"
            }
        }

        dependencies {
            buildPluginSampleBuilds.forEach { sampleBuild ->
                dependency(sampleBuild) {
                    snapshot {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                    }
                }
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "No agents available to run"
                failureMessage = "No compatible agents found for build plugin samples composite"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 30
        }
    }

    val samplesComposite = buildType {
        id("EAP_KtorSamplesValidate_All")
        name = "EAP Validate all samples"
        type = BuildTypeSettings.Type.COMPOSITE

        triggers {
            finishBuildTrigger {
                buildType = EapConstants.PUBLISH_EAP_BUILD_TYPE_ID
                successfulOnly = true
                branchFilter = "+:*"
            }
        }

        dependencies {
            regularSampleBuilds.forEach { sampleBuild ->
                dependency(sampleBuild) {
                    snapshot {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                    }
                }
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "No agents available to run"
                failureMessage = "No compatible agents found for samples composite"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 30
        }
    }

    buildType {
        id("KtorEAPSamplesCompositeBuild")
        name = "Validate All Samples with EAP"
        description = "Run all samples against the EAP version of Ktor"
        type = BuildTypeSettings.Type.COMPOSITE

        params {
            defaultGradleParams()
            param("env.GIT_BRANCH", "%teamcity.build.branch%")
            param("teamcity.build.skipDependencyBuilds", "true")
        }

        features {
            notifications {
                notifierSettings = slackNotifier {
                    connection = "PROJECT_EXT_5"
                    sendTo = "#ktor-projects-on-eap"
                    messageFormat = verboseMessageFormat {
                        addStatusText = true
                    }
                }
                buildFailedToStart = true
                buildFailed = true
                buildFinishedSuccessfully = true
            }
        }

        dependencies {
            dependency(buildPluginComposite) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
            dependency(samplesComposite) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "No agents available to run"
                failureMessage = "No compatible agents found for main EAP samples composite"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "Build queue timeout"
                failureMessage = "EAP samples build timed out waiting for compatible agents"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 60
        }
    }
})
