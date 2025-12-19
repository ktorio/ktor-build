
package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.Agents.Arch
import subprojects.Agents.OS
import subprojects.build.defaultBuildFeatures
import subprojects.build.defaultGradleParams

enum class SpecialHandling {
    KOTLIN_MULTIPLATFORM,
    AMPER_GRADLE_HYBRID,
    DOCKER_TESTCONTAINERS,
    DAGGER_ANNOTATION_PROCESSING,
    ANDROID_SDK_REQUIRED,
    ENHANCED_TOML_PATTERNS,
    HIGH_MEMORY_REQUIRED
}

enum class ExternalSampleBuildType {
    GRADLE, AMPER
}

interface ExternalEAPSampleConfig : EAPSampleConfig {
    val vcsRoot: VcsRoot
    val buildType: ExternalSampleBuildType
    val versionResolver: BuildType
    val specialHandling: List<SpecialHandling>
}

object SpecialHandlingUtils {
    fun requiresDocker(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DOCKER_TESTCONTAINERS)

    fun requiresAndroidSDK(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.ANDROID_SDK_REQUIRED)

    fun requiresDagger(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)

    fun requiresEnhancedTomlHandling(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.ENHANCED_TOML_PATTERNS)

    fun requiresHighMemory(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.HIGH_MEMORY_REQUIRED)
}

abstract class BaseExternalEAPSample : EAPSampleConfig {
    protected fun BuildType.addCommonExternalEAPConfiguration(sampleName: String, specialHandling: List<SpecialHandling>) {
        addExternalEAPSampleFailureConditions(sampleName, specialHandling)
        defaultBuildFeatures()

        features {
            with(EAPBuildFeatures) {
                addEAPSlackNotifications()
            }
        }
    }
}

object VCSKtorArrowExample : KtorVcsRoot({
    name = "Ktor Arrow Example"
    url = "https://github.com/nomisRev/ktor-arrow-example.git"
})

object VCSKtorAiServer : KtorVcsRoot({
    name = "Ktor AI Server"
    url = "https://github.com/nomisRev/ktor-ai-server.git"
})

object VCSKtorNativeServer : KtorVcsRoot({
    name = "Ktor Native Server"
    url = "https://github.com/nomisRev/ktor-native-server.git"
})

object VCSKtorKoogExample : KtorVcsRoot({
    name = "Ktor Koog Example"
    url = "https://github.com/nomisRev/ktor-koog-example.git"
})

object VCSFullStackKtorTalk : KtorVcsRoot({
    name = "Full Stack Ktor Talk"
    url = "https://github.com/nomisRev/full-stack-ktor-talk.git"
})

object VCSKtorConfigExample : KtorVcsRoot({
    name = "Ktor Config Example"
    url = "https://github.com/nomisRev/ktor-config-example.git"
})

object VCSKtorWorkshop2025 : KtorVcsRoot({
    name = "Ktor Workshop 2025"
    url = "https://github.com/nomisRev/ktor-workshop-2025.git"
})

object VCSAmperKtorSample : KtorVcsRoot({
    name = "Amper Ktor Sample"
    url = "https://github.com/nomisRev/amper-ktor-sample.git"
})

object VCSKtorDIOverview : KtorVcsRoot({
    name = "Ktor DI Overview"
    url = "https://github.com/nomisRev/Ktor-DI-Overview.git"
})

object VCSKtorFullStackRealWorld : KtorVcsRoot({
    name = "Ktor Full Stack Real World"
    url = "https://github.com/nomisRev/ktor-full-stack-real-world.git"
})

