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
            gradle.settingsEvaluated {
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        maven { 
                            name = "KtorEAP"
                            url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") 
                        }
                    }
                    
                    resolutionStrategy {
                        eachPlugin {
                            if (requested.id.id == "io.ktor.plugin") {
                                val pluginVersion = System.getenv("KTOR_GRADLE_PLUGIN_VERSION")
                                if (pluginVersion != null && pluginVersion.isNotEmpty()) {
                                    useVersion(pluginVersion)
                                    logger.lifecycle("Using latest Ktor Gradle plugin version from Plugin Portal: " + pluginVersion)
                                } else {
                                    logger.lifecycle("Using requested Ktor Gradle plugin version: " + requested.version)
                                }
                            }
                        }
                    }
                }
                
                dependencyResolutionManagement {
                    repositories {
                        maven { 
                            name = "KtorEAP"
                            url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
                        }
                        mavenCentral()
                    }
                }
            }
            
            gradle.allprojects {
                repositories {
                    clear()
                    maven { 
                        name = "KtorEAP"
                        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
                    }
                    mavenCentral()
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
                        cacheDynamicVersionsFor(0, "seconds")
                        cacheChangingModulesFor(0, "seconds")
                     }
                }

                
                afterEvaluate {
                    if (project == rootProject) {
                        logger.lifecycle("Project " + project.name + ": Using Ktor EAP version " + System.getenv("KTOR_VERSION"))
                        logger.lifecycle("Project " + project.name + ": EAP repository: https://maven.pkg.jetbrains.space/public/p/ktor/eap")
                        val pluginVersion = System.getenv("KTOR_GRADLE_PLUGIN_VERSION")
                        if (pluginVersion != null && pluginVersion.isNotEmpty()) {
                            logger.lifecycle("Project " + project.name + ": Using latest Ktor Gradle plugin version " + pluginVersion)
                        }
                    }
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

    script {
        name = "Verify Ktor BOM availability"
        scriptContent = """
        #!/bin/bash
        set -e
        
        KTOR_VERSION="%env.KTOR_VERSION%"
        BOM_URL="https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/${'$'}KTOR_VERSION/ktor-bom-${'$'}KTOR_VERSION.pom"
        
        echo "Checking BOM availability for Gradle build..."
        echo "BOM URL: ${'$'}BOM_URL"
        
        # Check if BOM is available with retries
        MAX_RETRIES=5
        RETRY_COUNT=0
        
        while [ ${'$'}RETRY_COUNT -lt ${'$'}MAX_RETRIES ]; do
            echo "Attempt $((RETRY_COUNT + 1))/${'$'}MAX_RETRIES: Checking BOM availability..."
            
            # Use reliable HTTP status check with proper timeouts
            HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
                --connect-timeout 10 \
                --max-time 30 \
                "${'$'}BOM_URL")
            
            echo "HTTP Status: ${'$'}HTTP_STATUS"
            
            if [ "${'$'}HTTP_STATUS" = "200" ]; then
                echo "BOM verified for version ${'$'}KTOR_VERSION"
                break
            else
                RETRY_COUNT=$((RETRY_COUNT + 1))
                if [ ${'$'}RETRY_COUNT -lt ${'$'}MAX_RETRIES ]; then
                    echo "BOM not available (HTTP ${'$'}HTTP_STATUS), retry ${'$'}RETRY_COUNT/${'$'}MAX_RETRIES in 30 seconds..."
                    sleep 30
                else
                    echo "BOM not available after ${'$'}MAX_RETRIES retries (final status: ${'$'}HTTP_STATUS)"
                    echo "Available versions:"
                    curl -s --connect-timeout 10 --max-time 30 \
                        "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml" \
                        | grep -o '<version>[^<]*</version>' | tail -10 || echo "Failed to fetch version list"
                    exit 1
                fi
            fi
        done
    """.trimIndent()
    }

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

        jdkHome = if (isPluginSample) Env.JDK_LTS else "%env.JDK_17_0%"
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
    }
}

fun BuildSteps.buildEAPGradleSample(relativeDir: String, standalone: Boolean) {
    buildEAPGradleProject(relativeDir, standalone, isPluginSample = false)
}

