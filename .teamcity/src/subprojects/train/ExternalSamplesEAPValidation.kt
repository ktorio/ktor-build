
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
import subprojects.train.EAPBuildFeatures.addEAPSlackNotifications
import subprojects.train.EAPBuildSteps.addCleanupStep
import subprojects.train.EAPBuildSteps.buildEAPExternalAmperSample
import subprojects.train.EAPBuildSteps.buildEAPExternalGradleSample
import subprojects.train.EAPBuildSteps.debugExternalEnvironmentVariables
import subprojects.train.EAPBuildSteps.resourceMonitoring
import subprojects.train.EAPBuildSteps.standardEAPSetup

enum class SpecialHandling {
    KOTLIN_MULTIPLATFORM,
    AMPER_GRADLE_HYBRID,
    DOCKER_TESTCONTAINERS,
    DAGGER_ANNOTATION_PROCESSING,
    ANDROID_SDK_REQUIRED
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
}

abstract class BaseExternalEAPSample : EAPSampleConfig {
    protected fun BuildType.addCommonExternalEAPConfiguration(sampleName: String) {
        id("KtorEAPExternalSample_${sampleName.replace('-', '_')}")
        name = "EAP Validate $sampleName (External)"

        requirements {
            agent(OS.Linux, Arch.X64)
        }

        params {
            defaultGradleParams()
        }

        addExternalEAPSampleFailureConditions(sampleName)
        defaultBuildFeatures()
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

fun BuildType.addExternalEAPSampleFailureConditions(sampleName: String) {
    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "BUILD FAILED"
            failureMessage = "Build failed for $sampleName external sample"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "java.lang.OutOfMemoryError"
            failureMessage = "Fatal out of memory error in $sampleName"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Gradle build daemon disappeared unexpectedly"
            failureMessage = "Gradle daemon crashed for $sampleName"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Plugin \\[id: 'io.ktor.plugin'.*was not found"
            failureMessage = "Ktor plugin not found in $sampleName - repository configuration issue"
            stopBuildOnFailure = true
        }
        executionTimeoutMin = 30
    }
}

object ExternalSampleScripts {

    fun monitorResources() = """
        monitor_resources() {
            echo "=== Resource Monitoring ==="
            echo "Available memory:"
            free -h || echo "free command not available"
            echo "Available disk space:"
            df -h . || echo "df command not available"
            echo "System load:"
            uptime || echo "uptime command not available"
            echo "Java processes:"
            ps aux | grep -i java | head -5 || echo "No Java processes found"
            echo "============================"
        }
        monitor_resources
    """.trimIndent()

    fun analyzeProjectStructure() = """
        analyze_project_structure() {
            echo "=== Project Structure Analysis ==="
            echo "Project root contents:"
            ls -la . || echo "Cannot list project root"
            echo "Looking for build files:"
            find . -name "build.gradle*" -o -name "pom.xml" -o -name "module.yaml" | head -10
            echo "Checking for submodules/multi-project:"
            find . -name "settings.gradle*" | head -5
            echo "==================================="
        }
        analyze_project_structure
    """.trimIndent()

    fun backupConfigFiles() = """
        backup_config_files() {
            echo "=== Backing up Configuration Files ==="
            for file in gradle.properties settings.gradle.kts build.gradle.kts pom.xml module.yaml; do
                if [ -f "${'$'}file" ]; then
                    cp "${'$'}file" "${'$'}file.backup"
                    echo "✓ Backed up ${'$'}file"
                fi
            done
            echo "======================================="
        }
        backup_config_files
    """.trimIndent()

    fun updateVersionCatalogComprehensive() = """
        update_version_catalog_comprehensive() {
            echo "=== Comprehensive Version Catalog Update ==="
            if [ -f "gradle/libs.versions.toml" ]; then
                echo "Found libs.versions.toml, updating versions..."
                cp "gradle/libs.versions.toml" "gradle/libs.versions.toml.backup"
                
                sed -i 's/ktor = "[^"]*"/ktor = "%env.KTOR_VERSION%"/' gradle/libs.versions.toml
                sed -i 's/ktor-version = "[^"]*"/ktor-version = "%env.KTOR_VERSION%"/' gradle/libs.versions.toml
                sed -i 's/ktorVersion = "[^"]*"/ktorVersion = "%env.KTOR_VERSION%"/' gradle/libs.versions.toml
                
                echo "Updated version catalog:"
                cat gradle/libs.versions.toml
            else
                echo "No version catalog found, skipping..."
            fi
            echo "============================================="
        }
        update_version_catalog_comprehensive
    """.trimIndent()

