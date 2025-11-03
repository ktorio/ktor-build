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

    fun generateSettingsContent(isPluginSample: Boolean): String {
        val baseSettings = if (isPluginSample) {
            """
pluginManagement {
    repositories {
        ${generateGradlePluginRepositories()}
    }
    
    ${generatePluginResolutionStrategy()}
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        ${generateGradleRepositories()}
    }
}
            """.trimIndent()
        } else {
            """
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        ${generateGradleRepositories()}
    }
}
            """.trimIndent()
        }

        return baseSettings
    }

    fun generateEAPPluginManagementContent(): String = """
pluginManagement {
    repositories {
        ${generateGradlePluginRepositories()}
    }
    
    ${generatePluginResolutionStrategy()}
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
            pattern = "No agents available to run"
            failureMessage = "No compatible agents found for $sampleName sample"
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
            failureMessage = "No suitable agents available for $sampleName sample"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "KTOR_GRADLE_PLUGIN_VERSION environment variable is not set"
            failureMessage = "Plugin version not resolved properly - build configuration error"
            stopBuildOnFailure = true
        }
        executionTimeoutMin = 10
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
        mavenCentral()
        google()
        maven("${EapRepositoryConfig.COMPOSE_DEV_URL}")
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
        name = "Validate Plugin Version"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            if [ -z "%env.KTOR_VERSION%" ]; then
                echo "ERROR: KTOR_VERSION environment variable is not set"
                exit 1
            fi
            
            if [ -z "%env.KTOR_GRADLE_PLUGIN_VERSION%" ]; then
                echo "ERROR: KTOR_GRADLE_PLUGIN_VERSION environment variable is not set"
                echo "This should have been resolved by the version resolver build step"
                exit 1
            fi
            
            echo "Framework EAP version: %env.KTOR_VERSION%"
            echo "Plugin EAP version: %env.KTOR_GRADLE_PLUGIN_VERSION%"
            
            if [[ "%env.KTOR_GRADLE_PLUGIN_VERSION%" =~ ^-eap- ]]; then
                echo "ERROR: Invalid plugin version detected: %env.KTOR_GRADLE_PLUGIN_VERSION%"
                echo "Plugin version should not start with '-eap-'"
                exit 1
            fi
            
            echo "Plugin version validation passed"
        """.trimIndent()
    }

    script {
        name = "Create EAP Gradle Plugin init script"
        executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
        scriptContent = """
            mkdir -p %system.teamcity.build.tempDir%
            
            echo "Using Ktor Framework EAP version: %env.KTOR_VERSION%"
            echo "Using Ktor Gradle Plugin version: %env.KTOR_GRADLE_PLUGIN_VERSION%"
            
            echo "Fixing build.gradle.kts files in gradle plugin samples..."
            find samples -name "build.gradle.kts" -type f 2>/dev/null | while read build_file; do
                if [ -f "${'$'}build_file" ]; then
                    echo "Processing ${'$'}build_file"
                    
                    cp "${'$'}build_file" "${'$'}build_file.backup"
                    
                    if grep -q "alias(libs\.plugins\." "${'$'}build_file"; then
                        echo "Found version catalog aliases in ${'$'}build_file, replacing..."
                        
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
        maven("${EapRepositoryConfig.COMPOSE_DEV_URL}")
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
            echo "Restoring build.gradle.kts files from backups..."
            
            find samples -name "build.gradle.kts.backup" -type f 2>/dev/null | while read backup_file; do
                if [ -f "${'$'}backup_file" ]; then
                    original_file="${'$'}{backup_file%.backup}"
                    echo "Restoring ${'$'}original_file from backup"
                    mv "${'$'}backup_file" "${'$'}original_file"
                fi
            done
            
            echo "Build files restoration completed"
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
            
            trap 'rm -f "${'$'}TEMP_SETTINGS" "${'$'}BACKUP_FILE"' EXIT
            
            if [ -f "${'$'}{SETTINGS_FILE}" ]; then
                if cp "${'$'}{SETTINGS_FILE}" "${'$'}BACKUP_FILE"; then
                    echo "Backed up existing settings file to ${'$'}BACKUP_FILE"
                else
                    echo "Failed to backup existing settings file" >&2
                    exit 1
                fi
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
            
            for backup_file in "${'$'}{SETTINGS_FILE}.backup."*; do
                if [ -f "${'$'}backup_file" ]; then
                    echo "Restoring ${'$'}SETTINGS_FILE from ${'$'}backup_file"
                    mv "${'$'}backup_file" "${'$'}SETTINGS_FILE"
                    break
                fi
            done
            
            echo "Sample settings restoration completed"
        """.trimIndent()
    }
}

fun BuildSteps.buildEAPGradleSample(relativeDir: String, standalone: Boolean) {
    createEAPGradleInitScript()

    if (!standalone) {
        createEAPSampleSettings(relativeDir, false)
    } else {
        modifyRootSettingsForEAP()
    }

    gradle {
        name = "Build EAP Sample (Gradle)"
        tasks = "build"

        if (!standalone) {
            workingDir = relativeDir
        }

        gradleParams = "--init-script %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts -Dorg.gradle.daemon=false"
        jdkHome = Env.JDK_LTS
    }

    if (!standalone) {
        restoreEAPSampleSettings(relativeDir)
    } else {
        restoreRootSettings()
    }
}

