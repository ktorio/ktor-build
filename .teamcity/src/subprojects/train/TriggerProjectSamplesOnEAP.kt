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
        mavenCentral()
        google()
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
            name = "KtorPluginEAP"  
            url = uri("$KTOR_EAP_URL")
            content {
                includeGroup("io.ktor.plugin")
            }
        }
        maven {
            name = "KtorEAP"  
            url = uri("$KTOR_EAP_URL")
            content {
                includeGroup("io.ktor")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        google()
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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        ${generateGradleRepositories()}
    }
}

pluginManagement {
    repositories {
        ${generateGradlePluginRepositories()}
    }
    
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.ktor.plugin") {
                val ktorVersion = System.getenv("KTOR_VERSION")
                val pluginVersion = System.getenv("KTOR_GRADLE_PLUGIN_VERSION")
                
                when {
                    !pluginVersion.isNullOrEmpty() -> {
                        useVersion(pluginVersion)
                        println("Using explicit Ktor Gradle Plugin version: " + pluginVersion)
                    }
                    !ktorVersion.isNullOrEmpty() -> {
                        useVersion(ktorVersion)
                        println("Using Ktor Gradle Plugin version matching framework: " + ktorVersion)
                    }
                    else -> {
                        throw GradleException("No Ktor plugin version available. Set KTOR_VERSION or KTOR_GRADLE_PLUGIN_VERSION environment variable.")
                    }
                }
            }
        }
    }
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
            
            echo "Using Ktor Framework EAP version: %env.KTOR_VERSION%"
            
            cat > %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts << 'EOF'
allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven {
            name = "KtorEAP"
            url = uri("${EapRepositoryConfig.KTOR_EAP_URL}")
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
            val frameworkVersion = System.getenv("KTOR_VERSION")
            logger.lifecycle("Project " + name + ": Using Ktor Framework EAP version: " + frameworkVersion)
            logger.lifecycle("Project " + name + ": EAP repository configured for framework")
        }
    }
}
EOF
        """.trimIndent()
    }
}

fun BuildSteps.createEAPGradlePluginInitScript() {
    script {
        name = "Detect EAP Plugin Version"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            # Validate that KTOR_VERSION is set
            if [ -z "%env.KTOR_VERSION%" ]; then
                echo "ERROR: KTOR_VERSION environment variable is not set"
                exit 1
            fi
            
            echo "Framework EAP version: %env.KTOR_VERSION%"
            
            # Check if KTOR_GRADLE_PLUGIN_VERSION is already set (from manual override or triggering build)
            EXISTING_PLUGIN_VERSION="%env.KTOR_GRADLE_PLUGIN_VERSION%"
            
            if [ -n "${'$'}EXISTING_PLUGIN_VERSION" ] && [ "${'$'}EXISTING_PLUGIN_VERSION" != "" ]; then
                echo "=== Using Pre-set Plugin Version ==="
                echo "KTOR_GRADLE_PLUGIN_VERSION already set to: ${'$'}EXISTING_PLUGIN_VERSION"
                echo "Skipping metadata lookup and honoring existing value"
            else
                echo "=== Detecting Plugin Version from Metadata ==="
                echo "KTOR_GRADLE_PLUGIN_VERSION not set, detecting from metadata..."
                
                # Try to find matching Ktor Gradle Plugin EAP version
                echo "Checking if Ktor Gradle Plugin EAP version exists for %env.KTOR_VERSION%..."
                
                PLUGIN_METADATA_URL="${EapRepositoryConfig.KTOR_EAP_URL}/io/ktor/plugin/io.ktor.plugin.gradle.plugin/maven-metadata.xml"
                TEMP_METADATA=$(mktemp)
                
                if curl -fsSL --connect-timeout 10 --max-time 20 --retry 3 --retry-delay 2 "${'$'}PLUGIN_METADATA_URL" -o "${'$'}TEMP_METADATA" 2>/dev/null; then
                    echo "Successfully fetched plugin metadata"
                    echo "Plugin metadata content:"
                    cat "${'$'}TEMP_METADATA"
                    
                    # Check if the exact framework version exists as plugin version
                    if grep -q ">%env.KTOR_VERSION%<" "${'$'}TEMP_METADATA"; then
                        echo "Found exact matching plugin version: %env.KTOR_VERSION%"
                        echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='%env.KTOR_VERSION%']"
                    else
                        echo "Exact plugin version %env.KTOR_VERSION% not found"
                        
                        # Try to find latest EAP plugin version - look for the latest tag first
                        LATEST_PLUGIN_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_METADATA" | sed 's/<latest>//;s/<\/latest>//')
                        
                        if [ -n "${'$'}LATEST_PLUGIN_VERSION" ]; then
                            echo "Found latest plugin version from metadata: ${'$'}LATEST_PLUGIN_VERSION"
                            echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='${'$'}LATEST_PLUGIN_VERSION']"
                        else
                            # Fallback: extract the highest version number from all versions
                            echo "No <latest> tag found, extracting highest version from all versions"
                            LATEST_PLUGIN_VERSION=$(grep -o ">[0-9][^<]*-eap-[0-9]*<" "${'$'}TEMP_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
                            
                            if [ -n "${'$'}LATEST_PLUGIN_VERSION" ]; then
                                echo "Found latest plugin EAP version: ${'$'}LATEST_PLUGIN_VERSION"
                                echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='${'$'}LATEST_PLUGIN_VERSION']"
                            else
                                echo "No compatible EAP plugin version found, will use framework version and let Gradle handle resolution"
                                echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='%env.KTOR_VERSION%']"
                            fi
                        fi
                    fi
                else
                    echo "WARNING: Could not fetch plugin metadata, using framework version as fallback"
                    echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='%env.KTOR_VERSION%']"
                fi
                
                rm -f "${'$'}TEMP_METADATA"
            fi
            
            echo "Final plugin version to use: %env.KTOR_GRADLE_PLUGIN_VERSION%"
        """.trimIndent()
    }

    script {
        name = "Create EAP Gradle Plugin init script"
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
        scriptContent = """
            mkdir -p %system.teamcity.build.tempDir%
            
            echo "Using Ktor Framework EAP version: %env.KTOR_VERSION%"
            echo "Using Ktor Gradle Plugin version: %env.KTOR_GRADLE_PLUGIN_VERSION%"
            
            # Validate that we have a proper plugin version
            if [[ "%env.KTOR_GRADLE_PLUGIN_VERSION%" =~ ^-eap- ]]; then
                echo "ERROR: Invalid plugin version detected: %env.KTOR_GRADLE_PLUGIN_VERSION%"
                echo "Plugin version should not start with '-eap-', falling back to framework version"
                echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='%env.KTOR_VERSION%']"
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
                        
                        rm -f "${'$'}build_file.tmp"
                        echo "Successfully fixed version catalog aliases in ${'$'}build_file"
                    else
                        echo "No version catalog aliases found in ${'$'}build_file, skipping"
                        rm -f "${'$'}build_file.backup"
                    fi
                fi
            done
            
            cat > %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts << 'EOF'
allprojects {
    repositories {
        maven {
            name = "KtorPluginEAP"  
            url = uri("${EapRepositoryConfig.KTOR_EAP_URL}")
            content {
                includeGroup("io.ktor.plugin")
            }
        }
        maven {
            name = "KtorEAP"  
            url = uri("${EapRepositoryConfig.KTOR_EAP_URL}")
            content {
                includeGroup("io.ktor")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
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
            val frameworkVersion = System.getenv("KTOR_VERSION")
            val pluginVersion = System.getenv("KTOR_GRADLE_PLUGIN_VERSION")
            logger.lifecycle("Project " + name + ": Using Ktor Framework EAP version: " + frameworkVersion)
            logger.lifecycle("Project " + name + ": Using Ktor Gradle Plugin version: " + pluginVersion)
            logger.lifecycle("Project " + name + ": EAP repository configured for both framework and plugin")
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
    createEAPGradlePluginInitScript()

    if (!standalone) {
        modifyRootSettingsForEAP()

        gradle {
            name = "Build EAP Build Plugin Sample"
            tasks = ":samples:$relativeDir:build"
            workingDir = ""
            useGradleWrapper = true
            gradleParams = "--init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts"
            jdkHome = Env.JDK_LTS
            executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
        }

        restoreGradlePluginSampleBuildFiles()
        restoreRootSettings()
    } else {
        createEAPSampleSettings("", true)
        gradle {
            name = "Build EAP Build Plugin Sample"
            tasks = "build"
            useGradleWrapper = true
            gradleParams = "--init-script=%system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts"
            jdkHome = Env.JDK_LTS
            executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
        }
        restoreGradlePluginSampleBuildFiles()
        restoreEAPSampleSettings("")
    }
}

fun BuildSteps.modifyRootSettingsForEAP() {
    script {
        name = "Create EAP Settings Override"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            SETTINGS_FILE="settings.gradle.kts"
            BACKUP_FILE="settings.gradle.kts.eap.backup"
            EAP_SETTINGS_FILE="eap-settings.gradle.kts"
            
            echo "Creating EAP settings override"
            
            # Backup original settings
            if [ -f "${'$'}SETTINGS_FILE" ]; then
                cp "${'$'}SETTINGS_FILE" "${'$'}BACKUP_FILE"
                echo "Backed up original settings.gradle.kts"
            fi
            
            # Create EAP-specific settings file using the existing function
            cat > "${'$'}EAP_SETTINGS_FILE" << 'EOF'
${EapRepositoryConfig.generateSettingsContent(true)}
EOF
            
            # Add apply from to the existing settings.gradle.kts
            if [ -f "${'$'}SETTINGS_FILE" ]; then
                # Add the apply from at the beginning of the file
                echo "apply(from = \"eap-settings.gradle.kts\")" > "${'$'}SETTINGS_FILE.tmp"
                echo "" >> "${'$'}SETTINGS_FILE.tmp"
                cat "${'$'}SETTINGS_FILE" >> "${'$'}SETTINGS_FILE.tmp"
                mv "${'$'}SETTINGS_FILE.tmp" "${'$'}SETTINGS_FILE"
                echo "Applied EAP settings to existing settings.gradle.kts"
            else
                # If no settings file exists, create one with just the apply
                echo "apply(from = \"eap-settings.gradle.kts\")" > "${'$'}SETTINGS_FILE"
                echo "Created new settings.gradle.kts with EAP configuration"
            fi
        """.trimIndent()
    }
}

