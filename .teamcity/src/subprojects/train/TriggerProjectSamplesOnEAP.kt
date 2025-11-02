package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.benchmarks.ProjectBenchmarks.buildType
import subprojects.build.*
import subprojects.build.samples.*

object EapConstants {
    const val PUBLISH_EAP_BUILD_TYPE_ID = "KtorPublish_AllEAP"
    const val PUBLISH_BUILD_PLUGIN_TYPE_ID = "KtorGradleBuildPlugin_Publish"
    const val EAP_VERSION_REGEX = ">[0-9][^<]*-eap-[0-9]*<"
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
            <name>Ktor EAP Repository</name>
            <url>$KTOR_EAP_URL</url>
        </repository>
    """.trimIndent()

    private fun generatePluginResolutionStrategy(): String = """
        resolutionStrategy {
            eachPlugin {
                if (requested.id.id == "io.ktor.plugin") {
                    val pluginVersion = System.getenv("KTOR_GRADLE_PLUGIN_VERSION")
                    
                    if (pluginVersion.isNullOrEmpty()) {
                        throw GradleException("KTOR_GRADLE_PLUGIN_VERSION environment variable is not set or is empty. This should have been resolved by the version resolver build step.")
                    }
                    
                    useModule("io.ktor.plugin:plugin:${'$'}pluginVersion")
                    println("Using Ktor Gradle Plugin version: ${'$'}pluginVersion")
                }
            }
        }
    """.trimIndent()

    fun generateSettingsContent(isPluginSample: Boolean): String = """
pluginManagement {
    repositories {
        ${generateGradlePluginRepositories()}
    }
    
    ${if (isPluginSample) generatePluginResolutionStrategy() else ""}
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        ${generateGradleRepositories()}
    }
}
    """.trimIndent()
}

interface EAPSampleConfig {
    val projectName: String
    fun createEAPBuildType(): BuildType
}

fun BuildType.addEAPSampleFailureConditions(sampleName: String) {
    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "ERROR:"
            failureMessage = "Error detected in $sampleName EAP sample validation"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "FAILED"
            failureMessage = "Build failed for $sampleName EAP sample"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "No agents available to run"
            failureMessage = "No compatible agents found for $sampleName EAP sample"
            stopBuildOnFailure = true
        }
        executionTimeoutMin = 20
    }
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
        name = "Create EAP Gradle Plugin init script"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            mkdir -p %system.teamcity.build.tempDir%
            
            echo "Using Ktor Gradle Plugin EAP version: %env.KTOR_GRADLE_PLUGIN_VERSION%"
            
            cat > %system.teamcity.build.tempDir%/ktor-plugin-eap.init.gradle.kts << 'EOF'
initscript {
    repositories {
        ${EapRepositoryConfig.generateGradlePluginRepositories()}
    }
}

allprojects {
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
            val frameworkVersion = System.getenv("KTOR_VERSION")
            val pluginVersion = System.getenv("KTOR_GRADLE_PLUGIN_VERSION")
            logger.lifecycle("Project " + name + ": Using Ktor Framework EAP version: " + frameworkVersion)
            logger.lifecycle("Project " + name + ": Using Ktor Gradle Plugin EAP version: " + pluginVersion)
            logger.lifecycle("Project " + name + ": EAP repositories configured")
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
            echo "Restoring original build files for gradle plugin samples"
            
            find . -name "build.gradle.kts.backup" -type f | while read backup_file; do
                original_file="${'$'}{backup_file%.backup}"
                if [ -f "${'$'}backup_file" ]; then
                    mv "${'$'}backup_file" "${'$'}original_file"
                    echo "Restored ${'$'}original_file from backup"
                fi
            done
            
            find . -name "settings.gradle.kts.backup.*" -type f | while read backup_file; do
                original_file=$(echo "${'$'}backup_file" | sed 's/\.backup\..*//')
                if [ -f "${'$'}backup_file" ]; then
                    mv "${'$'}backup_file" "${'$'}original_file"
                    echo "Restored ${'$'}original_file from backup"
                fi
            done
            
            echo "Restoration completed"
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
            GRADLE_PROPS="${'$'}{SAMPLE_DIR}/gradle.properties"
            TEMP_SETTINGS="${'$'}{SETTINGS_FILE}.tmp.$$"
            BACKUP_FILE="${'$'}{SETTINGS_FILE}.backup.$$"
            
            echo "Creating EAP sample settings at: ${'$'}{SETTINGS_FILE}"
            echo "Working directory: $(pwd)"
            echo "Sample directory: ${'$'}SAMPLE_DIR"
            
            if [ ! -d "${'$'}SAMPLE_DIR" ]; then
                echo "ERROR: Sample directory ${'$'}SAMPLE_DIR does not exist"
                echo "Available directories:"
                ls -la .
                exit 1
            fi
            
            trap 'rm -f "${'$'}TEMP_SETTINGS" "${'$'}BACKUP_FILE"' EXIT
            
            if [ -f "${'$'}{SETTINGS_FILE}" ]; then
                if cp "${'$'}{SETTINGS_FILE}" "${'$'}BACKUP_FILE"; then
                    echo "Backed up existing settings file to ${'$'}BACKUP_FILE"
                else
                    echo "Failed to backup existing settings file" >&2
                    exit 1
                fi
            else
                echo "No existing settings.gradle.kts found, will create new one"
            fi
            
            cat > "${'$'}TEMP_SETTINGS" << 'EOF'
${EapRepositoryConfig.generateSettingsContent(isPluginSample)}
EOF
            
            if mv "${'$'}TEMP_SETTINGS" "${'$'}{SETTINGS_FILE}"; then
                echo "EAP sample settings created successfully"
            else
                echo "Failed to create settings file" >&2
                if [ -f "${'$'}BACKUP_FILE" ]; then
                    mv "${'$'}BACKUP_FILE" "${'$'}{SETTINGS_FILE}"
                    echo "Restored original settings file"
                fi
                exit 1
            fi
            
            if [ "$samplePath" = "fullstack-mpp" ] || [ "$samplePath" = "client-mpp" ] || [ "$samplePath" = "ktor-client-wasm" ]; then
                echo "Configuring gradle.properties for multiplatform sample: $samplePath"
                
                if [ -f "${'$'}GRADLE_PROPS" ]; then
                    cp "${'$'}GRADLE_PROPS" "${'$'}GRADLE_PROPS.backup"
                else
                    touch "${'$'}GRADLE_PROPS"
                fi
                
                echo "" >> "${'$'}GRADLE_PROPS"
                echo "# Node.js configuration for Kotlin/JS" >> "${'$'}GRADLE_PROPS"
                echo "kotlin.js.nodejs.version=18.19.0" >> "${'$'}GRADLE_PROPS"
                echo "kotlin.js.nodejs.download=true" >> "${'$'}GRADLE_PROPS"
                echo "Added Node.js configuration to gradle.properties"
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
            GRADLE_PROPS="${'$'}{SAMPLE_DIR}/gradle.properties"
            
            echo "Restoring original files for sample: $samplePath"
            
            find "${'$'}SAMPLE_DIR" -name "settings.gradle.kts.backup.*" -type f | head -n 1 | while read backup_file; do
                if [ -f "${'$'}backup_file" ]; then
                    mv "${'$'}backup_file" "${'$'}SETTINGS_FILE"
                    echo "Restored settings.gradle.kts from backup"
                fi
            done
            
            if [ -f "${'$'}GRADLE_PROPS.backup" ]; then
                mv "${'$'}GRADLE_PROPS.backup" "${'$'}GRADLE_PROPS"
                echo "Restored gradle.properties from backup"
            fi
            
            echo "Restoration completed for $samplePath"
        """.trimIndent()
    }
}

