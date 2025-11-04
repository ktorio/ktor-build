package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.Agents.ANY
import subprojects.Agents.Arch
import subprojects.Agents.OS
import subprojects.build.*
import subprojects.build.samples.*

object EapConstants {
    const val PUBLISH_EAP_BUILD_TYPE_ID = "KtorPublish_AllEAP"
    const val EAP_VERSION_REGEX = ">[0-9][^<]*-eap-[0-9]*<"
    const val KTOR_EAP_METADATA_URL = "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml"
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
            name = "KtorEAP"  
            url = uri("$KTOR_EAP_URL")
            content {
                includeGroup("io.ktor.plugin")
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

            KTOR_VERSION_VAL="%env.KTOR_VERSION%"
            echo "Using Ktor Framework EAP version: ${'$'}KTOR_VERSION_VAL"

            if [ -z "${'$'}KTOR_VERSION_VAL" ]; then
                echo "ERROR: KTOR_VERSION environment variable is not properly resolved"
                echo "Raw value from TeamCity parameter: %env.KTOR_VERSION%"
                exit 1
            fi

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
                    useVersion("${'$'}KTOR_VERSION_VAL")
                    logger.lifecycle("Forcing Ktor dependency \${'$'}{'${'$'}'}{requested.name} to use EAP version: ${'$'}KTOR_VERSION_VAL")
                }
            }
        }
    }

    afterEvaluate {
        if (this == rootProject) {
            logger.lifecycle("Project \${'$'}{name}: Using Ktor Framework EAP version: ${'$'}KTOR_VERSION_VAL")
            logger.lifecycle("Project \${'$'}{name}: EAP repository configured for framework")
        }
        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            try {
                val kotlinExt = extensions.findByName("kotlin")
                if (kotlinExt != null) {
                    val kotlinExtClass = kotlinExt::class.java
                    val targetsMethod = kotlinExtClass.getMethod("getTargets")
                    val targets = targetsMethod.invoke(kotlinExt)
                    val targetsClass = targets::class.java
                    val findByNameMethod = targetsClass.getMethod("findByName", String::class.java)
                    val jsTarget = findByNameMethod.invoke(targets, "js")

                    if (jsTarget != null) {
                        rootProject.extensions.findByName("kotlinNodeJs")?.let { nodeJs ->
                            val nodeJsClass = nodeJs::class.java
                            try {
                                val downloadField = nodeJsClass.getDeclaredField("download")
                                downloadField.isAccessible = true
                                downloadField.setBoolean(nodeJs, true)

                                val downloadBaseUrlField = nodeJsClass.getDeclaredField("downloadBaseUrl")
                                downloadBaseUrlField.isAccessible = true
                                downloadBaseUrlField.set(nodeJs, "https://nodejs.org/dist")

                                val versionField = nodeJsClass.getDeclaredField("version")
                                versionField.isAccessible = true
                                versionField.set(nodeJs, "18.19.0")

                                logger.lifecycle("Configured Node.js for EAP build")
                            } catch (e: Exception) {
                                logger.warn("Could not configure Node.js settings: " + e.message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Could not configure Kotlin Multiplatform settings: " + e.message)
            }
        }
    }
}
EOF

            echo "==== EAP Gradle Init Script Content ===="
            cat %system.teamcity.build.tempDir%/ktor-eap.init.gradle.kts
            echo "========================================"
        """.trimIndent()
    }
}

fun BuildSteps.debugEnvironmentVariables() {
    script {
        name = "Debug Environment Variables"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            echo "=== Environment Variables Debug ==="
            echo "KTOR_VERSION env var: %env.KTOR_VERSION%"
            echo "KTOR_GRADLE_PLUGIN_VERSION env var: %env.KTOR_GRADLE_PLUGIN_VERSION%"
            echo "Shell KTOR_VERSION: ${'$'}KTOR_VERSION"
            echo "Shell KTOR_GRADLE_PLUGIN_VERSION: ${'$'}KTOR_GRADLE_PLUGIN_VERSION"
            
            echo "=================================="
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
            agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)
        }

        params {
            defaultGradleParams()
            param("teamcity.build.skipDependencyBuilds", "true")
            param("teamcity.runAsFirstBuild", "true")
            param("env.KTOR_VERSION", "")
        }

        steps {
            debugEnvironmentVariables()

            script {
                name = "Fetch Latest EAP Framework Version"
                scriptContent = """
                    #!/bin/bash
                    set -e

                    echo "=== Fetching Latest Ktor EAP Framework Version ==="

                    METADATA_URL="${EapConstants.KTOR_EAP_METADATA_URL}"
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
                name = "Final Validation"
                scriptContent = """
                    #!/bin/bash
                    set -e

                    echo "=== Final Validation of Resolved Versions ==="

                    if [ -z "%env.KTOR_VERSION%" ] || [ "%env.KTOR_VERSION%" = "" ]; then
                        echo "CRITICAL ERROR: KTOR_VERSION is not set after resolution"
                        exit 1
                    fi

                    echo "✓ Framework version validated: %env.KTOR_VERSION%"
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

    val allEAPSamples: List<EAPSampleConfig> = sampleProjects.map { it.asEAPSampleConfig(versionResolver) }

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