fun BuildType.addExternalEAPSampleFailureConditions(sampleName: String, specialHandling: List<SpecialHandling>) {
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

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "OutOfMemoryError"
            failureMessage = "Out of memory error in $sampleName - hosted agent memory exceeded"
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Gradle build daemon disappeared"
            failureMessage = "Gradle daemon crashed for $sampleName - likely memory exhaustion on hosted agent"
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Java heap space"
            failureMessage = "Java heap space exhausted for $sampleName on hosted agent"
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "CRITICAL ERROR:"
            failureMessage = "Critical error in $sampleName build"
            stopBuildOnFailure = true
        }

        if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "docker: command not found"
                failureMessage = "Docker not available for $sampleName"
                stopBuildOnFailure = true
            }

            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "Cannot connect to the Docker daemon"
                failureMessage = "Docker daemon not accessible for $sampleName"
                stopBuildOnFailure = true
            }
        }

        if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "ANDROID_HOME"
                failureMessage = "Android SDK not configured for $sampleName"
                stopBuildOnFailure = true
            }
        }

        executionTimeoutMin = if (SpecialHandlingUtils.requiresHighMemory(specialHandling)) 40 else 25
    }
}

object ExternalSampleScripts {

    fun monitorHostedAgentResources() = """
        monitor_hosted_agent_resources() {
            echo "=== Hosted Agent Resource Monitoring ==="
            echo "Agent Name: %teamcity.agent.name%"
            echo "Build ID: %teamcity.build.id%"
            
            if command -v free >/dev/null 2>&1; then
                echo "=== System Memory Status ==="
                free -h
            else
                echo "Memory monitoring not available on this hosted agent"
            fi
            
            echo "=== Java Memory Allocation Test ==="
            java -Xmx1g -version >/dev/null 2>&1 && echo "✓ 1GB allocation: OK" || echo "✗ 1GB allocation: FAILED"
            java -Xmx1536m -version >/dev/null 2>&1 && echo "✓ 1.5GB allocation: OK" || echo "✗ 1.5GB allocation: FAILED"
            java -Xmx2g -version >/dev/null 2>&1 && echo "✓ 2GB allocation: OK" || echo "✗ 2GB allocation: FAILED"
            
            echo "=== Disk Space Status ==="
            df -h . 2>/dev/null || echo "Disk space monitoring not available"
            
            echo "✓ Resource monitoring completed"
        }
        monitor_hosted_agent_resources
    """.trimIndent()

    fun analyzeProjectStructure() = """
        analyze_project_structure() {
            echo "=== Analyzing Project Structure ==="
            
            touch project_analysis.env
            
            if [ -f "build.gradle.kts" ] || [ -f "build.gradle" ]; then
                echo "BUILD_SYSTEM=gradle" >> project_analysis.env
                echo "✓ Detected Gradle build system"
            elif [ -f "module.yaml" ]; then
                echo "BUILD_SYSTEM=amper" >> project_analysis.env
                echo "✓ Detected Amper build system"
            fi
            
            if grep -r "kotlin.*multiplatform" . --include="*.gradle*" --include="*.kts" 2>/dev/null; then
                echo "KMP_PROJECT=true" >> project_analysis.env
                echo "✓ Detected Kotlin Multiplatform project"
            fi
            
            if grep -r "includeBuild\|include.*Build" . --include="settings.gradle*" 2>/dev/null; then
                echo "COMPOSITE_BUILD=true" >> project_analysis.env
                echo "✓ Detected composite build configuration"
            fi
            
            if [ -f "gradle/libs.versions.toml" ]; then
                echo "VERSION_CATALOG=true" >> project_analysis.env
                echo "✓ Detected version catalog (libs.versions.toml)"
            fi
            
            if [ -f "gradle/libs.versions.toml" ]; then
                echo "=== TOML Pattern Analysis ==="
                for toml_pattern in "ktor" "ktor-version" "ktorVersion" "ktor_version" "ktor-client" "ktor-server"; do
                    if grep -q "^[[:space:]]*${'$'}{toml_pattern}[[:space:]]*=" gradle/libs.versions.toml; then
                        echo "TOML_PATTERN_${'$'}{toml_pattern}=true" >> project_analysis.env
                        echo "✓ Found TOML pattern: ${'$'}{toml_pattern}"
                    fi
                done
            fi
            
            echo "✓ Project analysis completed"
        }
        analyze_project_structure
    """.trimIndent()

