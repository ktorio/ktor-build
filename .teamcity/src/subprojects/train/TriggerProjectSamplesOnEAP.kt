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
            
            # Fetch latest Ktor Gradle Plugin version if needed
            if [ "${'$'}{USE_LATEST_KTOR_GRADLE_PLUGIN}" = "true" ]; then
                echo "Fetching latest Ktor Gradle Plugin version from Gradle Plugin Portal..."
                PLUGIN_API_URL="https://plugins.gradle.org/api/plugins/io.ktor.plugin"
                TEMP_PLUGIN_FILE=$(mktemp)
                
                if curl -fsSL --connect-timeout 5 --max-time 15 --retry 2 --retry-delay 2 "${'$'}PLUGIN_API_URL" -o "${'$'}TEMP_PLUGIN_FILE"; then
                    if command -v jq &> /dev/null; then
                        LATEST_PLUGIN_VERSION=$(jq -r '.versions[0].version // empty' "${'$'}TEMP_PLUGIN_FILE")
                    else
                        LATEST_PLUGIN_VERSION=$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "${'$'}TEMP_PLUGIN_FILE" | head -n 1)
                    fi
                    
                    if [ -n "${'$'}LATEST_PLUGIN_VERSION" ]; then
                        echo "Latest Ktor Gradle Plugin version: ${'$'}LATEST_PLUGIN_VERSION"
                        echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='${'$'}LATEST_PLUGIN_VERSION']"
                    else
                        echo "Failed to extract plugin version from Gradle Plugin Portal, using default behavior"
                        echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='']"
                    fi
                else
                    echo "Failed to fetch plugin version from Gradle Plugin Portal, using default behavior"
                    echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='']"
                fi
                
                rm -f "${'$'}TEMP_PLUGIN_FILE"
            fi
            
            cat > %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts << 'EOL'
            gradle.allprojects {
                repositories {
                    google()
                    mavenCentral()
                    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
                    maven {
                        name = "KtorEAP"
                        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
                        content {
                            includeGroup("io.ktor")
                        }
                    }
                }
                
                configurations.all {
                    resolutionStrategy {
                        eachDependency {
                            if (requested.group == "io.ktor") {
                                val ktorVersion = System.getenv("KTOR_VERSION")
                                if (ktorVersion.isNullOrBlank()) {
                                    throw GradleException("KTOR_VERSION environment variable is not set or is blank. Cannot resolve Ktor EAP dependencies.")
                                }
                                useVersion(ktorVersion)
                                logger.lifecycle("Forcing Ktor dependency " + requested.name + " to use EAP version: " + ktorVersion)
                            }
                        }
                    }
                }

                afterEvaluate {
                    if (this == rootProject) {
                        logger.lifecycle("Project " + name + ": Using Ktor EAP version " + System.getenv("KTOR_VERSION"))
                        logger.lifecycle("Project " + name + ": EAP repository configured in settings.gradle.kts")
                        val pluginVersion = System.getenv("KTOR_GRADLE_PLUGIN_VERSION")
                        if (pluginVersion != null && pluginVersion.isNotEmpty()) {
                            logger.lifecycle("Project " + name + ": Using latest Ktor Gradle plugin version " + pluginVersion)
                        }
                    }
                }
            }
            EOL
        """.trimIndent()
    }
}

fun BuildSteps.createPluginSampleSettings(relativeDir: String, standalone: Boolean) {
    script {
        name = "Create Plugin Sample Settings"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            # Determine the settings file path
            SETTINGS_DIR="${if (!standalone) relativeDir else ""}"
            
            if [ -n "${'$'}{SETTINGS_DIR}" ]; then
                mkdir -p "${'$'}{SETTINGS_DIR}"
                SETTINGS_FILE="${'$'}{SETTINGS_DIR}/settings.gradle.kts"
            else
                SETTINGS_FILE="settings.gradle.kts"
            fi
            
            echo "Creating plugin sample settings at: ${'$'}{SETTINGS_FILE}"
            
            # Backup existing settings if present
            if [ -f "${'$'}{SETTINGS_FILE}" ]; then
                cp "${'$'}{SETTINGS_FILE}" "${'$'}{SETTINGS_FILE}.backup"
                echo "Backed up existing settings file"
            fi
            
            # Create settings that includes EAP repository configuration
            cat > "${'$'}{SETTINGS_FILE}" << 'EOF'
dependencyResolutionManagement {
    repositories {
        maven {
            name = "KtorEAP"
            url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
            content {
                includeGroup("io.ktor")
            }
        }
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

pluginManagement {
    repositories {
        maven {
            name = "KtorEAP"  
            url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
            content {
                includeGroup("io.ktor")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.ktor.plugin") {
                val pluginVersion = System.getenv("KTOR_GRADLE_PLUGIN_VERSION")
                if (pluginVersion != null && pluginVersion.isNotEmpty()) {
                    useVersion(pluginVersion)
                }
            }
        }
    }
}
EOF
            
            echo "Plugin sample settings created successfully"
        """.trimIndent()
    }
}