    fun updateGradleProperties() = """
        update_gradle_properties() {
            echo "=== Gradle Properties Update ==="
            
            WORKERS=4

            if [ -f "gradle.properties" ]; then
                echo "Updating existing gradle.properties..."
                grep -v "^ktor.*Version[[:space:]]*=" gradle.properties > gradle.properties.tmp || touch gradle.properties.tmp
                grep -v "^ktor\\..*\\.version[[:space:]]*=" gradle.properties.tmp > gradle.properties.tmp2 || cp gradle.properties.tmp gradle.properties.tmp2
                grep -v "^org\\.gradle\\.jvmargs[[:space:]]*=" gradle.properties.tmp2 > gradle.properties.tmp3 || cp gradle.properties.tmp2 gradle.properties.tmp3
                grep -v "^org\\.gradle\\.daemon[[:space:]]*=" gradle.properties.tmp3 > gradle.properties.tmp4 || cp gradle.properties.tmp3 gradle.properties.tmp4
                grep -v "^org\\.gradle\\.parallel[[:space:]]*=" gradle.properties.tmp4 > gradle.properties.tmp5 || cp gradle.properties.tmp4 gradle.properties.tmp5
                grep -v "^org\\.gradle\\.caching[[:space:]]*=" gradle.properties.tmp5 > gradle.properties.tmp6 || cp gradle.properties.tmp5 gradle.properties.tmp6
                grep -v "^org\\.gradle\\.workers\\.max[[:space:]]*=" gradle.properties.tmp6 > gradle.properties.tmp7 || cp gradle.properties.tmp6 gradle.properties.tmp7
                grep -v "^kotlin\\.incremental[[:space:]]*=" gradle.properties.tmp7 > gradle.properties.tmp8 || cp gradle.properties.tmp7 gradle.properties.tmp8
                
                cat >> gradle.properties.tmp8 << EOF
ktorVersion=%env.KTOR_VERSION%
ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%
org.gradle.daemon=false
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=false
org.gradle.workers.max=${'$'}WORKERS
kotlin.incremental=true
kotlin.compiler.execution.strategy=daemon
EOF
                
                mv gradle.properties.tmp8 gradle.properties
                rm -f gradle.properties.tmp*
            else
                echo "Creating new gradle.properties..."
                cat > gradle.properties << EOF
ktorVersion=%env.KTOR_VERSION%
ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%
org.gradle.daemon=false
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=false
org.gradle.workers.max=${'$'}WORKERS
kotlin.incremental=true
kotlin.compiler.execution.strategy=daemon
EOF
            fi
            
            echo "Final gradle.properties content:"
            cat gradle.properties
            echo "✓ Gradle properties update completed"
        }
        update_gradle_properties
    """.trimIndent()

    fun setupGradleRepositories() = """
        setup_gradle_repositories() {
            echo "=== Gradle Repositories Setup ==="
            
            cat > gradle-eap-init.gradle << EOF
allprojects {
    repositories {
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        mavenCentral()
        gradlePluginPortal()
    }
}

gradle.allprojects { project ->
    project.buildscript {
        repositories {
            maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
            maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

gradle.settingsEvaluated { settings ->
    settings.pluginManagement {
        repositories {
            maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
            maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
            gradlePluginPortal()
            mavenCentral()
        }
    }
}
EOF
            
            echo "✓ Init script created"
        }
        setup_gradle_repositories
    """.trimIndent()

    fun setupDockerEnvironment() = """
        setup_docker_environment() {
            echo "=== Docker Environment Setup ==="
            echo "Checking Docker availability..."
            if command -v docker >/dev/null 2>&1; then
                echo "✓ Docker is available"
                docker version || echo "Docker version check failed"
            else
                echo "⚠ Docker not available - skipping Docker-related tests"
            fi
            echo "================================"
        }
        setup_docker_environment
    """.trimIndent()

    fun setupDaggerEnvironment() = """
        setup_dagger_environment() {
            echo "=== Dagger Environment Setup ==="
            echo "Configuring annotation processing for Dagger..."
            if [ -f "build.gradle.kts" ]; then
                echo "Found build.gradle.kts - Dagger should be configured in the project"
            else
                echo "⚠ No build.gradle.kts found for Dagger configuration"
            fi
            echo "================================="
        }
        setup_dagger_environment
    """.trimIndent()

    fun setupAndroidSDK() = """
        setup_android_sdk() {
            echo "=== Android SDK Setup ==="
            echo "Checking for Android SDK..."
            if [ -n "${'$'}ANDROID_SDK_ROOT" ] || [ -n "${'$'}ANDROID_HOME" ]; then
                echo "✓ Android SDK environment variables found"
                echo "ANDROID_SDK_ROOT: ${'$'}ANDROID_SDK_ROOT"
                echo "ANDROID_HOME: ${'$'}ANDROID_HOME"
            else
                echo "⚠ Android SDK not configured - may affect Android-related builds"
            fi
            echo "=========================="
        }
        setup_android_sdk
    """.trimIndent()