    fun backupConfigFiles() = """
        echo "=== Backing up configuration files ==="
        [ -f "gradle.properties" ] && cp gradle.properties gradle.properties.backup
        [ -f "gradle/libs.versions.toml" ] && cp gradle/libs.versions.toml gradle/libs.versions.toml.backup
        [ -f "settings.gradle.kts" ] && cp settings.gradle.kts settings.gradle.kts.backup
        [ -f "settings.gradle" ] && cp settings.gradle settings.gradle.backup
        [ -f "module.yaml" ] && cp module.yaml module.yaml.backup
        [ -f "settings.yaml" ] && cp settings.yaml settings.yaml.backup
        echo "✓ Configuration files backed up"
    """.trimIndent()

    fun updateVersionCatalogComprehensive() = """
        update_version_catalog_comprehensive() {
            if [ -f "gradle/libs.versions.toml" ]; then
                echo "=== Comprehensive Version Catalog Update ==="

                local patterns=(
                    "ktor"
                    "ktor-client"
                    "ktor-server" 
                    "ktor-plugin"
                    "ktor-compiler-plugin"
                )

                for version_pattern in "${'$'}{patterns[@]}"; do
                    if grep -q "^[[:space:]]*${'$'}{version_pattern}[[:space:]]*=" gradle/libs.versions.toml; then
                        sed -i "s/^[[:space:]]*${'$'}{version_pattern}[[:space:]]*=.*/${'$'}{version_pattern} = \"%env.KTOR_VERSION%\"/" gradle/libs.versions.toml
                        echo "✓ Updated ${'$'}{version_pattern} version reference"
                    fi
                done

                if ! grep -q "^[[:space:]]*ktor[[:space:]]*=" gradle/libs.versions.toml; then
                    if grep -q "^\[versions\]" gradle/libs.versions.toml; then
                        sed -i '/^\[versions\]/a ktor = "%env.KTOR_VERSION%"' gradle/libs.versions.toml
                    else
                        echo -e "\n[versions]\nktor = \"%env.KTOR_VERSION%\"" >> gradle/libs.versions.toml
                    fi
                    echo "✓ Added ktor version to [versions] section"
                fi

                echo "✓ Comprehensive version catalog update completed"
            fi
        }
        update_version_catalog_comprehensive
    """.trimIndent()

    fun updateGradlePropertiesEnhanced(specialHandling: List<SpecialHandling>) = """
        update_gradle_properties_enhanced() {
            echo "=== Enhanced Gradle Properties Update (TeamCity Cloud Hosted Agents) ==="
            
            if ${if (SpecialHandlingUtils.requiresHighMemory(specialHandling)) "true" else "false"}; then
                HEAP_SIZE="2g"
                METASPACE_SIZE="512m"
                echo "Using high memory configuration for hosted agents: heap=${'$'}HEAP_SIZE, metaspace=${'$'}METASPACE_SIZE"
            else
                HEAP_SIZE="1536m"
                METASPACE_SIZE="256m"
                echo "Using standard memory configuration for hosted agents: heap=${'$'}HEAP_SIZE, metaspace=${'$'}METASPACE_SIZE"
            fi
            
            if [ -f "gradle.properties" ]; then
                echo "Updating existing gradle.properties..."
                grep -v "^ktor.*Version[[:space:]]*=" gradle.properties > gradle.properties.tmp || touch gradle.properties.tmp
                grep -v "^ktor\\..*\\.version[[:space:]]*=" gradle.properties.tmp > gradle.properties.tmp2 || cp gradle.properties.tmp gradle.properties.tmp2
                grep -v "^org\\.gradle\\.jvmargs[[:space:]]*=" gradle.properties.tmp2 > gradle.properties.tmp3 || cp gradle.properties.tmp2 gradle.properties.tmp3
                grep -v "^org\\.gradle\\.daemon[[:space:]]*=" gradle.properties.tmp3 > gradle.properties.tmp4 || cp gradle.properties.tmp3 gradle.properties.tmp4
                grep -v "^org\\.gradle\\.parallel[[:space:]]*=" gradle.properties.tmp4 > gradle.properties.tmp5 || cp gradle.properties.tmp4 gradle.properties.tmp5
                grep -v "^org\\.gradle\\.caching[[:space:]]*=" gradle.properties.tmp5 > gradle.properties.tmp6 || cp gradle.properties.tmp5 gradle.properties.tmp6
                
                cat >> gradle.properties.tmp6 << EOF
ktorVersion=%env.KTOR_VERSION%
ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%
org.gradle.daemon=false
org.gradle.jvmargs=-Xmx${'$'}HEAP_SIZE -XX:MaxMetaspaceSize=${'$'}METASPACE_SIZE -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=false
org.gradle.caching=false
org.gradle.configureondemand=false
EOF
                
                mv gradle.properties.tmp6 gradle.properties
                rm -f gradle.properties.tmp*
            else
                echo "Creating new gradle.properties..."
                cat > gradle.properties << EOF
ktorVersion=%env.KTOR_VERSION%
ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%
org.gradle.daemon=false
org.gradle.jvmargs=-Xmx${'$'}HEAP_SIZE -XX:MaxMetaspaceSize=${'$'}METASPACE_SIZE -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=false
org.gradle.caching=false
org.gradle.configureondemand=false
EOF
            fi
            
            echo "Final gradle.properties content:"
            cat gradle.properties
            echo "✓ Enhanced gradle.properties update completed for hosted agents"
        }
        update_gradle_properties_enhanced
    """.trimIndent()