fun BuildSteps.buildEAPGradlePluginSample(relativeDir: String, standalone: Boolean) {
    createEAPGradlePluginInitScript()

    if (!standalone) {
        createEAPSampleSettings(relativeDir, true)
    } else {
        modifyRootSettingsForEAP()
    }

    gradle {
        name = "Build EAP Sample (Gradle Plugin)"
        tasks = "build"

        if (!standalone) {
            workingDir = relativeDir
        }

        gradleParams = "--init-script %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts -Dorg.gradle.daemon=false"
        jdkHome = Env.JDK_LTS
    }

    restoreGradlePluginSampleBuildFiles()
    if (!standalone) {
        restoreEAPSampleSettings(relativeDir)
    } else {
        restoreRootSettings()
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
            
            if [ -f "${'$'}SETTINGS_FILE" ]; then
                cp "${'$'}SETTINGS_FILE" "${'$'}BACKUP_FILE"
                echo "Backed up original settings.gradle.kts"
            fi
            
            cat > "${'$'}EAP_SETTINGS_FILE" << 'EOF'
${EapRepositoryConfig.generateEAPPluginManagementContent()}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
        maven("${EapRepositoryConfig.COMPOSE_DEV_URL}")
        maven {
            name = "KtorEAP"
            url = uri("${EapRepositoryConfig.KTOR_EAP_URL}")
            content {
                includeGroup("io.ktor")
            }
        }
    }
}
EOF
            
            if [ -f "${'$'}SETTINGS_FILE" ]; then
                echo "apply(from = \"eap-settings.gradle.kts\")" > "${'$'}SETTINGS_FILE.tmp"
                echo "" >> "${'$'}SETTINGS_FILE.tmp"
                cat "${'$'}SETTINGS_FILE" >> "${'$'}SETTINGS_FILE.tmp"
                mv "${'$'}SETTINGS_FILE.tmp" "${'$'}SETTINGS_FILE"
                echo "Applied EAP settings to existing settings.gradle.kts"
            else
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
            
            if [ -f "${'$'}BACKUP_FILE" ]; then
                mv "${'$'}BACKUP_FILE" "${'$'}SETTINGS_FILE"
                echo "Restored original settings.gradle.kts"
            fi
            
            if [ -f "${'$'}EAP_SETTINGS_FILE" ]; then
                rm -f "${'$'}EAP_SETTINGS_FILE"
                echo "Removed EAP settings file"
            fi
        """.trimIndent()
    }
}

fun BuildSteps.buildEAPMavenSample(relativeDir: String) {
    script {
        name = "Modify Maven pom.xml for EAP"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            POM_FILE="$relativeDir/pom.xml"
            BACKUP_FILE="$relativeDir/pom.xml.backup"
            
            if [ ! -f "${'$'}POM_FILE" ]; then
                echo "ERROR: pom.xml not found at ${'$'}POM_FILE"
                exit 1
            fi
            
            cp "${'$'}POM_FILE" "${'$'}BACKUP_FILE"
            
            sed -i.tmp "/<\/repositories>/ i\\
${EapRepositoryConfig.generateMavenRepository()}" "${'$'}POM_FILE"
            
            rm -f "${'$'}POM_FILE.tmp"
            echo "Added EAP repository to pom.xml"
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
            POM_FILE="$relativeDir/pom.xml"
            BACKUP_FILE="$relativeDir/pom.xml.backup"
            
            if [ -f "${'$'}BACKUP_FILE" ]; then
                mv "${'$'}BACKUP_FILE" "${'$'}POM_FILE"
                echo "Restored original pom.xml"
            fi
        """.trimIndent()
    }
}

fun BuildPluginSampleSettings.asEAPSampleConfig(versionResolver: BuildType): EAPSampleConfig = object : EAPSampleConfig {
    override val projectName = this@asEAPSampleConfig.projectName

    override fun createEAPBuildType(): BuildType {
        return BuildType {
            id("EAP_KtorGradlePluginSamplesValidate_${projectName.replace('-', '_')}")
            name = "EAP Validate $projectName Gradle plugin sample"
            vcs {
                root(VCSSamples)
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
                buildEAPGradlePluginSample(
                    this@asEAPSampleConfig.projectName,
                    this@asEAPSampleConfig.standalone
                )
            }

            addEAPSampleFailureConditions(this@asEAPSampleConfig.projectName)

            defaultBuildFeatures(VCSSamples.id.toString())
        }
    }
}

fun SampleProjectSettings.asEAPSampleConfig(versionResolver: BuildType): EAPSampleConfig = object : EAPSampleConfig {
    override val projectName = this@asEAPSampleConfig.projectName

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

            addEAPSampleFailureConditions(this@asEAPSampleConfig.projectName)

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
