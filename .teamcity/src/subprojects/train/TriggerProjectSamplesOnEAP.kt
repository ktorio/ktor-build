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
import subprojects.benchmarks.ProjectBenchmarks.buildType
import subprojects.build.*
import subprojects.build.samples.*
import subprojects.build.samples.BuildPluginSampleSettings
import subprojects.build.samples.buildPluginSamples

object EapConstants {
    const val PUBLISH_EAP_BUILD_TYPE_ID = "KtorPublish_AllEAP"
    const val EAP_VERSION_REGEX = ">[0-9][^<]*-eap-[0-9]*<"
    const val KTOR_EAP_METADATA_URL =
        "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml"
    const val KTOR_COMPILER_PLUGIN_METADATA_URL =
        "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-compiler-plugin/maven-metadata.xml"
}

object EapRepositoryConfig {
    const val KTOR_EAP_URL = "https://maven.pkg.jetbrains.space/public/p/ktor/eap"
    const val COMPOSE_DEV_URL = "https://maven.pkg.jetbrains.space/public/p/compose/dev"

    fun generateGradleRepositories(): String = """
        repositories {
            maven("$KTOR_EAP_URL")
            maven("$COMPOSE_DEV_URL")
            mavenCentral()
            gradlePluginPortal()
        }
    """.trimIndent()

    fun generateMavenRepository(): String = """
        <repositories>
            <repository>
                <id>ktor-eap</id>
                <url>$KTOR_EAP_URL</url>
            </repository>
            <repository>
                <id>compose-dev</id>
                <url>$COMPOSE_DEV_URL</url>
            </repository>
        </repositories>
    """.trimIndent()

    fun generateSettingsContent(): String = """
        pluginManagement {
            repositories {
                maven("$KTOR_EAP_URL")
                maven("$COMPOSE_DEV_URL")
                gradlePluginPortal()
                mavenCentral()
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
            pattern = "BUILD FAILED"
            failureMessage = "Build failed for $sampleName sample"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "FAILURE:"
            failureMessage = "Build failure detected in $sampleName sample"
            stopBuildOnFailure = true
        }
        executionTimeoutMin = 20
    }
}

fun BuildType.configureEAPSampleBuild(
    projectName: String,
    vcsRoot: VcsRoot,
    versionResolver: BuildType
) {
    vcs {
        root(vcsRoot)
    }

    requirements {
        agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)
    }

    params {
        defaultGradleParams()
        param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
        param("env.KTOR_COMPILER_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_COMPILER_PLUGIN_VERSION%")
    }

    addEAPSampleFailureConditions(projectName)
    defaultBuildFeatures(vcsRoot.id.toString())

    dependencies {
        dependency(versionResolver) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }
}

fun BuildSteps.createEAPGradleInitScript() {
    script {
        name = "Create EAP Gradle Init Script"
        scriptContent = """
            #!/bin/bash
            set -e
            
            echo "Creating EAP Gradle init script..."
            
            cat > gradle-eap-init.gradle <<EOF
            allprojects {
                ${EapRepositoryConfig.generateGradleRepositories()}
            }
            EOF
            
            echo "EAP init script created successfully"
        """.trimIndent()
    }
}

fun BuildSteps.debugEnvironmentVariables() {
    script {
        name = "Debug Environment Variables"
        scriptContent = """
            #!/bin/bash
            echo "=== Environment Variables ==="
            echo "KTOR_VERSION: %env.KTOR_VERSION%"
            echo "KTOR_COMPILER_PLUGIN_VERSION: %env.KTOR_COMPILER_PLUGIN_VERSION%"
            echo "==============================="
        """.trimIndent()
    }
}

fun BuildSteps.createEAPSampleSettings(samplePath: String) {
    script {
        name = "Create EAP Sample Settings"
        scriptContent = """
            #!/bin/bash
            set -e
            
            if [ -d "$samplePath" ]; then
                echo "Creating EAP settings for $samplePath"
                
                # Backup original settings if exists
                if [ -f "$samplePath/settings.gradle.kts" ]; then
                    cp "$samplePath/settings.gradle.kts" "$samplePath/settings.gradle.kts.backup"
                fi
                
                # Create EAP settings
                cat > "$samplePath/settings.gradle.kts" <<EOF
${EapRepositoryConfig.generateSettingsContent()}

rootProject.name = "$(basename $samplePath)"
EOF
                
                echo "EAP settings created for $samplePath"
            fi
        """.trimIndent()
    }
}

fun BuildSteps.restoreEAPSampleSettings(samplePath: String) {
    script {
        name = "Restore Original Sample Settings"
        scriptContent = """
            #!/bin/bash
            