    fun setupEnhancedGradleRepositories() = """
        setup_enhanced_gradle_repositories() {
            echo "=== Setting up Enhanced Gradle Repositories ==="
            cat > gradle-eap-init.gradle << EOF
allprojects {
    repositories {
        maven { url "${EapRepositoryConfig.KTOR_EAP_URL}" }
        maven { url "${EapRepositoryConfig.COMPOSE_DEV_URL}" }
        mavenCentral()
        gradlePluginPortal()
    }
}

gradle.allprojects { project ->
    project.buildscript {
        repositories {
            maven { url "${EapRepositoryConfig.KTOR_EAP_URL}" }
            maven { url "${EapRepositoryConfig.COMPOSE_DEV_URL}" }
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

gradle.settingsEvaluated { settings ->
    settings.pluginManagement {
        repositories {
            maven { url "${EapRepositoryConfig.KTOR_EAP_URL}" }
            maven { url "${EapRepositoryConfig.COMPOSE_DEV_URL}" }
            gradlePluginPortal()
            mavenCentral()
        }
    }
}
EOF
            echo "✓ Enhanced Gradle repositories configuration created"
        }
        setup_enhanced_gradle_repositories
    """.trimIndent()

    fun setupDockerEnvironment() = """
        #!/bin/bash
        set -e
        echo "Setting up Docker environment for EAP testing on hosted agent..."
        
        docker --version
        
        DOCKER_API_VERSION=$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null || echo "unknown")
        echo "Docker API Version: ${'$'}DOCKER_API_VERSION"
        
        if [ "${'$'}DOCKER_API_VERSION" != "unknown" ]; then
            MAJOR_VERSION=$(echo ${'$'}DOCKER_API_VERSION | cut -d. -f1)
            MINOR_VERSION=$(echo ${'$'}DOCKER_API_VERSION | cut -d. -f2)
            
            if [ ${'$'}MAJOR_VERSION -eq 1 ] && [ ${'$'}MINOR_VERSION -lt 44 ]; then
                echo "WARNING: Docker API version ${'$'}DOCKER_API_VERSION is too old. Minimum required is 1.44"
                echo "Attempting to use newer Docker client..."
                
                export DOCKER_API_VERSION=1.44
                echo "Set DOCKER_API_VERSION to 1.44"
            fi
        fi
        
        echo "Testing Docker functionality..."
        docker info > /dev/null 2>&1 && echo "✓ Docker is functional" || echo "⚠ Docker may have limitations on this hosted agent"
        
        echo "Docker setup completed successfully"
    """.trimIndent()

