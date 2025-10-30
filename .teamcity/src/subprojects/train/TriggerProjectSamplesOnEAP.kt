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

object EapRepositoryConfig {
    const val KTOR_EAP_URL = "https://maven.pkg.jetbrains.space/public/p/ktor/eap"
    const val COMPOSE_DEV_URL = "https://maven.pkg.jetbrains.space/public/p/compose/dev"

    fun generateGradleRepositories(): String = """
        google()
        mavenCentral()
        maven("$COMPOSE_DEV_URL")
        maven {
            name = "KtorEAP"
            url = uri("$KTOR_EAP_URL")
            content {
                includeGroup("io.ktor")
            }
        }
    """.trimIndent()

    fun generateGradlePluginRepositories(): String = """
        maven {
            name = "KtorEAP"  
            url = uri("$KTOR_EAP_URL")
            content {
                includeGroup("io.ktor")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        maven("$COMPOSE_DEV_URL")
    """.trimIndent()

    fun generateMavenRepository(): String = """
        <repository>
            <id>ktor-eap</id>
            <url>$KTOR_EAP_URL</url>
        </repository>
    """.trimIndent()

    fun generateSettingsContent(isPluginSample: Boolean): String {
        val baseSettings = """
dependencyResolutionManagement {
    repositories {
        ${generateGradleRepositories()}
    }
}

pluginManagement {
    repositories {
        ${generateGradlePluginRepositories()}
    }${if (isPluginSample) """
    
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.ktor.plugin") {
                val pluginVersion = System.getenv("KTOR_GRADLE_PLUGIN_VERSION")
                if (pluginVersion != null && pluginVersion.isNotEmpty()) {
                    useVersion(pluginVersion)
                }
            }
        }
    }""" else ""}
}
        """.trimIndent()

        return baseSettings
    }
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
            
            # Fix build.gradle.kts files in Gradle plugin samples to replace version catalog aliases
            echo "Fixing build.gradle.kts files in gradle plugin samples..."
            find samples -name "build.gradle.kts" -type f 2>/dev/null | while read build_file; do
                if [ -f "${'$'}build_file" ]; then
                    echo "Processing ${'$'}build_file"
                    
                    # Create backup
                    cp "${'$'}build_file" "${'$'}build_file.backup"
                    
                    # Check if file contains version catalog aliases
                    if grep -q "alias(libs\.plugins\." "${'$'}build_file"; then
                        echo "Found version catalog aliases in ${'$'}build_file, replacing..."
                        
                        # Replace version catalog aliases with direct plugin IDs
                        sed -i.tmp \
                            -e 's/alias(libs\.plugins\.ktor)/id("io.ktor.plugin")/g' \
                            -e 's/alias(libs\.plugins\.kotlin\.jvm)/id("org.jetbrains.kotlin.jvm")/g' \
                            -e 's/alias(libs\.plugins\.kotlin\.plugin\.serialization)/id("org.jetbrains.kotlin.plugin.serialization")/g' \
                            -e 's/alias(libs\.plugins\.shadow)/id("com.github.johnrengelman.shadow")/g' \
                            "${'$'}build_file"
                        
                        # Remove the temporary file created by sed -i
                        rm -f "${'$'}build_file.tmp"
                        
                        echo "Successfully fixed version catalog aliases in ${'$'}build_file"
                    else
                        echo "No version catalog aliases found in ${'$'}build_file, skipping"
                        # Remove backup since no changes were made
                        rm -f "${'$'}build_file.backup"
                    fi
                fi
            done
            
            cat > %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts << 'EOF'
gradle.allprojects {
    repositories {
        ${EapRepositoryConfig.generateGradleRepositories()}
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
EOF
        """.trimIndent()
    }
}

fun BuildSteps.restoreGradlePluginSampleBuildFiles() {
    script {
        name = "Restore Gradle Plugin Sample Build Files"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            echo "Restoring original build.gradle.kts files..."
            find samples -name "build.gradle.kts.backup" -type f 2>/dev/null | while read backup_file; do
                original_file="${'$'}{backup_file%.backup}"
                if [ -f "${'$'}backup_file" ]; then
                    echo "Restoring ${'$'}original_file from backup"
                    if mv "${'$'}backup_file" "${'$'}original_file"; then
                        echo "Successfully restored ${'$'}original_file"
                    else
                        echo "Failed to restore ${'$'}original_file" >&2
                    fi
                fi
            done
        """.trimIndent()
    }
}