            if [ -f "$samplePath/settings.gradle.kts.backup" ]; then
                echo "Restoring original settings for $samplePath"
                mv "$samplePath/settings.gradle.kts.backup" "$samplePath/settings.gradle.kts"
                echo "Original settings restored"
            fi
        """.trimIndent()
        executionMode = BuildStep.ExecutionMode.ALWAYS
    }
}

fun BuildSteps.createEAPMavenRepositoryConfig(samplePath: String) {
    script {
        name = "Setup EAP Maven Repositories"
        scriptContent = """
            #!/bin/bash
            set -e
            
            echo "=== Setting up Maven sample: $samplePath ==="
            
            # Debug information
            echo "Current directory: $(pwd)"
            echo "Listing root directory:"
            ls -la . || echo "Cannot list root directory"
            
            echo "Checking if sample path exists..."
            if [ ! -d "$samplePath" ]; then
                echo "ERROR: Sample directory '$samplePath' does not exist"
                echo "Available directories in current directory:"
                ls -la . | grep "^d" || echo "No directories found"
                exit 1
            fi
            
            echo "Sample directory exists, listing contents:"
            ls -la "$samplePath"
            
            if [ ! -f "$samplePath/pom.xml" ]; then
                echo "ERROR: Maven pom.xml not found at $samplePath/pom.xml"
                echo "Contents of $samplePath:"
                ls -la "$samplePath"
                exit 1
            fi
            
            echo "Found pom.xml, setting up EAP repositories..."
            
            # Backup original pom.xml
            cp "$samplePath/pom.xml" "$samplePath/pom.xml.backup"
            echo "Created backup of original pom.xml"
            
            # Check if repositories section already exists
            if grep -q "<repositories>" "$samplePath/pom.xml"; then
                echo "Repositories section already exists, adding EAP repositories to it"
                # Insert EAP repositories before the closing </repositories> tag
                sed -i '/<\/repositories>/i\
        <repository>\
            <id>ktor-eap</id>\
            <url>${EapRepositoryConfig.KTOR_EAP_URL}</url>\
        </repository>\
        <repository>\
            <id>compose-dev</id>\
            <url>${EapRepositoryConfig.COMPOSE_DEV_URL}</url>\
        </repository>' "$samplePath/pom.xml"
            else
                echo "No repositories section found, adding new repositories section"
                # Add repositories section after the <project> opening tag
                sed -i '/<project[^>]*>/a\
${EapRepositoryConfig.generateMavenRepository()}' "$samplePath/pom.xml"
            fi
            
            echo "EAP repositories added to $samplePath/pom.xml"
            echo "Modified pom.xml content:"
            cat "$samplePath/pom.xml"
        """.trimIndent()
    }
}

fun BuildSteps.restoreEAPMavenConfig(samplePath: String) {
    script {
        name = "Restore Original Maven Config"
        scriptContent = """
            #!/bin/bash
            