    fun setupDaggerEnvironment() = """
        #!/bin/bash
        set -e
        echo "Setting up Dagger annotation processing environment on hosted agent..."
        echo "Dagger environment setup completed"
    """.trimIndent()

    fun setupAndroidSDK() = """
        #!/bin/bash
        set -e
        echo "Setting up Android SDK environment on hosted agent..."
        if [ -z "${'$'}ANDROID_HOME" ]; then
            echo "ERROR: ANDROID_HOME not set on hosted agent"
            exit 1
        fi
        echo "Android SDK found at: ${'$'}ANDROID_HOME"
        echo "Android SDK setup completed"
    """.trimIndent()

    fun buildAmperProjectEnhanced() = """
        build_amper_project_enhanced() {
            echo "=== Enhanced Amper Build (Hosted Agent Optimized) ==="
            echo "Current directory contents:"
            ls -la
            
            if [ -f "module.yaml" ]; then
                echo "Found module.yaml - this is an Amper project"
                
                if [ -f "./amperw" ]; then
                    echo "Using Amper wrapper"
                    chmod +x ./amperw
                    ./amperw build
                elif [ -f "gradlew" ]; then
                    echo "Amper project with Gradle wrapper detected"
                    chmod +x ./gradlew
                    ./gradlew build --init-script gradle-eap-init.gradle --no-daemon --no-parallel --no-build-cache --stacktrace
                else
                    echo "ERROR: No build wrapper found for Amper project"
                    exit 1
                fi
            else
                echo "No module.yaml found - treating as regular Gradle project"
                if [ -f "./gradlew" ]; then
                    chmod +x ./gradlew
                    ./gradlew build --init-script gradle-eap-init.gradle --no-daemon --no-parallel --no-build-cache --stacktrace
                else
                    echo "ERROR: No gradlew found"
                    exit 1
                fi
            fi
            
            echo "✓ Enhanced Amper build completed successfully on hosted agent"
        }
        build_amper_project_enhanced
    """.trimIndent()

    fun cleanupBackups() = """
        cleanup_backups() {
            echo "=== Cleaning up backup files on hosted agent ==="
            rm -f *.backup gradle/libs.versions.toml.backup gradle-eap-init.gradle project_analysis.env
            echo "✓ Cleanup completed"
        }
        cleanup_backups
    """.trimIndent()
}

object EAPBuildSteps {
    fun BuildSteps.hostedAgentResourceMonitoring() {
        script {
            name = "Monitor Hosted Agent Resources"
            scriptContent = """
                #!/bin/bash
                set -e
                ${ExternalSampleScripts.monitorHostedAgentResources()}
            """.trimIndent()
        }
    }