fun BuildSteps.buildEAPGradleSample(relativeDir: String, standalone: Boolean) {
    createEAPGradleInitScript()
    createEAPSampleSettings(relativeDir, false)

    gradle {
        name = "Build EAP Sample (Gradle)"
        tasks = "build"
        gradleParams = "--init-script %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts --continue --info"
        jdkHome = Env.JDK_LTS

        if (!standalone) {
            workingDir = relativeDir
        }
    }

    restoreEAPSampleSettings(relativeDir)
}

fun BuildSteps.buildEAPGradlePluginSample(relativeDir: String, standalone: Boolean) {
    createEAPGradlePluginInitScript()
    createEAPSampleSettings(relativeDir, true)

    gradle {
        name = "Build EAP Gradle Plugin Sample"
        tasks = "build"
        gradleParams = "--init-script %system.teamcity.build.tempDir%/ktor-plugin-eap.init.gradle.kts --continue --info"
        jdkHome = Env.JDK_LTS

        if (!standalone) {
            workingDir = relativeDir
        }
    }

    restoreGradlePluginSampleBuildFiles()
    restoreEAPSampleSettings(relativeDir)
}

fun BuildSteps.buildEAPMavenSample(relativeDir: String) {
    script {
        name = "Modify Maven pom.xml for EAP"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            SAMPLE_DIR="$relativeDir"
            POM_FILE="${'$'}{SAMPLE_DIR}/pom.xml"
            BACKUP_FILE="${'$'}{SAMPLE_DIR}/pom.xml.backup"
            
            echo "Working with sample directory: ${'$'}SAMPLE_DIR"
            echo "Looking for pom.xml at: ${'$'}POM_FILE"
            
            if [ ! -d "${'$'}SAMPLE_DIR" ]; then
                echo "ERROR: Sample directory ${'$'}SAMPLE_DIR not found"
                ls -la .
                exit 1
            fi
            
            if [ ! -f "${'$'}POM_FILE" ]; then
                echo "ERROR: pom.xml not found at ${'$'}POM_FILE"
                echo "Contents of ${'$'}SAMPLE_DIR:"
                ls -la "${'$'}SAMPLE_DIR"
                exit 1
            fi
            
            cp "${'$'}POM_FILE" "${'$'}BACKUP_FILE"
            echo "Created backup at ${'$'}BACKUP_FILE"
            
            if grep -q "<repositories>" "${'$'}POM_FILE"; then
                echo "Found existing <repositories> section, adding EAP repository"
                sed -i.tmp "/<\/repositories>/ i\\
        ${EapRepositoryConfig.generateMavenRepository()}" "${'$'}POM_FILE"
            else
                echo "No <repositories> section found, adding repositories section with EAP repository"
                sed -i.tmp "/<\/project>/ i\\
    <repositories>\\
        ${EapRepositoryConfig.generateMavenRepository()}\\
    </repositories>" "${'$'}POM_FILE"
            fi
            
            rm -f "${'$'}POM_FILE.tmp"
            echo "Added EAP repository to pom.xml"
            
            if grep -q "ktor-eap" "${'$'}POM_FILE"; then
                echo "✓ EAP repository successfully added to pom.xml"
            else
                echo "WARNING: EAP repository might not have been added correctly"
            fi
        """.trimIndent()
    }

    maven {
        name = "Build EAP Sample (Maven)"
        goals = "clean compile package"
        runnerArgs = "-Dktor.version=%env.KTOR_VERSION%"
        workingDir = relativeDir
        jdkHome = Env.JDK_LTS
    }

    script {
        name = "Restore Maven pom.xml"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            SAMPLE_DIR="$relativeDir"
            POM_FILE="${'$'}{SAMPLE_DIR}/pom.xml"
            BACKUP_FILE="${'$'}{SAMPLE_DIR}/pom.xml.backup"
            
            if [ -f "${'$'}BACKUP_FILE" ]; then
                mv "${'$'}BACKUP_FILE" "${'$'}POM_FILE"
                echo "Restored original pom.xml from backup"
            else
                echo "No backup file found at ${'$'}BACKUP_FILE"
            fi
        """.trimIndent()
    }
}