            if [ -f "$samplePath/pom.xml.backup" ]; then
                echo "Restoring original pom.xml for $samplePath"
                mv "$samplePath/pom.xml.backup" "$samplePath/pom.xml"
                echo "Original pom.xml restored"
            else
                echo "No backup found for $samplePath/pom.xml"
            fi
        """.trimIndent()
        executionMode = BuildStep.ExecutionMode.ALWAYS
    }
}

fun BuildSteps.buildEAPGradlePluginSample(relativeDir: String) {
    gradle {
        name = "Build $relativeDir EAP sample"
        tasks = "build"
        param("org.gradle.project.includeBuild", "samples/$relativeDir")
        workingDir = "."
        jdkHome = Env.JDK_LTS
    }
}

fun BuildSteps.buildEAPGradleSample(relativeDir: String, standalone: Boolean) {
    if (standalone) {
        buildEAPGradlePluginSample(relativeDir)
    } else {
        gradle {
            name = "Build $relativeDir EAP sample"
            tasks = "build"
            workingDir = relativeDir
            jdkHome = Env.JDK_LTS
        }
    }
}

fun BuildSteps.buildEAPMavenSample(relativeDir: String) {
    maven {
        name = "Build $relativeDir EAP sample (Maven)"
        goals = "clean compile test"
        workingDir = relativeDir
        pomLocation = "$relativeDir/pom.xml"
        jdkHome = Env.JDK_LTS
    }
}

fun SampleProjectSettings.asEAPSampleConfig(versionResolver: BuildType): EAPSampleConfig =
    object : EAPSampleConfig {
        override val projectName = this@asEAPSampleConfig.projectName

        override fun createEAPBuildType(): BuildType = buildType {
            id("KtorEAPSample_${projectName.replace('-', '_')}")
            name = "EAP Validate $projectName sample"

            configureEAPSampleBuild(projectName, this@asEAPSampleConfig.vcsRoot, versionResolver)

            steps {
                debugEnvironmentVariables()

                when (this@asEAPSampleConfig.buildSystem) {
                    BuildSystem.GRADLE -> {
                        createEAPGradleInitScript()
                        createEAPSampleSettings("samples/$projectName")
                        buildEAPGradleSample(projectName, this@asEAPSampleConfig.standalone)
                        restoreEAPSampleSettings("samples/$projectName")
                    }

                    BuildSystem.MAVEN -> {
                        createEAPMavenRepositoryConfig(projectName)
                        buildEAPMavenSample(projectName)
                        restoreEAPMavenConfig(projectName)
                    }
                }
            }
        }
    }

fun BuildPluginSampleSettings.asBuildPluginEAPSampleConfig(versionResolver: BuildType): EAPSampleConfig =
    object : EAPSampleConfig {
        override val projectName = this@asBuildPluginEAPSampleConfig.projectName

        override fun createEAPBuildType(): BuildType = buildType {
            id("KtorEAPGradlePluginSample_${projectName.replace('-', '_')}")
            name = "EAP Validate $projectName sample"

            configureEAPSampleBuild(projectName, this@asBuildPluginEAPSampleConfig.vcsRoot, versionResolver)

            steps {
                debugEnvironmentVariables()
                createEAPGradleInitScript()

                buildEAPGradlePluginSample(projectName)
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
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "")
        }

        steps {
            debugEnvironmentVariables()

            script {
                name = "Fetch Latest EAP Framework Version"
                scriptContent = """
                    #!/bin/bash
                    set -e

                    echo "=== Fetching Latest Ktor EAP Framework Version ==="

                    # Fetch Ktor BOM version
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

                    # Fetch Ktor Compiler Plugin version
                    echo "=== Fetching Latest Ktor Compiler Plugin Version ==="

                    COMPILER_METADATA_URL="${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}"
                    TEMP_COMPILER_METADATA=$(mktemp)

                    echo "Fetching compiler plugin metadata from: ${'$'}COMPILER_METADATA_URL"

                    if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}COMPILER_METADATA_URL" -o "${'$'}TEMP_COMPILER_METADATA" 2>/dev/null; then
                        echo "Successfully fetched compiler plugin metadata"
                        echo "Compiler plugin metadata content:"
                        cat "${'$'}TEMP_COMPILER_METADATA"

                        LATEST_COMPILER_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_COMPILER_METADATA" | sed 's/<latest>//;s/<\/latest>//')

                        if [ -z "${'$'}LATEST_COMPILER_VERSION" ]; then
                            LATEST_COMPILER_VERSION=$(grep -o "${EapConstants.EAP_VERSION_REGEX}" "${'$'}TEMP_COMPILER_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
                        fi

                        if [ -n "${'$'}LATEST_COMPILER_VERSION" ]; then
                            echo "Found latest compiler plugin version: ${'$'}LATEST_COMPILER_VERSION"
                            echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}LATEST_COMPILER_VERSION']"
                        else
                            echo "ERROR: No compiler plugin version found in metadata"
                            echo "Metadata content:"
                            cat "${'$'}TEMP_COMPILER_METADATA"
                            exit 1
                        fi

                        rm -f "${'$'}TEMP_COMPILER_METADATA"
                    else
                        echo "ERROR: Failed to fetch compiler plugin metadata from ${'$'}COMPILER_METADATA_URL"
                        exit 1
                    fi

                    echo "=== Compiler Plugin Version Set Successfully ==="
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

                    if [ -z "%env.KTOR_COMPILER_PLUGIN_VERSION%" ] || [ "%env.KTOR_COMPILER_PLUGIN_VERSION%" = "" ]; then
                        echo "CRITICAL ERROR: KTOR_COMPILER_PLUGIN_VERSION is not set after resolution"
                        exit 1
                    fi

                    echo "✓ Framework version validated: %env.KTOR_VERSION%"
                    echo "✓ Compiler plugin version validated: %env.KTOR_COMPILER_PLUGIN_VERSION%"
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

    val allEAPSamples: List<EAPSampleConfig> = sampleProjects.map { it.asEAPSampleConfig(versionResolver) } +
        buildPluginSamples.map { it.asBuildPluginEAPSampleConfig(versionResolver) }

    val allSampleBuilds = allEAPSamples.map { it.createEAPBuildType() }

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