    fun BuildSteps.standardEAPSetup() {
        script {
            name = "EAP Environment Setup"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== EAP Build Setup for TeamCity Cloud Hosted Agents ==="
                echo "Build Agent: %teamcity.agent.name%"
                echo "Build ID: %teamcity.build.id%"
                echo "Using Ktor version: %env.KTOR_VERSION%"
                echo "Using compiler plugin version: %env.KTOR_COMPILER_PLUGIN_VERSION%"
                echo "Hosted Agent Configuration: Optimized for 4-8GB memory"
                echo "=============================================================="
            """.trimIndent()
        }
    }

    fun BuildSteps.setupDockerEnvironment() {
        script {
            name = "Setup Docker Environment"
            scriptContent = ExternalSampleScripts.setupDockerEnvironment()
        }
    }

    fun BuildSteps.setupDaggerEnvironment() {
        script {
            name = "Setup Dagger Environment"
            scriptContent = ExternalSampleScripts.setupDaggerEnvironment()
        }
    }

    fun BuildSteps.setupAndroidEnvironment() {
        script {
            name = "Setup Android Environment"
            scriptContent = ExternalSampleScripts.setupAndroidSDK()
        }
    }


    fun BuildSteps.gradleEAPBuildWithMemoryHandling(projectName: String, specialHandling: List<SpecialHandling>) {
        gradle {
            name = "Build EAP Sample (Hosted Agent Optimized)"
            tasks = if (projectName == "ktor-ai-server" || projectName == "ktor-full-stack-real-world") {
                "assemble"
            } else {
                "build"
            }
            jdkHome = Env.JDK_LTS

            gradleParams = "--no-scan --no-build-cache --no-parallel --no-daemon --init-script gradle-eap-init.gradle --info"

            jvmArgs = buildString {
                append("-XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heap-dump.hprof")

                if (SpecialHandlingUtils.requiresHighMemory(specialHandling)) {
                    append(" -XX:G1HeapRegionSize=16m -XX:+UnlockExperimentalVMOptions")
                }

                if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                    append(" -Djava.security.egd=file:/dev/./urandom")
                }

                if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
                    append(" -Dkapt.verbose=false -Dkapt.use.worker.api=false")
                }
            }

            useGradleWrapper = true
            enableStacktrace = true
        }
    }

    fun BuildSteps.buildEnhancedEAPExternalGradleSample(projectName: String, specialHandling: List<SpecialHandling>) {
        script {
            name = "Analyze Project Structure"
            scriptContent = """
                #!/bin/bash
                set -e
                ${ExternalSampleScripts.analyzeProjectStructure()}
            """.trimIndent()
        }

        script {
            name = "Setup Enhanced EAP Configuration"
            scriptContent = """
                #!/bin/bash
                set -e
                ${ExternalSampleScripts.backupConfigFiles()}
                ${ExternalSampleScripts.updateGradlePropertiesEnhanced(specialHandling)}
                ${if (SpecialHandlingUtils.requiresEnhancedTomlHandling(specialHandling)) ExternalSampleScripts.updateVersionCatalogComprehensive() else "echo 'Skipping TOML handling'"}
                ${ExternalSampleScripts.setupEnhancedGradleRepositories()}
            """.trimIndent()
        }

        gradleEAPBuildWithMemoryHandling(projectName, specialHandling)
    }

    fun BuildSteps.buildEnhancedEAPExternalAmperSample() {
        script {
            name = "Setup Enhanced Amper EAP Configuration"
            scriptContent = """
                #!/bin/bash
                set -e
                ${ExternalSampleScripts.analyzeProjectStructure()}
                ${ExternalSampleScripts.backupConfigFiles()}
                ${ExternalSampleScripts.updateGradlePropertiesEnhanced(emptyList())}
                ${ExternalSampleScripts.setupEnhancedGradleRepositories()}
            """.trimIndent()
        }

        script {
            name = "Build Enhanced Amper Sample"
            scriptContent = """
                #!/bin/bash
                set -e
                ${ExternalSampleScripts.buildAmperProjectEnhanced()}
            """.trimIndent()
        }
    }

    fun BuildSteps.addEnhancedCleanupStep() {
        script {
            name = "Enhanced Cleanup"
            scriptContent = """
                #!/bin/bash
                ${ExternalSampleScripts.cleanupBackups()}
            """.trimIndent()
            executionMode = BuildStep.ExecutionMode.ALWAYS
        }
    }
}

data class EAPSampleBuilder(
    val projectName: String,
    val vcsRoot: VcsRoot,
    val versionResolver: BuildType
) {
    private var buildType: ExternalSampleBuildType = ExternalSampleBuildType.GRADLE
    private var specialHandling: List<SpecialHandling> = emptyList()

    fun withBuildType(type: ExternalSampleBuildType) = apply { buildType = type }

    fun withSpecialHandling(vararg handling: SpecialHandling) = apply {
        specialHandling = handling.toList()
    }

    fun build(): ExternalSampleConfig = ExternalSampleConfig(
        projectName = projectName,
        vcsRoot = vcsRoot,
        buildType = buildType,
        versionResolver = versionResolver,
        specialHandling = specialHandling
    )
}

object EAPBuildFeatures {
    fun BuildFeatures.addEAPSlackNotifications(
        includeSuccess: Boolean = false,
        includeBuildStart: Boolean = false
    ) {
        notifications {
            notifierSettings = slackNotifier {
                connection = "PROJECT_EXT_5"
                sendTo = "#ktor-projects-on-eap"
                messageFormat = verboseMessageFormat {
                    addStatusText = true
                    addBranch = true
                    addChanges = true
                }
            }
            if (includeBuildStart) buildStarted = true
            buildFailed = true
            if (includeSuccess) buildFinishedSuccessfully = true
            buildFailedToStart = true
            firstSuccessAfterFailure = true
        }
    }
}

data class ExternalSampleConfig(
    override val projectName: String,
    override val vcsRoot: VcsRoot,
    override val buildType: ExternalSampleBuildType,
    override val versionResolver: BuildType,
    override val specialHandling: List<SpecialHandling>
) : BaseExternalEAPSample(), ExternalEAPSampleConfig {

    override fun createEAPBuildType(): BuildType = BuildType {
        id("ExternalEAP_${projectName.replace("-", "_").replace(" ", "_")}")
        name = "EAP: $projectName"
        description = "Enhanced validation of $projectName against EAP version of Ktor optimized for TeamCity Cloud hosted agents"

        vcs {
            root(vcsRoot)
        }

        requirements {
            agent(OS.Linux, Arch.X64)

            if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                contains("teamcity.agent.jvm.os.name", "Linux")
                exists("docker.server.version")
                matches("docker.server.version", ".*")
                doesNotContain("docker.server.version", "1.3")
                doesNotMatch("docker.server.version", "1\\.(3|4[0-3])")
            }

            if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                exists("android.sdk.root")
            }
        }

        params {
            defaultGradleParams()
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_COMPILER_PLUGIN_VERSION%")
            param("enhanced.validation.enabled", "true")
            param("hosted.agent.optimized", "true")
            param("toml.comprehensive.handling", if (SpecialHandlingUtils.requiresEnhancedTomlHandling(specialHandling)) "true" else "false")

            if (projectName == "full-stack-ktor-talk") {
                param("env.GOOGLE_CLIENT_ID", "placeholder-google-client-id")
                param("env.API_BASE_URL", "http://localhost:8080")
            }
        }

        steps {
            with(EAPBuildSteps) {
                hostedAgentResourceMonitoring()
                standardEAPSetup()

                if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                    setupDockerEnvironment()
                }

                if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                    setupAndroidEnvironment()
                }

                if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
                    setupDaggerEnvironment()
                }

                when (buildType) {
                    ExternalSampleBuildType.GRADLE -> {
                        buildEnhancedEAPExternalGradleSample(projectName, specialHandling)
                    }
                    ExternalSampleBuildType.AMPER -> {
                        buildEnhancedEAPExternalAmperSample()
                    }
                }

                addEnhancedCleanupStep()
            }
        }

        addCommonExternalEAPConfiguration(projectName, specialHandling)

        dependencies {
            dependency(versionResolver) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }
}

object ExternalSamplesEAPValidation : Project({
    id("ExternalSamplesEAPValidation")
    name = "External Samples EAP Validation"
    description = "Enhanced validation of external GitHub samples against EAP versions of Ktor optimized for TeamCity Cloud hosted agents"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
        param("hosted.agent.optimized", "true")
        param("teamcity.cloud.hosted", "true")
    }

    val versionResolver = createVersionResolver()
    buildType(versionResolver)

    val samples = createSampleConfigurations(versionResolver)
    val allBuildTypes = samples.map { it.createEAPBuildType() }
    allBuildTypes.forEach { buildType(it) }

    buildType(createMasterCompositeBuild(versionResolver, allBuildTypes))
})

private fun Project.registerVCSRoots() {
    vcsRoot(VCSKtorArrowExample)
    vcsRoot(VCSKtorAiServer)
    vcsRoot(VCSKtorNativeServer)
    vcsRoot(VCSKtorKoogExample)
    vcsRoot(VCSFullStackKtorTalk)
    vcsRoot(VCSKtorConfigExample)
    vcsRoot(VCSKtorWorkshop2025)
    vcsRoot(VCSAmperKtorSample)
    vcsRoot(VCSKtorDIOverview)
    vcsRoot(VCSKtorFullStackRealWorld)
}

private fun createVersionResolver(): BuildType =
    EAPVersionResolver.createVersionResolver(
        id = "ExternalKtorEAPVersionResolver",
        name = "EAP Version Resolver for External Samples",
        description = "Enhanced version resolver optimized for hosted agents with comprehensive validation"
    )

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalSampleConfig> {
    return listOf(
        EAPSampleBuilder("ktor-arrow-example", VCSKtorArrowExample, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),
        EAPSampleBuilder("ktor-ai-server", VCSKtorAiServer, versionResolver)
            .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS, SpecialHandling.ENHANCED_TOML_PATTERNS, SpecialHandling.HIGH_MEMORY_REQUIRED)
            .build(),
        EAPSampleBuilder("ktor-native-server", VCSKtorNativeServer, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),
        EAPSampleBuilder("ktor-koog-example", VCSKtorKoogExample, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.DOCKER_TESTCONTAINERS, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),
        EAPSampleBuilder("full-stack-ktor-talk", VCSFullStackKtorTalk, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),
        EAPSampleBuilder("ktor-config-example", VCSKtorConfigExample, versionResolver)
            .withSpecialHandling(SpecialHandling.HIGH_MEMORY_REQUIRED, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),
        EAPSampleBuilder("ktor-workshop-2025", VCSKtorWorkshop2025, versionResolver)
            .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),
        EAPSampleBuilder("amper-ktor-sample", VCSAmperKtorSample, versionResolver)
            .withBuildType(ExternalSampleBuildType.AMPER)
            .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),
        EAPSampleBuilder("ktor-di-overview", VCSKtorDIOverview, versionResolver)
            .withSpecialHandling(SpecialHandling.DAGGER_ANNOTATION_PROCESSING, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),
        EAPSampleBuilder("ktor-full-stack-real-world", VCSKtorFullStackRealWorld, versionResolver)
            .withSpecialHandling(
                SpecialHandling.KOTLIN_MULTIPLATFORM,
                SpecialHandling.DOCKER_TESTCONTAINERS,
                SpecialHandling.ANDROID_SDK_REQUIRED,
                SpecialHandling.HIGH_MEMORY_REQUIRED,
                SpecialHandling.ENHANCED_TOML_PATTERNS
            )
            .build()
    )
}

private fun createMasterCompositeBuild(
    versionResolver: BuildType,
    allBuildTypes: List<BuildType>
): BuildType = BuildType {
    id("ExternalSamplesMasterBuild")
    name = "EAP External Samples Master Build"
    description = "Enhanced master composite build for all external samples optimized for TeamCity Cloud hosted agents"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("env.GIT_BRANCH", "%teamcity.build.branch%")
        param("teamcity.build.skipDependencyBuilds", "true")
        param("enhanced.validation.enabled", "true")
        param("hosted.agent.optimized", "true")
        param("teamcity.cloud.hosted", "true")
    }

    dependencies {
        dependency(versionResolver) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }

        allBuildTypes.forEach { buildType ->
            dependency(buildType) {
                snapshot {
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }

    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "No agents available to run"
            failureMessage = "No hosted agents available for external samples composite"
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Build queue timeout"
            failureMessage = "External samples master build timed out waiting for hosted agents"
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "✗.*FAILED:"
            failureMessage = "Enhanced validation failed in one or more samples on hosted agents"
            stopBuildOnFailure = false
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Java heap space"
            failureMessage = "Memory exhaustion detected on hosted agent"
            stopBuildOnFailure = true
        }

        executionTimeoutMin = 60
    }

    features {
        with(EAPBuildFeatures) {
            addEAPSlackNotifications(includeSuccess = true, includeBuildStart = true)
        }
    }

    triggers {
        finishBuildTrigger {
            buildType = EapConstants.PUBLISH_EAP_BUILD_TYPE_ID
            successfulOnly = true
            branchFilter = "+:refs/heads/*"
        }
    }
}
