package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.Agents.ANY
import subprojects.Agents.Arch
import subprojects.Agents.OS
import subprojects.build.*
import subprojects.build.samples.*

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
            maven { url = uri("$KTOR_EAP_URL") }
            maven { url = uri("$COMPOSE_DEV_URL") }
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
                maven { url = uri("$KTOR_EAP_URL") }
                maven { url = uri("$COMPOSE_DEV_URL") }
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
    defaultBuildFeatures()

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

        override fun createEAPBuildType(): BuildType = BuildType {
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

        override fun createEAPBuildType(): BuildType = BuildType {
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

    val versionResolver = EAPVersionResolver.createVersionResolver(
        id = "KtorEAPVersionResolver",
        name = "Set EAP Version for Tests",
        description = "Determines the EAP version to use for sample validation"
    )

    buildType(versionResolver)


    val allEAPSamples: List<EAPSampleConfig> = sampleProjects.map { it.asEAPSampleConfig(versionResolver) } +
        buildPluginSamples.map { it.asBuildPluginEAPSampleConfig(versionResolver) }

    val allSampleBuilds = allEAPSamples.map { it.createEAPBuildType() }

    allSampleBuilds.forEach { buildType(it) }

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
                branchFilter = "+:refs/heads/*"
            }
        }

        dependencies {
            dependency(versionResolver) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                }
            }

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