fun BuildSteps.buildEAPGradlePluginSample(relativeDir: String, standalone: Boolean) {
    createEAPGradleInitScript()

    gradle {
        name = "Build Gradle Plugin"
        tasks = "build"
        gradleParams = "--init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts"
        jdkHome = Env.JDK_LTS
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
    }

    gradle {
        name = "Build EAP Build Plugin Sample"
        tasks = "build"
        workingDir = if (!standalone) "samples/$relativeDir" else ""
        gradleParams = "--init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts"
        jdkHome = Env.JDK_LTS
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
    }
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
        
        # Function to search for EAP versions and handle fallback logic
        find_eap_version_or_fallback() {
            local temp_file="$1"
            local context_message="$2"
            
            echo "${'$'}{context_message}"
            
            # Try to find EAP versions and pick the newest
            local eap_version=$(grep -o '<version>[^<]*</version>' "${'$'}{temp_file}" | sed 's/<version>\(.*\)<\/version>/\1/' | grep -i eap | tail -1 || echo "")
            
            if [ -n "${'$'}{eap_version}" ]; then
                echo "${'$'}{eap_version}"
                return 0
            else
                echo "No EAP versions found in metadata" >&2
                
                # Check if non-EAP fallback is allowed via environment flag
                if [ "${'$'}{ALLOW_NON_EAP:-false}" = "true" ]; then
                    echo "ALLOW_NON_EAP flag is set, falling back to most recent version" >&2
                    local fallback_version=$(grep -o '<version>[^<]*</version>' "${'$'}{temp_file}" | sed 's/<version>\(.*\)<\/version>/\1/' | tail -1 || echo "")
                    if [ -n "${'$'}{fallback_version}" ]; then
                        echo "Selected non-EAP fallback version: ${'$'}{fallback_version}" >&2
                        echo "${'$'}{fallback_version}"
                        return 0
                    fi
                else
                    echo "ALLOW_NON_EAP flag not set (default: deny non-EAP fallback)" >&2
                    echo "No EAP versions available and non-EAP fallback is disabled" >&2
                    return 1
                fi
            fi
            
            echo "Failed to find any suitable version" >&2
            return 1
        }
        
        # Fetch the latest EAP version from the Ktor BOM metadata
        METADATA_URL="https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml"
        echo "Fetching metadata from ${'$'}METADATA_URL"
        
        # Create a temporary file for the metadata
        TEMP_FILE=$(mktemp)
        
        # Download the metadata file with proper timeouts and redirect handling
        if ! curl -s --connect-timeout 10 --max-time 30 --location "${'$'}METADATA_URL" -o "${'$'}TEMP_FILE"; then
            echo "Failed to download metadata from ${'$'}METADATA_URL"
            rm -f "${'$'}TEMP_FILE"
            exit 1
        fi
        
        echo "Metadata content preview:"
        head -20 "${'$'}TEMP_FILE" || echo "Could not preview metadata file"
        
        # Extract the latest version using grep and sed
        # This pattern looks for a <latest>version</latest> tag
        LATEST_VERSION=$(grep -o '<latest>[^<]*</latest>' "${'$'}TEMP_FILE" | sed 's/<latest>\(.*\)<\/latest>/\1/' || echo "")
        
        # If no latest tag found, try to get EAP version from the versions list first
        if [ -z "${'$'}LATEST_VERSION" ]; then
            echo "No <latest> tag found, searching for EAP versions in versions list..."
            LATEST_VERSION=$(find_eap_version_or_fallback "${'$'}TEMP_FILE" "Searching for EAP versions...")
            if [ $? -ne 0 ]; then
                rm -f "${'$'}TEMP_FILE"
                exit 1
            fi
        else
            # Check if the latest version contains "eap"
            if echo "${'$'}LATEST_VERSION" | grep -qi eap; then
                echo "Latest version is EAP: ${'$'}LATEST_VERSION"
            else
                echo "Latest version is not EAP: ${'$'}LATEST_VERSION"
                NEW_VERSION=$(find_eap_version_or_fallback "${'$'}TEMP_FILE" "Searching for EAP versions in versions list...")
                if [ $? -eq 0 ]; then
                    LATEST_VERSION="${'$'}NEW_VERSION"
                    echo "Selected EAP version instead: ${'$'}LATEST_VERSION"
                else
                    # Check if non-EAP fallback is allowed for the latest version we already found
                    if [ "${'$'}{ALLOW_NON_EAP:-false}" = "true" ]; then
                        echo "ALLOW_NON_EAP flag is set, using latest non-EAP version: ${'$'}LATEST_VERSION"
                    else
                        echo "ALLOW_NON_EAP flag not set (default: deny non-EAP fallback)"
                        echo "No EAP versions available and non-EAP fallback is disabled"
                        rm -f "${'$'}TEMP_FILE"
                        exit 1
                    fi
                fi
            fi
        fi
        
        # Clean up temp file
        rm -f "${'$'}TEMP_FILE"
        
        if [ -z "${'$'}LATEST_VERSION" ]; then
            echo "Failed to extract any version from metadata"
            exit 1
        fi
        
        echo "Final selected Ktor version: ${'$'}LATEST_VERSION"
        
        # Validate the version format (should contain "eap" or be a valid semantic version)
        if echo "${'$'}LATEST_VERSION" | grep -qiE "(eap|snapshot|alpha|beta|rc)"; then
            echo "Version validation passed: ${'$'}LATEST_VERSION appears to be a pre-release version"
        else
            echo "Warning: Version ${'$'}LATEST_VERSION might not be an EAP version"
        fi
        
        # Set build parameter directly (will be propagated to dependent builds)
        echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}LATEST_VERSION']"
        
        # Also set configuration parameter for reference
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
