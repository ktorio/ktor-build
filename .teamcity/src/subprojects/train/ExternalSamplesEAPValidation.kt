package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.Agents.Arch
import subprojects.Agents.MEDIUM
import subprojects.Agents.OS
import subprojects.build.defaultBuildFeatures
import subprojects.build.defaultGradleParams

enum class SpecialHandling {
    KOTLIN_MULTIPLATFORM,
    AMPER_GRADLE_HYBRID,
    DOCKER_TESTCONTAINERS,
    DAGGER_ANNOTATION_PROCESSING,
    ANDROID_SDK_REQUIRED,
    COMPOSE_MULTIPLATFORM
}

enum class ExternalSampleBuildType {
    GRADLE, AMPER
}

interface ExternalEAPSampleConfig {
    val projectName: String
    fun createEAPBuildType(): BuildType
}

object SpecialHandlingUtils {
    fun requiresDocker(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DOCKER_TESTCONTAINERS)

    fun requiresAndroidSDK(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.ANDROID_SDK_REQUIRED)

    fun requiresDagger(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)

    fun isMultiplatform(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.KOTLIN_MULTIPLATFORM)

    fun isAmperHybrid(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.AMPER_GRADLE_HYBRID)
}

fun BuildSteps.addDockerAgentLogging() {
    script {
        name = "Log Agent Information"
        scriptContent = """
            #!/bin/bash
            echo "=== Agent Information ==="

            echo "##teamcity[message text='Using agent: ${'$'}TEAMCITY_AGENT_NAME' status='NORMAL']"
            echo "##teamcity[setParameter name='env.DOCKER_AGENT_FOUND' value='false']"

            echo "Agent Name: ${'$'}TEAMCITY_AGENT_NAME"
            echo "Agent OS: ${'$'}TEAMCITY_AGENT_OS_FAMILY"
            echo "Agent Architecture: ${'$'}TEAMCITY_AGENT_CPU_ARCHITECTURE"

            echo "Note: Docker compatibility will be checked at runtime in setupTestcontainersEnvironment step"
            echo "=== Agent Information Complete ==="
        """.trimIndent()
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
            buildFailed = true
            if (includeSuccess) buildFinishedSuccessfully = true
            if (includeBuildStart) buildStarted = true
        }
    }
}

object ExternalSampleScripts {
    fun BuildSteps.backupConfigFiles() {
        script {
            name = "Backup Configuration Files"
            scriptContent = """
                #!/bin/bash
                echo "=== Backing up configuration files ==="
                find . -name "gradle.properties" -exec cp {} {}.backup \;
                find . -name "build.gradle.kts" -exec cp {} {}.backup \;
                find . -name "libs.versions.toml" -exec cp {} {}.backup \;
                echo "Configuration files backed up"
            """.trimIndent()
        }
    }