fun BuildSteps.restoreRootSettings() {
    script {
        name = "Restore Root Settings"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            SETTINGS_FILE="settings.gradle.kts"
            BACKUP_FILE="settings.gradle.kts.eap.backup"
            EAP_SETTINGS_FILE="eap-settings.gradle.kts"
            
            echo "Restoring original settings and cleaning up EAP files"
            
            # Remove EAP settings file
            if [ -f "${'$'}EAP_SETTINGS_FILE" ]; then
                rm -f "${'$'}EAP_SETTINGS_FILE"
                echo "Removed EAP settings file"
            fi
            
            # Restore original settings
            if [ -f "${'$'}BACKUP_FILE" ]; then
                if mv "${'$'}BACKUP_FILE" "${'$'}SETTINGS_FILE"; then
                    echo "Successfully restored original settings.gradle.kts"
                else
                    echo "Failed to restore original settings.gradle.kts" >&2
                    exit 1
                fi
            else
                echo "No backup file found, settings.gradle.kts may not have been modified"
            fi
        """.trimIndent()
    }
}

fun BuildSteps.buildEAPMavenSample(relativeDir: String) {
    script {
        name = "Create EAP Maven settings"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            # Validate KTOR_VERSION is available for EAP Maven sample
            if [ -z "%env.KTOR_VERSION%" ]; then
                echo "ERROR: KTOR_VERSION is required for EAP Maven sample but not set"
                exit 1
            fi
            
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
                    agent(Agents.OS.Linux)
                }

                params {
                    param("teamcity.build.skipDependencyBuilds", "true")
                    param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
                    param("env.KTOR_GRADLE_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_GRADLE_PLUGIN_VERSION%")
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

            if (this@asEAPSampleConfig.withAndroidSdk) configureAndroidHome()

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
                if (this@asEAPSampleConfig.withAndroidSdk) acceptAndroidSDKLicense()

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
            param("env.KTOR_VERSION", "")
            param("env.KTOR_GRADLE_PLUGIN_VERSION", "")
        }

        steps {
            script {
                name = "Fetch Latest EAP Framework Version"
                scriptContent = """
                    #!/bin/bash
                    set -e
                    
                    echo "=== Fetching Latest Ktor EAP Framework Version ==="
                    
                    METADATA_URL="${EapRepositoryConfig.KTOR_EAP_URL}/io/ktor/ktor-bom/maven-metadata.xml"
                    TEMP_METADATA=$(mktemp)
                    
                    echo "Fetching framework metadata from: ${'$'}METADATA_URL"
                    
                    if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}METADATA_URL" -o "${'$'}TEMP_METADATA" 2>/dev/null; then
                        echo "Successfully fetched framework metadata"
                        echo "Framework metadata content:"
                        cat "${'$'}TEMP_METADATA"
                        
                        # Extract the latest EAP version - try latest tag first
                        LATEST_EAP_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_METADATA" | sed 's/<latest>//;s/<\/latest>//')
                        
                        if [ -z "${'$'}LATEST_EAP_VERSION" ]; then
                            # Fallback to extracting from versions list
                            LATEST_EAP_VERSION=$(grep -o ">[0-9][^<]*-eap-[0-9]*<" "${'$'}TEMP_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
                        fi
                        
                        if [ -n "${'$'}LATEST_EAP_VERSION" ]; then
                            echo "Found latest EAP framework version: ${'$'}LATEST_EAP_VERSION"
                            echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}LATEST_EAP_VERSION']"
                        else
                            echo "ERROR: No EAP version found in metadata"
                            echo "Metadata content:"
                            cat "${'$'}TEMP_METADATA"
                            exit 1
                        fi
                        
                        rm -f "${'$'}TEMP_METADATA"
                    else
                        echo "ERROR: Failed to fetch framework metadata from ${'$'}METADATA_URL"
                        exit 1
                    fi
                    
                    echo "=== Framework Version Set Successfully ==="
                """.trimIndent()
            }

            script {
                name = "Resolve EAP Plugin Version"
                scriptContent = """
                    #!/bin/bash
                    set -e
                    
                    echo "=== EAP Plugin Version Resolution Started ==="
                    
                    # Validate that KTOR_VERSION is set from previous step
                    if [ -z "%env.KTOR_VERSION%" ] || [ "%env.KTOR_VERSION%" = "" ]; then
                        echo "ERROR: KTOR_VERSION environment variable is not set or empty"
                        exit 1
                    fi
                    
                    FRAMEWORK_VERSION="%env.KTOR_VERSION%"
                    echo "Framework EAP version: ${'$'}FRAMEWORK_VERSION"
                    
                    # Check if KTOR_GRADLE_PLUGIN_VERSION is already set (from manual override or triggering build)
                    EXISTING_PLUGIN_VERSION="%env.KTOR_GRADLE_PLUGIN_VERSION%"
                    
                    if [ -n "${'$'}EXISTING_PLUGIN_VERSION" ] && [ "${'$'}EXISTING_PLUGIN_VERSION" != "" ]; then
                        echo "=== Using Pre-set Plugin Version ==="
                        echo "KTOR_GRADLE_PLUGIN_VERSION already set to: ${'$'}EXISTING_PLUGIN_VERSION"
                        echo "Skipping metadata lookup and honoring existing value"
                        PLUGIN_VERSION="${'$'}EXISTING_PLUGIN_VERSION"
                    else
                        echo "=== Detecting Plugin Version from Metadata ==="
                        echo "KTOR_GRADLE_PLUGIN_VERSION not set, detecting from metadata..."
                        
                        # Initialize plugin version to framework version as default
                        PLUGIN_VERSION="${'$'}FRAMEWORK_VERSION"
                        echo "Default plugin version set to: ${'$'}PLUGIN_VERSION"
                        
                        # Try to find a more specific Ktor Gradle Plugin EAP version
                        echo "Checking if specific Ktor Gradle Plugin EAP version exists..."
                        
                        PLUGIN_METADATA_URL="${EapRepositoryConfig.KTOR_EAP_URL}/io/ktor/plugin/io.ktor.plugin.gradle.plugin/maven-metadata.xml"
                        TEMP_METADATA=$(mktemp)
                        
                        if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}PLUGIN_METADATA_URL" -o "${'$'}TEMP_METADATA" 2>/dev/null; then
                            echo "Successfully fetched plugin metadata"
                            echo "Plugin metadata content:"
                            cat "${'$'}TEMP_METADATA"
                            
                            # Check if the exact framework version exists as plugin version
                            if grep -q ">${'$'}FRAMEWORK_VERSION<" "${'$'}TEMP_METADATA"; then
                                echo "Found exact matching plugin version: ${'$'}FRAMEWORK_VERSION"
                                PLUGIN_VERSION="${'$'}FRAMEWORK_VERSION"
                            else
                                echo "Exact plugin version ${'$'}FRAMEWORK_VERSION not found"
                                
                                # Try to find latest EAP plugin version - look for the latest tag first
                                LATEST_PLUGIN_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_METADATA" | sed 's/<latest>//;s/<\/latest>//')
                                
                                if [ -n "${'$'}LATEST_PLUGIN_VERSION" ]; then
                                    echo "Found latest plugin version from metadata: ${'$'}LATEST_PLUGIN_VERSION"
                                    PLUGIN_VERSION="${'$'}LATEST_PLUGIN_VERSION"
                                else
                                    # Fallback: extract the highest version number from all versions
                                    echo "No <latest> tag found, extracting highest version from all versions"
                                    LATEST_PLUGIN_VERSION=$(grep -o ">[0-9][^<]*-eap-[0-9]*<" "${'$'}TEMP_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
                                    
                                    if [ -n "${'$'}LATEST_PLUGIN_VERSION" ]; then
                                        echo "Found latest plugin EAP version: ${'$'}LATEST_PLUGIN_VERSION"
                                        PLUGIN_VERSION="${'$'}LATEST_PLUGIN_VERSION"
                                    else
                                        echo "No compatible EAP plugin version found, using framework version: ${'$'}FRAMEWORK_VERSION"
                                        PLUGIN_VERSION="${'$'}FRAMEWORK_VERSION"
                                    fi
                                fi
                            fi
                            
                            rm -f "${'$'}TEMP_METADATA"
                        else
                            echo "WARNING: Could not fetch plugin metadata, using framework version as fallback"
                            PLUGIN_VERSION="${'$'}FRAMEWORK_VERSION"
                        fi
                    fi
                    
                    # Set the parameter (either the existing value or the detected one)
                    echo "=== Setting TeamCity Parameters ==="
                    echo "Framework version: ${'$'}FRAMEWORK_VERSION"
                    echo "Plugin version: ${'$'}PLUGIN_VERSION"
                    
                    echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='${'$'}PLUGIN_VERSION']"
                    
                    # Verify the parameter was set by echoing it
                    echo "=== Verification ==="
                    echo "Final KTOR_VERSION: ${'$'}FRAMEWORK_VERSION"  
                    echo "Final KTOR_GRADLE_PLUGIN_VERSION: ${'$'}PLUGIN_VERSION"
                    echo "=== EAP Version Resolution Completed Successfully ==="
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