fun BuildPluginSampleSettings.asEAPSampleConfig(versionResolver: BuildType): EAPSampleConfig =
    object : EAPSampleConfig {
        override val projectName: String = this@asEAPSampleConfig.projectName

        override fun createEAPBuildType(): BuildType = buildType {
            id("Ktor_EAP_${this@asEAPSampleConfig.projectName.replace("-", "_")}")
            name = "EAP Validate ${this@asEAPSampleConfig.projectName} gradle plugin sample"

            vcs {
                root(this@asEAPSampleConfig.vcsRoot)
            }

            addEAPSampleFailureConditions(this@asEAPSampleConfig.projectName)

            params {
                defaultGradleParams()
                param("env.KTOR_VERSION", "")
                param("env.KTOR_GRADLE_PLUGIN_VERSION", "")
            }

            dependencies {
                snapshot(versionResolver) {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }

            steps {
                buildEAPGradlePluginSample(this@asEAPSampleConfig.projectName, this@asEAPSampleConfig.standalone)
            }

            defaultBuildFeatures(this@asEAPSampleConfig.vcsRoot.id.toString())

            requirements {
                agent(Agents.OS.Linux)
            }
        }
    }

fun SampleProjectSettings.asEAPSampleConfig(versionResolver: BuildType): EAPSampleConfig =
    object : EAPSampleConfig {
        override val projectName: String = this@asEAPSampleConfig.projectName

        override fun createEAPBuildType(): BuildType = buildType {
            id("Ktor_EAP_KtorSamplesValidate_${this@asEAPSampleConfig.projectName.replace('-', '_')}")
            name = "EAP Validate ${this@asEAPSampleConfig.projectName} sample"

            vcs {
                root(this@asEAPSampleConfig.vcsRoot)
            }

            if (this@asEAPSampleConfig.withAndroidSdk) configureAndroidHome()
            addEAPSampleFailureConditions(this@asEAPSampleConfig.projectName)

            params {
                defaultGradleParams()
                param("env.KTOR_VERSION", "")
                if (this@asEAPSampleConfig.buildSystem == BuildSystem.GRADLE) {
                    param("env.KTOR_GRADLE_PLUGIN_VERSION", "")
                }
            }

            dependencies {
                snapshot(versionResolver) {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }

            steps {
                if (this@asEAPSampleConfig.withAndroidSdk) acceptAndroidSDKLicense()

                when (this@asEAPSampleConfig.buildSystem) {
                    BuildSystem.MAVEN -> buildEAPMavenSample(this@asEAPSampleConfig.projectName)
                    BuildSystem.GRADLE -> buildEAPGradleSample(this@asEAPSampleConfig.projectName, this@asEAPSampleConfig.standalone)
                }
            }

            defaultBuildFeatures(this@asEAPSampleConfig.vcsRoot.id.toString())

            requirements {
                agent(Agents.OS.Linux)
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
                        
                        LATEST_EAP_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_METADATA" | sed 's/<latest>//;s/<\/latest>//')
                        
                        if [ -z "${'$'}LATEST_EAP_VERSION" ]; then
                            LATEST_EAP_VERSION=$(grep -o "${EapConstants.EAP_VERSION_REGEX}" "${'$'}TEMP_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
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
                    
                    if [ -z "%env.KTOR_VERSION%" ] || [ "%env.KTOR_VERSION%" = "" ]; then
                        echo "ERROR: KTOR_VERSION environment variable is not set or empty"
                        exit 1
                    fi
                    
                    FRAMEWORK_VERSION="%env.KTOR_VERSION%"
                    echo "Framework EAP version: ${'$'}FRAMEWORK_VERSION"
                    
                    EXISTING_PLUGIN_VERSION="%env.KTOR_GRADLE_PLUGIN_VERSION%"
                    
                    if [ -n "${'$'}EXISTING_PLUGIN_VERSION" ] && [ "${'$'}EXISTING_PLUGIN_VERSION" != "" ]; then
                        echo "=== Using Pre-set Plugin Version ==="
                        echo "KTOR_GRADLE_PLUGIN_VERSION already set to: ${'$'}EXISTING_PLUGIN_VERSION"
                        echo "Verifying that this version exists in the repository..."
                        
                        PLUGIN_METADATA_URL="${EapRepositoryConfig.KTOR_EAP_URL}/io/ktor/plugin/io.ktor.plugin.gradle.plugin/maven-metadata.xml"
                        TEMP_METADATA=$(mktemp)
                        
                        if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}PLUGIN_METADATA_URL" -o "${'$'}TEMP_METADATA" 2>/dev/null; then
                            if grep -q ">${'$'}EXISTING_PLUGIN_VERSION<" "${'$'}TEMP_METADATA"; then
                                echo "Verified: Plugin version ${'$'}EXISTING_PLUGIN_VERSION exists in repository"
                                PLUGIN_VERSION="${'$'}EXISTING_PLUGIN_VERSION"
                            else
                                echo "WARNING: Pre-set plugin version ${'$'}EXISTING_PLUGIN_VERSION not found in repository"
                                echo "Will fall back to detecting latest version..."
                                EXISTING_PLUGIN_VERSION=""
                            fi
                        else
                            echo "WARNING: Cannot verify pre-set plugin version, proceeding anyway"
                            PLUGIN_VERSION="${'$'}EXISTING_PLUGIN_VERSION"
                        fi
                        
                        rm -f "${'$'}TEMP_METADATA"
                    fi
                    
                    if [ -z "${'$'}EXISTING_PLUGIN_VERSION" ]; then
                        echo "=== Detecting Latest Plugin Version from Metadata ==="
                        echo "KTOR_GRADLE_PLUGIN_VERSION not set or invalid, detecting latest available version from metadata..."
                        
                        echo "Fetching latest Ktor Gradle Plugin EAP version..."
                        
                        PLUGIN_METADATA_URL="${EapRepositoryConfig.KTOR_EAP_URL}/io/ktor/plugin/io.ktor.plugin.gradle.plugin/maven-metadata.xml"
                        TEMP_METADATA=$(mktemp)
                        
                        if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}PLUGIN_METADATA_URL" -o "${'$'}TEMP_METADATA" 2>/dev/null; then
                            echo "Successfully fetched plugin metadata"
                            echo "Plugin metadata content:"
                            cat "${'$'}TEMP_METADATA"
                            
                            LATEST_PLUGIN_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_METADATA" | sed 's/<latest>//;s/<\/latest>//')
                            
                            if [ -n "${'$'}LATEST_PLUGIN_VERSION" ]; then
                                echo "Found latest plugin version from <latest> tag: ${'$'}LATEST_PLUGIN_VERSION"
                                PLUGIN_VERSION="${'$'}LATEST_PLUGIN_VERSION"
                            else
                                echo "No <latest> tag found, extracting highest version from all versions"
                                LATEST_PLUGIN_VERSION=$(grep -o "${EapConstants.EAP_VERSION_REGEX}" "${'$'}TEMP_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
                                
                                if [ -n "${'$'}LATEST_PLUGIN_VERSION" ]; then
                                    echo "Found latest plugin EAP version from versions list: ${'$'}LATEST_PLUGIN_VERSION"
                                    PLUGIN_VERSION="${'$'}LATEST_PLUGIN_VERSION"
                                else
                                    echo "ERROR: No EAP plugin versions found in metadata"
                                    echo "Cannot proceed without a valid plugin version"
                                    exit 1
                                fi
                            fi
                            
                            if grep -q ">${'$'}FRAMEWORK_VERSION<" "${'$'}TEMP_METADATA"; then
                                echo "NOTE: Exact matching plugin version ${'$'}FRAMEWORK_VERSION exists but using latest: ${'$'}PLUGIN_VERSION"
                            else
                                echo "NOTE: Exact matching plugin version ${'$'}FRAMEWORK_VERSION does not exist, using latest: ${'$'}PLUGIN_VERSION"
                            fi
                            
                            rm -f "${'$'}TEMP_METADATA"
                        else
                            echo "ERROR: Could not fetch plugin metadata from ${'$'}PLUGIN_METADATA_URL"
                            echo "This is CRITICAL - cannot proceed without valid plugin version information"
                            exit 1
                        fi
                    fi
                    
                    if [ -z "${'$'}PLUGIN_VERSION" ]; then
                        echo "ERROR: No plugin version was determined - this is a critical failure"
                        exit 1
                    fi
                    
                    echo "=== Setting TeamCity Parameters ==="
                    echo "Framework version: ${'$'}FRAMEWORK_VERSION"
                    echo "Plugin version: ${'$'}PLUGIN_VERSION"
                    
                    echo "##teamcity[setParameter name='env.KTOR_GRADLE_PLUGIN_VERSION' value='${'$'}PLUGIN_VERSION']"
                    
                    echo "=== Verification ==="
                    echo "Final KTOR_VERSION: ${'$'}FRAMEWORK_VERSION"  
                    echo "Final KTOR_GRADLE_PLUGIN_VERSION: ${'$'}PLUGIN_VERSION"
                    echo "=== EAP Plugin Version Resolution Completed Successfully ==="
                """.trimIndent()
            }

            script {
                name = "Final Validation"
                scriptContent = """
                    #!/bin/bash
                    set -e
                    
                    echo "=== Final Validation of Resolved Versions ==="
                    
                    if [ -z "%env.KTOR_VERSION%" ] || [ "%env.KTOR_VERSION%" = "" ]; then
                        echo "CRITICAL ERROR: KTOR_VERSION is not set after resolution"
                        exit 1
                    fi
                    
                    if [ -z "%env.KTOR_GRADLE_PLUGIN_VERSION%" ] || [ "%env.KTOR_GRADLE_PLUGIN_VERSION%" = "" ]; then
                        echo "CRITICAL ERROR: KTOR_GRADLE_PLUGIN_VERSION is not set after resolution"
                        exit 1
                    fi
                    
                    if [[ "%env.KTOR_GRADLE_PLUGIN_VERSION%" =~ ^-eap- ]]; then
                        echo "CRITICAL ERROR: Malformed plugin version: %env.KTOR_GRADLE_PLUGIN_VERSION%"
                        exit 1
                    fi
                    
                    echo "✓ Framework version validated: %env.KTOR_VERSION%"
                    echo "✓ Plugin version validated: %env.KTOR_GRADLE_PLUGIN_VERSION%"
                    echo "✓ All versions are properly resolved and validated"
                    echo "=== Version Resolution SUCCESSFUL ==="
                """.trimIndent()
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "ERROR:"
                failureMessage = "Error detected in version resolution"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "CRITICAL ERROR:"
                failureMessage = "Critical error in version resolution"
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