    fun buildGradleProjectWithInitScript() = """
        build_gradle_project_with_init_script() {
            echo "=== Building Gradle Project with EAP Init Script ==="
            echo "Using Ktor version: %env.KTOR_VERSION%"
            echo "Using compiler plugin version: %env.KTOR_COMPILER_PLUGIN_VERSION%"
            
            if [ -f "./gradlew" ]; then
                chmod +x ./gradlew
                GRADLE_CMD="./gradlew"
            else
                GRADLE_CMD="gradle"
            fi

            echo "Building with: ${'$'}GRADLE_CMD"
            echo "Init script location: gradle-eap-init.gradle"
            
            if [ -f "gradle-eap-init.gradle" ]; then
                echo "✓ Init script exists"
                echo "Init script content:"
                cat gradle-eap-init.gradle
            else
                echo "ERROR: Init script not found!"
                exit 1
            fi
            
            echo "Starting build with init script..."
            ${'$'}GRADLE_CMD clean build \
                --init-script gradle-eap-init.gradle \
                --no-daemon \
                --stacktrace \
                --refresh-dependencies \
                --info || {
                echo "Build failed, checking for common issues..."
                if [ -f "build.gradle.kts" ]; then
                    echo "=== build.gradle.kts content ==="
                    cat build.gradle.kts
                fi
                if [ -f "settings.gradle.kts" ]; then
                    echo "=== settings.gradle.kts content ==="
                    cat settings.gradle.kts
                fi
                exit 1
            }
            
            echo "✓ Gradle build completed successfully with EAP repositories"
        }
        build_gradle_project_with_init_script
    """.trimIndent()

    fun buildAmperProject() = """
        build_amper_project() {
            echo "=== Amper Project Build ==="
            if [ -f "module.yaml" ]; then
                echo "Found Amper project (module.yaml)"
                echo "Setting up Amper repositories..."
                
                cat > settings.yaml << EOF
repositories:
  - https://maven.pkg.jetbrains.space/public/p/ktor/eap
  - https://maven.pkg.jetbrains.space/public/p/compose/dev
  - https://repo.maven.apache.org/maven2
EOF
                
                echo "✓ Amper repositories configured"
                echo "Building with Amper (using init script)..."
                
                ${setupGradleRepositories()}
                ${buildGradleProjectWithInitScript()}
            else
                echo "⚠ No module.yaml found - not an Amper project"
            fi
            echo "=========================="
        }
        build_amper_project
    """.trimIndent()

    fun cleanupBackups() = """
        cleanup_backups() {
            echo "=== Cleanup Backup Files ==="
            find . -name "*.backup" -type f -delete 2>/dev/null || echo "No backup files to clean"
            rm -f gradle-eap-init.gradle 2>/dev/null || echo "No init script to clean"
            echo "✓ Backup cleanup completed"
            echo "============================="
        }
        cleanup_backups
    """.trimIndent()
}

object EAPBuildSteps {
    fun BuildSteps.resourceMonitoring() {
        script {
            name = "Resource Monitoring"
            scriptContent = ExternalSampleScripts.monitorResources()
        }
    }