fun BuildSteps.restorePluginSampleSettings(relativeDir: String, standalone: Boolean) {
    script {
        name = "Restore Plugin Sample Settings"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            # Determine the settings file path
            SETTINGS_DIR="${if (!standalone) relativeDir else ""}"
            
            if [ -n "${'$'}{SETTINGS_DIR}" ]; then
                SETTINGS_FILE="${'$'}{SETTINGS_DIR}/settings.gradle.kts"
            else
                SETTINGS_FILE="settings.gradle.kts"
            fi
            
            echo "Restoring plugin sample settings at: ${'$'}{SETTINGS_FILE}"
            
            # Restore backup if it exists
            if [ -f "${'$'}{SETTINGS_FILE}.backup" ]; then
                mv "${'$'}{SETTINGS_FILE}.backup" "${'$'}{SETTINGS_FILE}"
                echo "Restored original settings file from backup"
            else
                # Remove the temporary settings file if no backup existed
                rm -f "${'$'}{SETTINGS_FILE}"
                echo "Removed temporary settings file"
            fi
        """.trimIndent()
    }
}

fun BuildSteps.buildEAPGradleSample(relativeDir: String, standalone: Boolean) {
    createEAPGradleInitScript()
    createPluginSampleSettings(relativeDir, standalone)

    gradle {
        name = "Build EAP Sample"
        tasks = "build"
        workingDir = if (!standalone) relativeDir else ""
        gradleParams = "--init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts"
        jdkHome = Env.JDK_LTS
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
    }

    restorePluginSampleSettings(relativeDir, standalone)
}

fun BuildSteps.buildEAPGradlePluginSample(relativeDir: String, standalone: Boolean) {
    createEAPGradleInitScript()
    createPluginSampleSettings(relativeDir, standalone)

    gradle {
        name = "Build EAP Build Plugin Sample"
        tasks = "build"
        workingDir = if (!standalone) "samples/$relativeDir" else ""
        useGradleWrapper = true
        gradleWrapperPath = if (!standalone) "../.." else ""
        gradleParams = "--init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts"
        jdkHome = Env.JDK_LTS
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
    }

    restorePluginSampleSettings(relativeDir, standalone)
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

fun BuildType.inheritSampleAgentRequirements(sampleSettings: SampleProjectSettings) {
    requirements {
        agent(Agents.OS.Linux, Agents.Arch.X64, Agents.MEDIUM)

        if (sampleSettings.withAndroidSdk) {
            exists("env.ANDROID_HOME")
        }
    }
}

fun BuildType.inheritGradlePluginSampleAgentRequirements(pluginSettings: BuildPluginSampleSettings) {
    requirements {
        agent(Agents.OS.Linux, hardwareCapacity = Agents.ANY)
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

                inheritGradlePluginSampleAgentRequirements(this@asEAPSampleConfig)

                params {
                    param("teamcity.build.skipDependencyBuilds", "true")
                    param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
                    param("env.USE_LATEST_KTOR_GRADLE_PLUGIN", "true")
                }

                dependencies {
                    dependency(versionResolver) {
                        snapshot {
                            onDependencyFailure = FailureAction.FAIL_TO_START
                            onDependencyCancel = FailureAction.CANCEL
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

            inheritSampleAgentRequirements(this@asEAPSampleConfig)

            if (this@asEAPSampleConfig.withAndroidSdk) {
                params {
                    param("env.ANDROID_HOME", "%android-sdk.location%")
                }
            }

            params {
                param("teamcity.build.skipDependencyBuilds", "true")
                param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            }

            dependencies {
                dependency(versionResolver) {
                    snapshot {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                        onDependencyCancel = FailureAction.CANCEL
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
            agent(Agents.OS.Linux, Agents.Arch.X64, Agents.MEDIUM)
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
            echo "##teamcity[setParameter name='ktor.eap.version' value='${'$'}LATEST_VERSION']"
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

        triggers {
            finishBuildTrigger {
                buildType = EapConstants.PUBLISH_EAP_BUILD_TYPE_ID
                successfulOnly = true
                branchFilter = "+:*"
            }
            finishBuildTrigger {
                buildType = EapConstants.PUBLISH_BUILD_PLUGIN_TYPE_ID
                successfulOnly = true
                branchFilter = "+:*"
            }
        }

        dependencies {
            allSampleBuilds.forEach { sampleBuild ->
                dependency(sampleBuild) {
                    snapshot {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                        onDependencyCancel = FailureAction.CANCEL
                    }
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
            executionTimeoutMin = 30
        }
    }
})