fun BuildSteps.createEAPSampleSettings(samplePath: String, isPluginSample: Boolean) {
    script {
        name = "Create EAP Sample Settings"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            SAMPLE_DIR="$samplePath"
            SETTINGS_FILE="${'$'}{SAMPLE_DIR}/settings.gradle.kts"
            TEMP_SETTINGS="${'$'}{SETTINGS_FILE}.tmp.$$"
            BACKUP_FILE="${'$'}{SETTINGS_FILE}.backup.$$"
            
            echo "Creating EAP sample settings at: ${'$'}{SETTINGS_FILE}"
            
            # Use process ID to make temp files unique
            trap 'rm -f "${'$'}TEMP_SETTINGS" "${'$'}BACKUP_FILE"' EXIT
            
            # Atomically backup existing settings if present
            if [ -f "${'$'}{SETTINGS_FILE}" ]; then
                if cp "${'$'}{SETTINGS_FILE}" "${'$'}BACKUP_FILE"; then
                    echo "Backed up existing settings file to ${'$'}BACKUP_FILE"
                else
                    echo "Failed to backup existing settings file" >&2
                    exit 1
                fi
            fi
            
            # Create new settings in temporary file
            cat > "${'$'}TEMP_SETTINGS" << 'EOF'
${EapRepositoryConfig.generateSettingsContent(isPluginSample)}
EOF
            
            # Atomically move temp file to final location
            if mv "${'$'}TEMP_SETTINGS" "${'$'}{SETTINGS_FILE}"; then
                echo "EAP sample settings created successfully"
            else
                echo "Failed to create settings file" >&2
                # Restore backup if atomic move failed
                if [ -f "${'$'}BACKUP_FILE" ]; then
                    mv "${'$'}BACKUP_FILE" "${'$'}{SETTINGS_FILE}"
                    echo "Restored original settings file"
                fi
                exit 1
            fi
        """.trimIndent()
    }
}

fun BuildSteps.restoreEAPSampleSettings(samplePath: String) {
    script {
        name = "Restore EAP Sample Settings"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            SAMPLE_DIR="$samplePath"
            SETTINGS_FILE="${'$'}{SAMPLE_DIR}/settings.gradle.kts"
            BACKUP_FILE="${'$'}{SETTINGS_FILE}.backup.$$"
            
            echo "Restoring sample settings at: ${'$'}{SETTINGS_FILE}"
            
            # Look for backup file with current process ID first, then fallback to generic backup
            if [ -f "${'$'}BACKUP_FILE" ]; then
                if mv "${'$'}BACKUP_FILE" "${'$'}{SETTINGS_FILE}"; then
                    echo "Restored original settings file from backup"
                else
                    echo "Failed to restore from backup" >&2
                    exit 1
                fi
            elif [ -f "${'$'}{SETTINGS_FILE}.backup" ]; then
                if mv "${'$'}{SETTINGS_FILE}.backup" "${'$'}{SETTINGS_FILE}"; then
                    echo "Restored original settings file from generic backup"
                else
                    echo "Failed to restore from generic backup" >&2
                    exit 1
                fi
            else
                # Remove the temporary settings file if no backup existed
                rm -f "${'$'}{SETTINGS_FILE}"
                echo "Removed temporary settings file (no original existed)"
            fi
        """.trimIndent()
    }
}

fun BuildSteps.buildEAPGradleSample(relativeDir: String, standalone: Boolean) {
    createEAPGradleInitScript()
    val samplePath = if (!standalone) relativeDir else ""
    if (!standalone) {
        createEAPSampleSettings(samplePath, false)
    }
    gradle {
        name = "Build EAP Sample"
        tasks = "build"
        workingDir = samplePath
        useGradleWrapper = true
        gradleParams = "--init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts"
        jdkHome = Env.JDK_LTS
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
    }
    if (!standalone) {
        restoreGradlePluginSampleBuildFiles()
        restoreEAPSampleSettings(samplePath)
    }
}

fun BuildSteps.buildEAPGradlePluginSample(relativeDir: String, standalone: Boolean) {
    createEAPGradleInitScript()
    val samplePath = if (!standalone) "samples/$relativeDir" else ""
    val wrapperPath = if (!standalone) "../.." else ""
    if (!standalone) {
        createEAPSampleSettings(samplePath, true)
    }
    gradle {
        name = "Build EAP Build Plugin Sample"
        tasks = "build"
        workingDir = samplePath
        useGradleWrapper = true
        gradleWrapperPath = wrapperPath
        gradleParams = "--init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts"
        jdkHome = Env.JDK_LTS
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
    }
    if (!standalone) {
        restoreGradlePluginSampleBuildFiles()
        restoreEAPSampleSettings(samplePath)
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
                    ${EapRepositoryConfig.generateMavenRepository()}
                  </repositories>
                  <properties>
                    <ktor.version>%env.KTOR_VERSION%</ktor.version>
                  </properties>
                </profile>
              </profiles>
              <activeProjects>
                <activeProfile>ktor-eap</activeProfile>
              </activeProjects>
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