    fun BuildSteps.standardEAPSetup() {
        script {
            name = "Standard EAP Setup"
            scriptContent = """
                ${ExternalSampleScripts.analyzeProjectStructure()}
                ${ExternalSampleScripts.backupConfigFiles()}
                ${ExternalSampleScripts.updateVersionCatalogComprehensive()}
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

    fun BuildSteps.buildEAPExternalGradleSample(projectName: String, specialHandling: List<SpecialHandling>) {
        script {
            name = "EAP Setup for $projectName"
            scriptContent = """
                ${ExternalSampleScripts.updateGradleProperties()}
                ${ExternalSampleScripts.setupGradleRepositories()}
            """.trimIndent()
        }

        if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
            setupDockerEnvironment()
        }

        if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
            setupDaggerEnvironment()
        }

        if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
            setupAndroidEnvironment()
        }

        script {
            name = "Build $projectName with EAP Init Script"
            scriptContent = ExternalSampleScripts.buildGradleProjectWithInitScript()
        }
    }

    fun BuildSteps.buildEAPExternalAmperSample() {
        script {
            name = "Amper Build with EAP Support"
            scriptContent = ExternalSampleScripts.buildAmperProject()
        }
    }

    fun BuildSteps.addCleanupStep() {
        script {
            name = "Cleanup"
            scriptContent = ExternalSampleScripts.cleanupBackups()
            executionMode = BuildStep.ExecutionMode.ALWAYS
        }
    }

    fun BuildSteps.debugExternalEnvironmentVariables() {
        script {
            name = "Debug Environment Variables (External Samples)"
            scriptContent = """
                #!/bin/bash
                echo "=== Environment Variables (External Samples) ==="
                echo "KTOR_VERSION: %env.KTOR_VERSION%"
                echo "KTOR_COMPILER_PLUGIN_VERSION: %env.KTOR_COMPILER_PLUGIN_VERSION%"
                echo "================================================="
            """.trimIndent()
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
                }
            }
            if (includeBuildStart) buildStarted = true
            buildFailed = true
            if (includeSuccess) buildFinishedSuccessfully = true
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
        addCommonExternalEAPConfiguration(projectName)

        vcs {
            root(vcsRoot)
        }

        params {
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_COMPILER_PLUGIN_VERSION%")
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
            resourceMonitoring()
            debugExternalEnvironmentVariables()
            standardEAPSetup()

            when (buildType) {
                ExternalSampleBuildType.GRADLE -> {
                    buildEAPExternalGradleSample(projectName, specialHandling)
                }
                ExternalSampleBuildType.AMPER -> {
                    buildEAPExternalAmperSample()
                }
            }

            addCleanupStep()
        }

        features {
            addEAPSlackNotifications(includeSuccess = false, includeBuildStart = false)
        }
    }
}

object ExternalSamplesEAPValidation : Project({
    id("ExternalSamplesEAPValidation")
    name = "External Samples EAP Validation"
    description = "Validation of external GitHub samples against EAP versions of Ktor"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
    }

    val versionResolver = createVersionResolver()
    buildType(versionResolver)

    val samples = createSampleConfigurations(versionResolver)
    val allBuildTypes = samples.map { it.createEAPBuildType() }
    allBuildTypes.forEach { buildType(it) }

    buildType(createAllSamplesCompositeBuild(versionResolver, allBuildTypes))
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

private fun createVersionResolver(): BuildType = EAPVersionResolver.createVersionResolver(
    id = "KtorExternalEAPVersionResolver",
    name = "Set EAP Version for External Samples",
    description = "Determines the EAP version to use for external sample validation"
)

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalSampleConfig> = listOf(
    EAPSampleBuilder(
        projectName = "ktor-arrow-example",
        vcsRoot = VCSKtorArrowExample,
        versionResolver = versionResolver
    ).build(),

    EAPSampleBuilder(
        projectName = "ktor-ai-server",
        vcsRoot = VCSKtorAiServer,
        versionResolver = versionResolver
    ).build(),

    EAPSampleBuilder(
        projectName = "ktor-native-server",
        vcsRoot = VCSKtorNativeServer,
        versionResolver = versionResolver
    )
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
        .build(),

    EAPSampleBuilder(
        projectName = "ktor-koog-example",
        vcsRoot = VCSKtorKoogExample,
        versionResolver = versionResolver
    ).build(),

    EAPSampleBuilder(
        projectName = "full-stack-ktor-talk",
        vcsRoot = VCSFullStackKtorTalk,
        versionResolver = versionResolver
    ).build(),

    EAPSampleBuilder(
        projectName = "ktor-config-example",
        vcsRoot = VCSKtorConfigExample,
        versionResolver = versionResolver
    ).build(),

    EAPSampleBuilder(
        projectName = "ktor-workshop-2025",
        vcsRoot = VCSKtorWorkshop2025,
        versionResolver = versionResolver
    ).build(),

    EAPSampleBuilder(
        projectName = "amper-ktor-sample",
        vcsRoot = VCSAmperKtorSample,
        versionResolver = versionResolver
    )
        .withBuildType(ExternalSampleBuildType.AMPER)
        .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID)
        .build(),

    EAPSampleBuilder(
        projectName = "ktor-di-overview",
        vcsRoot = VCSKtorDIOverview,
        versionResolver = versionResolver
    ).build(),

    EAPSampleBuilder(
        projectName = "ktor-full-stack-real-world",
        vcsRoot = VCSKtorFullStackRealWorld,
        versionResolver = versionResolver
    ).build()
)

private fun createAllSamplesCompositeBuild(
    versionResolver: BuildType,
    allBuildTypes: List<BuildType>
): BuildType = BuildType {
    id("KtorExternalEAPSamplesCompositeBuild")
    name = "Validate All External Samples with EAP"
    description = "Run all external samples against the EAP version of Ktor"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("env.GIT_BRANCH", "%teamcity.build.branch%")
        param("teamcity.build.skipDependencyBuilds", "true")
    }

    features {
        addEAPSlackNotifications(includeSuccess = true, includeBuildStart = false)
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

        allBuildTypes.forEach { sampleBuild ->
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
            failureMessage = "No compatible agents found for external EAP samples composite"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Build queue timeout"
            failureMessage = "External EAP samples build timed out waiting for compatible agents"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "java.lang.OutOfMemoryError"
            failureMessage = "Fatal memory exhaustion detected in external samples build"
            stopBuildOnFailure = true
        }
        executionTimeoutMin = 45
    }
}