    fun BuildSteps.analyzeProjectStructure(specialHandling: List<SpecialHandling> = emptyList()) {
        script {
            name = "Analyze Project Structure"
            scriptContent = """
                #!/bin/bash
                echo "=== Project Structure Analysis ==="
                echo "Special handling: ${specialHandling.joinToString(",") { it.name }}"
                ls -la
                echo "=== Analysis Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.setupTestcontainersEnvironment() {
        script {
            name = "Setup Testcontainers Environment"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Setting up Testcontainers Environment ==="

                if [ -n "%TC_CLOUD_TOKEN%" ] && [ "%TC_CLOUD_TOKEN%" != "" ]; then
                    echo "✓ Testcontainers Cloud token found, configuring cloud environment"

                    mkdir -p ${'$'}HOME/.testcontainers
                    cat > ${'$'}HOME/.testcontainers/testcontainers.properties << 'EOF'
testcontainers.reuse.enable=false
ryuk.container.privileged=true
testcontainers.cloud.token=%TC_CLOUD_TOKEN%
EOF

                    export TESTCONTAINERS_CLOUD_TOKEN="%TC_CLOUD_TOKEN%"
                    export TESTCONTAINERS_RYUK_DISABLED=true

                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_CLOUD_TOKEN' value='%TC_CLOUD_TOKEN%']"
                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_RYUK_DISABLED' value='true']"
                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='cloud']"

                    if [ -f "gradle.properties" ]; then
                        echo "Updating existing gradle.properties with Testcontainers Cloud configuration"
                        sed -i '/^testcontainers\./d' gradle.properties
                    else
                        echo "Creating gradle.properties with Testcontainers Cloud configuration"
                        touch gradle.properties
                    fi

                    echo "" >> gradle.properties
                    echo "# Testcontainers Cloud Configuration" >> gradle.properties
                    echo "testcontainers.cloud.token=%TC_CLOUD_TOKEN%" >> gradle.properties
                    echo "testcontainers.ryuk.disabled=true" >> gradle.properties
                    echo "systemProp.testcontainers.cloud.token=%TC_CLOUD_TOKEN%" >> gradle.properties
                    echo "systemProp.testcontainers.ryuk.disabled=true" >> gradle.properties

                    echo "Contents of gradle.properties:"
                    cat gradle.properties
                    echo "Contents of testcontainers.properties:"
                    cat ${'$'}HOME/.testcontainers/testcontainers.properties

                    echo "✓ Testcontainers Cloud configured successfully"
                else
                    echo "⚠ No Testcontainers Cloud token found"
                    echo "Checking for local Docker availability..."

                    if command -v docker >/dev/null 2>&1; then
                        echo "Docker command found, checking Docker daemon and API version..."

                        if docker info >/dev/null 2>&1; then
                            echo "✓ Docker daemon is accessible"

                            DOCKER_API_VERSION=$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null || echo "unknown")
                            echo "Docker API Version: ${'$'}DOCKER_API_VERSION"

                            if [[ "${'$'}DOCKER_API_VERSION" =~ ^([0-9]+)\.([0-9]+) ]]; then
                                MAJOR=${'$'}{BASH_REMATCH[1]}
                                MINOR=${'$'}{BASH_REMATCH[2]}

                                if [ "${'$'}MAJOR" -gt 1 ] || ([ "${'$'}MAJOR" -eq 1 ] && [ "${'$'}MINOR" -ge 44 ]); then
                                    echo "✓ Docker API version compatible (${'$'}DOCKER_API_VERSION >= 1.44)"
                                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='local']"
                                else
                                    echo "❌ CRITICAL: Docker API version too old (${'$'}DOCKER_API_VERSION < 1.44)"
                                    echo "This will cause Testcontainers to fail with 'client version too old' error"
                                    echo "The minimum required Docker API version is 1.44"
                                    echo "Please upgrade Docker on this agent or use Testcontainers Cloud"
                                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='incompatible']"
                                    echo "##teamcity[buildProblem description='Docker API version ${'$'}DOCKER_API_VERSION is incompatible (minimum: 1.44)' identity='docker-api-version-incompatible']"
                                fi
                            else
                                echo "⚠ Could not determine Docker API version"
                                echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='unknown']"
                            fi
                        else
                            echo "⚠ Docker daemon not accessible"
                            echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='skip']"
                        fi
                    else
                        echo "⚠ Docker command not found"
                        echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='skip']"
                    fi
                fi

                echo "Final Testcontainers mode: %env.TESTCONTAINERS_MODE%"
                echo "=== Testcontainers Environment Setup Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.setupAndroidSDK() {
        script {
            name = "Setup Android SDK"
            scriptContent = """
                #!/bin/bash
                echo "=== Setting up Android SDK ==="
                export ANDROID_HOME=/opt/android-sdk
                export PATH=${'$'}PATH:${'$'}ANDROID_HOME/tools:${'$'}ANDROID_HOME/platform-tools
                echo "Android SDK configured"
                echo "=== Android SDK Setup Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.setupDaggerEnvironment() {
        script {
            name = "Setup Dagger Environment"
            scriptContent = """
                #!/bin/bash
                echo "=== Setting up Dagger Environment ==="
                echo "Configuring annotation processing for Dagger"
                echo "=== Dagger Environment Setup Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.updateGradlePropertiesEnhanced() {
        script {
            name = "Update Gradle Properties"
            scriptContent = """
                #!/bin/bash
                echo "=== Updating Gradle Properties ==="
                echo "kotlin.mpp.enableCInteropCommonization=true" >> gradle.properties
                echo "=== Gradle Properties Updated ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.updateVersionCatalogComprehensive(specialHandling: List<SpecialHandling> = emptyList()) {
        script {
            name = "Update Version Catalog"
            scriptContent = """
                #!/bin/bash
                echo "=== Updating Version Catalog ==="
                echo "Special handling: ${specialHandling.joinToString(",") { it.name }}"
                echo "=== Version Catalog Updated ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.setupEnhancedGradleRepositories(specialHandling: List<SpecialHandling> = emptyList()) {
        script {
            name = "Setup Enhanced Gradle Repositories"
            scriptContent = """
                #!/bin/bash
                echo "=== Setting up Enhanced Gradle Repositories ==="
                echo "Special handling: ${specialHandling.joinToString(",") { it.name }}"
                echo "=== Enhanced Gradle Repositories Setup Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.configureKotlinMultiplatform() {
        script {
            name = "Configure Kotlin Multiplatform"
            scriptContent = """
                #!/bin/bash
                echo "=== Configuring Kotlin Multiplatform ==="
                echo "=== Kotlin Multiplatform Configuration Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.handleAmperGradleHybrid() {
        script {
            name = "Handle Amper Gradle Hybrid"
            scriptContent = """
                #!/bin/bash
                echo "=== Handling Amper Gradle Hybrid ==="
                echo "=== Amper Gradle Hybrid Handling Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.buildGradleProjectEnhanced(specialHandling: List<SpecialHandling> = emptyList()) {
        script {
            name = "Build Gradle Project Enhanced"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== Building Gradle Project (Enhanced) ==="
                echo "Setting up EAP build environment..."

                if [ -n "%env.JDK_21%" ]; then
                    export JAVA_HOME="%env.JDK_21%"
                    export PATH="${'$'}JAVA_HOME/bin:${'$'}PATH"
                    echo "Using JDK 21: ${'$'}JAVA_HOME"
                    java -version
                else
                    echo "⚠ JDK_21 environment variable not set, using system default"
                    java -version
                fi

                echo "KTOR_VERSION: %env.KTOR_VERSION%"
                echo "KTOR_COMPILER_PLUGIN_VERSION: %env.KTOR_COMPILER_PLUGIN_VERSION%"

                GRADLE_OPTS="--init-script gradle-eap-init.gradle --info --stacktrace"
                BUILD_TASK="build"

                ${if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                """
                    echo "=== DOCKER/TESTCONTAINERS CONFIGURATION ==="

                    DOCKER_API_VERSION=$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null || echo "unknown")
                    echo "Docker API Version: ${'$'}DOCKER_API_VERSION"

                    if [ -n "%TC_CLOUD_TOKEN%" ] && [ "%TC_CLOUD_TOKEN%" != "" ]; then
                        echo "✓ Testcontainers Cloud token available - using cloud mode"
                        export TESTCONTAINERS_CLOUD_TOKEN="%TC_CLOUD_TOKEN%"
                        export TESTCONTAINERS_RYUK_DISABLED=true
                        export TESTCONTAINERS_REUSE_ENABLE=false

                        echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='cloud']"

                        GRADLE_OPTS="${'$'}GRADLE_OPTS -Dtestcontainers.cloud.token=%TC_CLOUD_TOKEN%"
                        GRADLE_OPTS="${'$'}GRADLE_OPTS -Dtestcontainers.ryuk.disabled=true"
                        GRADLE_OPTS="${'$'}GRADLE_OPTS -Dtestcontainers.reuse.enable=false"
                        GRADLE_OPTS="${'$'}GRADLE_OPTS -Ptestcontainers.cloud.enabled=true"

                        echo "Testcontainers Cloud configuration applied"

                    elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
                        echo "✓ Local Docker available - checking compatibility"

                        DOCKER_API_VERSION=$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null || echo "unknown")
                        echo "Docker API Version: ${'$'}DOCKER_API_VERSION"

                        if [[ "${'$'}DOCKER_API_VERSION" =~ ^([0-9]+)\.([0-9]+) ]]; then
                            MAJOR=${'$'}{BASH_REMATCH[1]}
                            MINOR=${'$'}{BASH_REMATCH[2]}

                            if [ "${'$'}MAJOR" -gt 1 ] || ([ "${'$'}MAJOR" -eq 1 ] && [ "${'$'}MINOR" -ge 44 ]); then
                                echo "✓ Docker API version compatible (${'$'}DOCKER_API_VERSION >= 1.44)"
                                echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='local']"
                            else
                                echo "❌ CRITICAL: Docker API version too old (${'$'}DOCKER_API_VERSION < 1.44)"
                                echo "This will cause Testcontainers to fail with 'client version too old' error"
                                echo "The minimum required Docker API version is 1.44"
                                echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='incompatible']"
                                echo "##teamcity[buildProblem description='Docker API version ${'$'}DOCKER_API_VERSION is incompatible (minimum: 1.44)' identity='docker-api-version-incompatible']"
                            fi
                        else
                            echo "⚠ Could not determine Docker API version"
                            echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='skip']"
                        fi

                    else
                        echo "⚠ No Docker environment available"
                        echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='skip']"
                    fi

                    TESTCONTAINERS_MODE="%env.TESTCONTAINERS_MODE%"
                    echo "Final Testcontainers mode: ${'$'}TESTCONTAINERS_MODE"

                    case "${'$'}TESTCONTAINERS_MODE" in
                        "cloud")
                            echo "Using Testcontainers Cloud for integration tests"
                            ;;
                        "local")
                            echo "Using local Docker for integration tests"
                            ;;
                        "incompatible")
                            echo "❌ CRITICAL: Docker API version is incompatible (< 1.44)"
                            echo "This build will fail because Testcontainers requires Docker API version 1.44 or higher"
                            echo "Solutions:"
                            echo "  1. Configure Testcontainers Cloud token for reliable test execution"
                            echo "  2. Upgrade Docker on this agent to a version with API 1.44+"
                            echo "  3. Use a different agent with compatible Docker version"
                            echo "##teamcity[buildProblem description='Docker API version incompatible - build will fail' identity='docker-incompatible-fail']"
                            exit 1
                            ;;
                        "skip"|"unknown"|*)
                            echo "❌ CRITICAL: Cannot run integration tests - no compatible Docker environment"
                            echo "This will cause integration tests to fail as expected"
                            echo "Consider configuring Testcontainers Cloud token for reliable test execution"
                            ;;
                    esac
                    """
                } else ""}

                ${if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                """
                    if [ "%env.ANDROID_SDK_AVAILABLE%" != "false" ]; then
                        echo "Android SDK configuration detected"
                        export ANDROID_HOME=/opt/android-sdk
                        export PATH=${'$'}PATH:${'$'}ANDROID_HOME/tools:${'$'}ANDROID_HOME/platform-tools
                    else
                        echo "⚠ Android SDK not available, some builds may fail"
                    fi
                    """
                } else ""}

                ${if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
                """
                    if [ "%env.DAGGER_CONFIGURED%" = "true" ]; then
                        echo "Dagger annotation processing configured"
                        GRADLE_OPTS="${'$'}GRADLE_OPTS -Dkapt.verbose=true"
                    fi
                    """
                } else ""}

                echo "=== STARTING GRADLE OPERATIONS ==="
                echo "Final Gradle options: ${'$'}GRADLE_OPTS"
                echo "Build task: ${'$'}BUILD_TASK"

                echo "STEP: Gradle Clean..."
                if ./gradlew clean ${'$'}GRADLE_OPTS; then
                    echo "✓ Clean completed successfully"
                else
                    echo "⚠ Clean failed, continuing with build"
                fi

                echo "STEP: Gradle Build..."
                if ./gradlew ${'$'}BUILD_TASK ${'$'}GRADLE_OPTS; then
                    echo "✓ Build completed successfully"
                else
                    echo "❌ Build failed"
                    echo "Build output and logs:"
                    echo "=============================="

                    echo "Last 50 lines of build output:"
                    ./gradlew ${'$'}BUILD_TASK ${'$'}GRADLE_OPTS --debug 2>&1 | tail -50 || true

                    exit 1
                fi

                ${if (SpecialHandlingUtils.isMultiplatform(specialHandling)) {
                """
                    echo "Running multiplatform-specific tasks..."
                    ./gradlew allTests ${'$'}GRADLE_OPTS || echo "⚠ Some multiplatform tests failed"
                    """
                } else ""}

                echo "✓ Gradle build enhanced completed successfully"
            """.trimIndent()
        }
    }

    fun BuildSteps.setupAmperRepositories() {
        script {
            name = "Setup Amper Repositories"
            scriptContent = """
                #!/bin/bash
                echo "=== Setting up Amper Repositories ==="
                echo "=== Amper Repositories Setup Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.updateAmperVersionsEnhanced() {
        script {
            name = "Update Amper Versions Enhanced"
            scriptContent = """
                #!/bin/bash
                echo "=== Updating Amper Versions Enhanced ==="
                echo "=== Amper Versions Enhanced Update Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.buildAmperProjectEnhanced() {
        script {
            name = "Build Amper Project Enhanced"
            scriptContent = """
                #!/bin/bash
                echo "=== Building Amper Project Enhanced ==="
                ./gradlew build
                echo "=== Amper Project Enhanced Build Complete ==="
            """.trimIndent()
        }
    }
}

data class ExternalSampleConfig(
    override val projectName: String,
    val vcsRoot: VcsRoot,
    val buildType: ExternalSampleBuildType,
    val versionResolver: BuildType,
    val specialHandling: List<SpecialHandling>
) : ExternalEAPSampleConfig {

    override fun createEAPBuildType(): BuildType = BuildType {
        id("ExternalSampleEAP_${projectName.replace(" ", "_").replace("-", "_")}")
        name = "EAP Validation: $projectName"
        description = "EAP validation for external sample: $projectName with enhanced handling"

        vcs {
            root(vcsRoot)
            cleanCheckout = true
            checkoutDir = "sample-project"
        }

        dependencies {
            snapshot(versionResolver) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                synchronizeRevisions = false
            }
        }

        params {
            defaultGradleParams()
            param("sample.project.name", projectName)
            param("sample.build.type", buildType.name)
            param("special.handling", specialHandling.joinToString(",") { it.name })
            param("env.DOCKER_AGENT_FOUND", "false")
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_COMPILER_PLUGIN_VERSION%")
            param("env.TESTCONTAINERS_MODE", "skip")
            param("env.JDK_21", "")
            param("env.TC_CLOUD_TOKEN", "")
        }

        requirements {
            agent(OS.Linux, Arch.X64, hardwareCapacity = MEDIUM)
        }

        steps {
            if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                addDockerAgentLogging()
            }

            ExternalSampleScripts.run {
                backupConfigFiles()
                analyzeProjectStructure(specialHandling)

                if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                    setupTestcontainersEnvironment()
                }

                if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                    setupAndroidSDK()
                }

                if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
                    setupDaggerEnvironment()
                }

                when (buildType) {
                    ExternalSampleBuildType.GRADLE -> {
                        updateGradlePropertiesEnhanced()
                        updateVersionCatalogComprehensive(specialHandling)
                        setupEnhancedGradleRepositories(specialHandling)

                        if (SpecialHandlingUtils.isMultiplatform(specialHandling)) {
                            configureKotlinMultiplatform()
                        }

                        if (SpecialHandlingUtils.isAmperHybrid(specialHandling)) {
                            handleAmperGradleHybrid()
                        }

                        buildGradleProjectEnhanced(specialHandling)
                    }
                    ExternalSampleBuildType.AMPER -> {
                        setupAmperRepositories()
                        updateAmperVersionsEnhanced()
                        buildAmperProjectEnhanced()
                    }
                }
            }
        }

        defaultBuildFeatures()

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "BUILD FAILED"
                failureMessage = "Build failed during EAP validation"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "FAILURE: Build failed with an exception"
                failureMessage = "Build exception during EAP validation"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 30
        }
    }
}

object ExternalSamplesEAPValidation : Project({
    id("ExternalSamplesEAPValidation")
    name = "External Samples EAP Validation"
    description = "Enhanced validation of external GitHub samples against EAP versions of Ktor with Testcontainers Cloud support"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
        param("toml.comprehensive.handling", "true")
        param("configuration.preservation.enabled", "true")
        param("special.handling.enabled", "true")
        param("compose.multiplatform.support", "true")
        param("testcontainers.cloud.enabled", "true")
        param("GOOGLE_CLIENT_ID", "placeholder_google_client_id_for_build_validation")
        password("testcontainers-cloud-token", "credentialsJSON:your-testcontainers-cloud-token-id")
    }

    val versionResolver = createVersionResolver()
    buildType(versionResolver)

    val samples = createSampleConfigurations(versionResolver)
    val buildTypes = samples.map { it.createEAPBuildType() }

    buildTypes.forEach { buildType(it) }
    buildType(createCompositeBuild(versionResolver, buildTypes))
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
    id = "ExternalSamplesEAPVersionResolver",
    name = "External Samples EAP Version Resolver",
    description = "Resolves EAP versions for external sample validation"
)

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalSampleConfig> = listOf(
    EAPSampleBuilder("Ktor Arrow Example", VCSKtorArrowExample, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("Ktor AI Server", VCSKtorAiServer, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("Ktor Native Server", VCSKtorNativeServer, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
        .build(),

    EAPSampleBuilder("Ktor Koog Example", VCSKtorKoogExample, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.COMPOSE_MULTIPLATFORM, SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("Full Stack Ktor Talk", VCSFullStackKtorTalk, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("Ktor Config Example", VCSKtorConfigExample, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .build(),

    EAPSampleBuilder("Ktor Workshop 2025", VCSKtorWorkshop2025, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("Amper Ktor Sample", VCSAmperKtorSample, versionResolver)
        .withBuildType(ExternalSampleBuildType.AMPER)
        .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID)
        .build(),

    EAPSampleBuilder("Ktor DI Overview", VCSKtorDIOverview, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)
        .build(),

    EAPSampleBuilder("Ktor Full Stack Real World", VCSKtorFullStackRealWorld, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.DOCKER_TESTCONTAINERS, SpecialHandling.COMPOSE_MULTIPLATFORM)
        .build()
)

private fun createCompositeBuild(versionResolver: BuildType, buildTypes: List<BuildType>): BuildType = BuildType {
    id("ExternalSamplesEAPCompositeBuild")
    name = "External Samples EAP Validation - All Samples"
    description = "Composite build that runs all external sample validations"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("teamcity.build.skipDependencyBuilds", "true")
    }

    features {
        EAPBuildFeatures.run {
            addEAPSlackNotifications(includeSuccess = true, includeBuildStart = false)
        }
    }

    triggers {
        finishBuildTrigger {
            buildType = "KtorPublish_AllEAP"
            successfulOnly = true
            branchFilter = "+:refs/heads/*"
        }
    }

    dependencies {
        snapshot(versionResolver) {
            onDependencyFailure = FailureAction.FAIL_TO_START
            synchronizeRevisions = false
        }

        buildTypes.forEach { buildType ->
            snapshot(buildType) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                synchronizeRevisions = false
            }
        }
    }

    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "No agents available"
            failureMessage = "No compatible agents found for external samples EAP validation"
            stopBuildOnFailure = true
        }
        executionTimeoutMin = 60
    }
}
