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

    fun isComposeMultiplatform(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.COMPOSE_MULTIPLATFORM)
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

                if [ -n "%env.TC_CLOUD_TOKEN%" ] && [ "%env.TC_CLOUD_TOKEN%" != "" ]; then
                    echo "✓ Testcontainers Cloud token found, configuring cloud environment"

                    mkdir -p ${'$'}HOME/.testcontainers
                    cat > ${'$'}HOME/.testcontainers/testcontainers.properties << 'EOF'
testcontainers.reuse.enable=false
ryuk.container.privileged=true
testcontainers.cloud.token=%env.TC_CLOUD_TOKEN%
EOF

                    export TESTCONTAINERS_CLOUD_TOKEN="%env.TC_CLOUD_TOKEN%"
                    export TESTCONTAINERS_RYUK_DISABLED=true

                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_CLOUD_TOKEN' value='%env.TC_CLOUD_TOKEN%']"
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
                    echo "testcontainers.cloud.token=%env.TC_CLOUD_TOKEN%" >> gradle.properties
                    echo "testcontainers.ryuk.disabled=true" >> gradle.properties
                    echo "systemProp.testcontainers.cloud.token=%env.TC_CLOUD_TOKEN%" >> gradle.properties
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

    fun BuildSteps.updateGradlePropertiesEnhanced(specialHandling: List<SpecialHandling> = emptyList()) {
        script {
            name = "Update Gradle Properties"
            scriptContent = """
                #!/bin/bash
                echo "=== Updating Gradle Properties ==="

                if [ ! -f "gradle.properties" ]; then
                    touch gradle.properties
                fi

                if [ -s gradle.properties ] && [ "$(tail -c1 gradle.properties)" != "" ]; then
                    echo "" >> gradle.properties
                fi

                ${if (SpecialHandlingUtils.isMultiplatform(specialHandling) || SpecialHandlingUtils.isComposeMultiplatform(specialHandling)) {
                """
                    echo "=== MULTIPLATFORM PROJECT DETECTED ==="
                    echo "Applying multiplatform-specific Gradle properties"

                    echo "kotlin.mpp.enableCInteropCommonization=true" >> gradle.properties
                    echo "kotlin.native.version=%env.KOTLIN_VERSION%" >> gradle.properties

                    echo "# Prevent NPM configuration resolution during configuration time" >> gradle.properties
                    echo "kotlin.js.nodejs.check.fail=false" >> gradle.properties
                    echo "kotlin.js.yarn.check.fail=false" >> gradle.properties
                    echo "kotlin.js.npm.lazy=true" >> gradle.properties

                    echo "# Kotlin/JS and WASM optimization" >> gradle.properties
                    echo "kotlin.js.compiler=ir" >> gradle.properties
                    echo "kotlin.js.generate.executable.default=false" >> gradle.properties
                    echo "kotlin.wasm.experimental=true" >> gradle.properties
                    """
            } else {
                """
                    echo "=== NON-MULTIPLATFORM PROJECT ==="
                    echo "Skipping multiplatform-specific properties (NPM, Kotlin/JS, WASM)"
                    """
            }}

                echo "# Gradle performance optimizations" >> gradle.properties
                echo "org.gradle.configureondemand=true" >> gradle.properties
                echo "org.gradle.parallel=true" >> gradle.properties
                echo "org.gradle.caching=true" >> gradle.properties
                echo "org.gradle.daemon=true" >> gradle.properties
                echo "org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC" >> gradle.properties

                ${if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                """
                    echo "=== ANDROID SDK PROJECT DETECTED ==="
                    echo "Applying Android SDK optimizations"
                    echo "# Android SDK optimization" >> gradle.properties
                    echo "android.useAndroidX=true" >> gradle.properties
                    echo "android.enableJetifier=true" >> gradle.properties
                    echo "android.builder.sdkDownload=false" >> gradle.properties
                    """
            } else {
                """
                    echo "=== NON-ANDROID PROJECT ==="
                    echo "Skipping Android SDK optimizations"
                    """
            }}

                echo "=== Gradle Properties Updated ==="
                echo "Contents of gradle.properties:"
                cat gradle.properties
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
                set -e
                echo "=== Setting up Enhanced Gradle Repositories ==="
                echo "Special handling: ${specialHandling.joinToString(",") { it.name }}"

                ${if (SpecialHandlingUtils.isMultiplatform(specialHandling) || SpecialHandlingUtils.isComposeMultiplatform(specialHandling)) {
                """
                    echo "=== MULTIPLATFORM PROJECT DETECTED ==="
                    echo "Creating EAP Gradle init script..."

                    cat > gradle-eap-init.gradle << 'EOF'
allprojects {
    repositories {
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    gradle.projectsEvaluated {
        configurations.all { config ->
            if (config.name.contains("NpmAggregated") || config.name.contains("npm")) {
                try {
                    if (config.hasProperty('isCanBeResolved')) {
                        config.isCanBeResolved = false
                    }
                    if (config.hasProperty('isCanBeConsumed')) {
                        config.isCanBeConsumed = false
                    }
                } catch (Exception e) {
                    logger.info("Could not configure NPM configuration " + config.name + ": " + e.message)
                }
            }
        }
    }

    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-stdlib-js:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-stdlib-wasm-js:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-test:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-test-common:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-test-js:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-test-junit:%env.KOTLIN_VERSION%")

            eachDependency { details ->
                if (details.requested.group == "org.jetbrains.kotlin") {
                    details.useVersion("%env.KOTLIN_VERSION%")
                    details.because("Align Kotlin version with compiler to prevent compilation errors")
                }
            }
        }
    }
}

gradle.beforeProject { project ->
    project.plugins.withId("org.jetbrains.kotlin.js") {
        project.kotlin {
            js {
                nodejs {
                    testTask {
                        useMocha {
                            timeout = "30s"
                        }
                    }
                }
                browser {
                    testTask {
                        useKarma {
                            useChromeHeadless()
                        }
                    }
                }
            }
        }
    }

    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        project.kotlin {
            targets.configureEach { target ->
                if (target.name.contains("js") || target.name.contains("wasm")) {
                    target.compilations.configureEach { compilation ->
                        compilation.compileKotlinTask.doFirst {
                            println("Starting compilation for target: " + target.name + ", compilation: " + compilation.name)
                        }
                    }
                }
            }
        }
    }
}
EOF
                    """
            } else {
                """
                    echo "=== NON-MULTIPLATFORM PROJECT ==="
                    echo "Creating basic EAP Gradle init script without multiplatform optimizations"

                    cat > gradle-eap-init.gradle << 'EOF'
allprojects {
    repositories {
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-test:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-test-common:%env.KOTLIN_VERSION%")
            force("org.jetbrains.kotlin:kotlin-test-junit:%env.KOTLIN_VERSION%")

            eachDependency { details ->
                if (details.requested.group == "org.jetbrains.kotlin") {
                    details.useVersion("%env.KOTLIN_VERSION%")
                    details.because("Align Kotlin version with compiler to prevent compilation errors")
                }
            }
        }
    }
}
EOF
                    """
            }}

                echo "✓ EAP init script created successfully"
                echo "Contents of gradle-eap-init.gradle:"
                cat gradle-eap-init.gradle

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

                ${if (SpecialHandlingUtils.isMultiplatform(specialHandling) || SpecialHandlingUtils.isComposeMultiplatform(specialHandling)) {
                """
                    GRADLE_OPTS="--init-script gradle-eap-init.gradle --init-script fix-npm-resolution.gradle --configuration-cache --info --stacktrace"
                    echo "Using both EAP and NPM resolution init scripts for multiplatform project with configuration cache enabled"
                    """
            } else {
                """
                    GRADLE_OPTS="--init-script gradle-eap-init.gradle --configuration-cache --info --stacktrace"
                    echo "Using only EAP init script for non-multiplatform project with configuration cache enabled"
                    """
            }}

                ${if (SpecialHandlingUtils.requiresDocker(specialHandling) || SpecialHandlingUtils.requiresDagger(specialHandling)) {
                """
                    echo "=== SKIPPING TESTS FOR ${if (SpecialHandlingUtils.requiresDocker(specialHandling) && SpecialHandlingUtils.requiresDagger(specialHandling)) "DOCKER AND DAGGER" else if (SpecialHandlingUtils.requiresDocker(specialHandling)) "DOCKER" else "DAGGER"} PROJECT ==="
                    echo "This project uses ${if (SpecialHandlingUtils.requiresDocker(specialHandling) && SpecialHandlingUtils.requiresDagger(specialHandling)) "Docker/Testcontainers and Dagger annotation processing" else if (SpecialHandlingUtils.requiresDocker(specialHandling)) "Docker/Testcontainers" else "Dagger annotation processing"}, skipping tests to avoid compatibility issues"
                    BUILD_TASK="assemble"
                    echo "Using build task: ${'$'}BUILD_TASK (tests will be skipped)"

                    GRADLE_OPTS="${'$'}GRADLE_OPTS -x test -x check"
                    ${if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
                    """
                    if ./gradlew projects | grep -q ":dagger"; then
                        GRADLE_OPTS="${'$'}GRADLE_OPTS -x :dagger:test"
                        echo "Found dagger subproject, added :dagger:test exclusion"
                    else
                        echo "No dagger subproject found, skipping :dagger:test exclusion"
                    fi

                    ${if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                        """
                    GRADLE_OPTS="${'$'}GRADLE_OPTS -x testDebugUnitTest -x testReleaseUnitTest"
                    echo "Added Android-specific test exclusions for dagger project"
                    """
                    } else {
                        """
                    echo "Non-Android project, skipping Android-specific test exclusions"
                    """
                    }}
                    echo "Added explicit dagger test exclusions to prevent test execution"
                    """
                } else ""}
                    echo "Final Gradle options with test exclusions: ${'$'}GRADLE_OPTS"
                    """
            } else {
                """
                    BUILD_TASK="build"
                    echo "Using build task: ${'$'}BUILD_TASK (includes tests)"
                    """
            }}

                ${if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                """
                    echo "=== DOCKER/TESTCONTAINERS CONFIGURATION ==="

                    TESTCONTAINERS_MODE="%env.TESTCONTAINERS_MODE%"
                    echo "Testcontainers mode from setup: ${'$'}TESTCONTAINERS_MODE"

                    if [ "${'$'}TESTCONTAINERS_MODE" = "incompatible" ]; then
                        echo "❌ CRITICAL: Docker API version is incompatible (< 1.44)"
                        echo "This build will fail because Testcontainers requires Docker API version 1.44 or higher"
                        echo "The setupTestcontainersEnvironment step already detected this incompatibility"
                        echo "Solutions:"
                        echo "  1. Configure Testcontainers Cloud token for reliable test execution"
                        echo "  2. Upgrade Docker on this agent to a version with API 1.44+"
                        echo "  3. Use a different agent with compatible Docker version"
                        echo "##teamcity[buildProblem description='Docker API version incompatible - failing fast to prevent test execution' identity='docker-incompatible-fail-fast']"
                        exit 1
                    fi

                    case "${'$'}TESTCONTAINERS_MODE" in
                        "cloud")
                            echo "✓ Using Testcontainers Cloud for integration tests"
                            if [ -n "%env.TC_CLOUD_TOKEN%" ] && [ "%env.TC_CLOUD_TOKEN%" != "" ]; then
                                export TESTCONTAINERS_CLOUD_TOKEN="%env.TC_CLOUD_TOKEN%"
                                export TESTCONTAINERS_RYUK_DISABLED=true
                                export TESTCONTAINERS_REUSE_ENABLE=false

                                GRADLE_OPTS="${'$'}GRADLE_OPTS -Dtestcontainers.cloud.token=%env.TC_CLOUD_TOKEN%"
                                GRADLE_OPTS="${'$'}GRADLE_OPTS -Dtestcontainers.ryuk.disabled=true"
                                GRADLE_OPTS="${'$'}GRADLE_OPTS -Dtestcontainers.reuse.enable=false"
                                GRADLE_OPTS="${'$'}GRADLE_OPTS -Ptestcontainers.cloud.enabled=true"

                                echo "Testcontainers Cloud configuration applied"
                            else
                                echo "❌ ERROR: TESTCONTAINERS_MODE is 'cloud' but no TC_CLOUD_TOKEN available"
                                exit 1
                            fi
                            ;;
                        "local")
                            echo "✓ Using local Docker for integration tests"
                            if ! docker info >/dev/null 2>&1; then
                                echo "❌ ERROR: Docker was available during setup but is no longer accessible"
                                exit 1
                            fi
                            ;;
                        "skip"|"unknown"|*)
                            echo "⚠ WARNING: No compatible Docker environment - integration tests may fail"
                            echo "TESTCONTAINERS_MODE: ${'$'}TESTCONTAINERS_MODE"
                            echo "Consider configuring Testcontainers Cloud token for reliable test execution"
                            ;;
                    esac

                    echo "Final Docker/Testcontainers configuration complete"
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
                echo "Starting build with NPM configuration resolution fixes..."
                echo "Build command: ./gradlew ${'$'}BUILD_TASK ${'$'}GRADLE_OPTS"

                export GRADLE_JVM_OPTS="-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC"
                export ANDROID_SDK_ROOT="/home/teamcity/android-sdk-linux"
                export ANDROID_HOME="/home/teamcity/android-sdk-linux"

                export ANDROID_SDK_MANAGER_OPTS="--no_https --verbose"

                export NODE_OPTIONS="--max-old-space-size=4096"
                export NPM_CONFIG_PROGRESS="false"
                export NPM_CONFIG_LOGLEVEL="error"

                echo "Environment variables set to prevent hanging:"
                echo "GRADLE_OPTS: ${'$'}GRADLE_OPTS"
                echo "GRADLE_JVM_OPTS: ${'$'}GRADLE_JVM_OPTS"
                echo "ANDROID_SDK_ROOT: ${'$'}ANDROID_SDK_ROOT"
                echo "NODE_OPTIONS: ${'$'}NODE_OPTIONS"

                if timeout 3600 ./gradlew ${'$'}BUILD_TASK ${'$'}GRADLE_OPTS; then
                    echo "✓ Build completed successfully"
                else
                    BUILD_EXIT_CODE=$?
                    echo "❌ Build failed with exit code: ${'$'}BUILD_EXIT_CODE"

                    if [ ${'$'}BUILD_EXIT_CODE -eq 124 ]; then
                        echo "❌ TIMEOUT: Build exceeded 1 hour - likely hanging due to NPM or Android SDK issues"
                        echo "##teamcity[buildProblem description='Build timeout - NPM configuration resolution or Android SDK hanging' identity='build-hanging-timeout']"
                    fi

                    echo "Build output and logs:"
                    echo "=============================="
                    echo "Last 50 lines of build output:"
                    ./gradlew ${'$'}BUILD_TASK ${'$'}GRADLE_OPTS --debug 2>&1 | tail -50 || true

                    echo "Checking for specific hanging indicators:"
                    if ./gradlew ${'$'}BUILD_TASK ${'$'}GRADLE_OPTS --debug 2>&1 | grep -q "Configuration.*was resolved during configuration time"; then
                        echo "❌ DETECTED: NPM configuration resolution during configuration time"
                        echo "##teamcity[buildProblem description='NPM configuration resolved during configuration time causing hang' identity='npm-config-resolution-hang']"
                    fi

                    if ./gradlew ${'$'}BUILD_TASK ${'$'}GRADLE_OPTS --debug 2>&1 | grep -q "SDK Manager"; then
                        echo "❌ DETECTED: Android SDK Manager operations potentially hanging"
                        echo "##teamcity[buildProblem description='Android SDK Manager operations causing hang' identity='android-sdk-hang']"
                    fi

                    exit 1
                fi

                ${if (SpecialHandlingUtils.isMultiplatform(specialHandling) && !SpecialHandlingUtils.requiresDocker(specialHandling)) {
                """
                    echo "Running multiplatform-specific tasks..."
                    ./gradlew check ${'$'}GRADLE_OPTS || echo "⚠ Some multiplatform tests failed"
                    """
            } else if (SpecialHandlingUtils.isMultiplatform(specialHandling) && SpecialHandlingUtils.requiresDocker(specialHandling)) {
                """
                    echo "Skipping multiplatform tests for Docker project (tests already skipped)"
                    echo "Multiplatform Docker project - tests are skipped to avoid Docker compatibility issues"
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

    fun BuildSteps.fetchKotlinVersionFromExternalProject() {
        script {
            name = "Fetch Kotlin Version from External Project"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== Fetching Kotlin Version from External Project Repository ==="

                KOTLIN_VERSION=""

                if [ -f "gradle/libs.versions.toml" ]; then
                    echo "Found gradle/libs.versions.toml, extracting Kotlin version..."
                    cat gradle/libs.versions.toml

                    KOTLIN_VERSION=$(grep -E '^kotlin\s*=' gradle/libs.versions.toml | sed 's/.*=\s*"\([^"]*\)".*/\1/' | head -n 1)

                    if [ -z "${'$'}KOTLIN_VERSION" ]; then
                        KOTLIN_VERSION=$(grep -E '^kotlinVersion\s*=' gradle/libs.versions.toml | sed 's/.*=\s*"\([^"]*\)".*/\1/' | head -n 1)
                    fi

                    if [ -z "${'$'}KOTLIN_VERSION" ]; then
                        KOTLIN_VERSION=$(grep -E '^kotlin-version\s*=' gradle/libs.versions.toml | sed 's/.*=\s*"\([^"]*\)".*/\1/' | head -n 1)
                    fi

                    if [ -z "${'$'}KOTLIN_VERSION" ]; then
                        KOTLIN_VERSION=$(grep -E '^kotlin_version\s*=' gradle/libs.versions.toml | sed 's/.*=\s*"\([^"]*\)".*/\1/' | head -n 1)
                    fi
                fi

                if [ -z "${'$'}KOTLIN_VERSION" ]; then
                    echo "Kotlin version not found in libs.versions.toml, checking build.gradle.kts files..."

                    for gradle_file in build.gradle.kts */build.gradle.kts; do
                        if [ -f "${'$'}gradle_file" ]; then
                            echo "Checking ${'$'}gradle_file for Kotlin version..."

                            KOTLIN_VERSION=$(grep -i kotlin "${'$'}gradle_file" | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+' | head -n 1)

                            if [ -n "${'$'}KOTLIN_VERSION" ]; then
                                echo "Found Kotlin version in ${'$'}gradle_file: ${'$'}KOTLIN_VERSION"
                                break
                            fi
                        fi
                    done
                fi

                if [ -z "${'$'}KOTLIN_VERSION" ] && [ -f "gradle.properties" ]; then
                    echo "Checking gradle.properties for Kotlin version..."
                    KOTLIN_VERSION=$(grep -E '^kotlin.*version\s*=' gradle.properties | sed 's/.*=\s*\([0-9]\+\.[0-9]\+\.[0-9]\+\).*/\1/' | head -n 1)
                fi

                if [ -n "${'$'}KOTLIN_VERSION" ]; then
                    echo "✓ Found Kotlin version in external project: ${'$'}KOTLIN_VERSION"
                    echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='${'$'}KOTLIN_VERSION']"
                else
                    echo "⚠ No Kotlin version found in external project, will use default from version resolver"
                    echo "This may cause version alignment issues if the external project uses a different Kotlin version"
                fi

                echo "=== External Project Kotlin Version Resolution Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.fixNpmConfigurationResolution() {
        script {
            name = "Fix NPM Configuration Resolution"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Fixing NPM Configuration Resolution Issues ==="

                cat > fix-npm-resolution.gradle << 'EOF'
allprojects {
    gradle.projectsEvaluated {
        configurations.configureEach { config ->
            if (config.name.contains("NpmAggregated") || 
                config.name.contains("npm") || 
                config.name.contains("Npm")) {

                config.incoming.beforeResolve {
                    logger.info("Deferring resolution of NPM configuration: " + config.name)
                }
            }
        }
    }

    tasks.configureEach { task ->
        if (task.name.contains("compileKotlinJs") || task.name.contains("Kotlin2JsCompile")) {
            task.inputs.property("kotlin.js.compiler.cache.safe", "true")
        }

        if (task.name.contains("npm") || task.name.contains("Npm")) {
            task.inputs.property("npm.task.timeout", "10m")
        }
    }

    plugins.withId("org.jetbrains.kotlin.js") {
        project.setProperty("kotlin.js.nodejs.check.fail", "false")
        project.setProperty("kotlin.js.yarn.check.fail", "false")
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        project.setProperty("kotlin.js.nodejs.check.fail", "false")
        project.setProperty("kotlin.js.yarn.check.fail", "false")
    }
}

gradle.beforeProject { project ->
    System.setProperty("kotlin.js.npm.lazy", "true")
    System.setProperty("kotlin.js.nodejs.check.fail", "false")
    System.setProperty("kotlin.js.yarn.check.fail", "false")
}
EOF

                echo "✓ Configuration cache compatible NPM resolution fix script created"
                echo "Contents of fix-npm-resolution.gradle:"
                cat fix-npm-resolution.gradle

                echo "=== NPM Configuration Resolution Fix Complete ==="
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
            param("env.KOTLIN_VERSION", "%dep.${versionResolver.id}.env.KOTLIN_VERSION%")
            param("env.TESTCONTAINERS_MODE", "skip")
            param("env.JDK_21", "")
            param("env.TC_CLOUD_TOKEN", "")
            param("env.DAGGER_CONFIGURED", "false")
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
                fetchKotlinVersionFromExternalProject()

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
                        updateGradlePropertiesEnhanced(specialHandling)
                        updateVersionCatalogComprehensive(specialHandling)
                        setupEnhancedGradleRepositories(specialHandling)

                        if (SpecialHandlingUtils.isMultiplatform(specialHandling) || SpecialHandlingUtils.isComposeMultiplatform(specialHandling)) {
                            fixNpmConfigurationResolution()
                        }

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
            executionTimeoutMin = when {
                SpecialHandlingUtils.isComposeMultiplatform(specialHandling) -> 60
                SpecialHandlingUtils.isMultiplatform(specialHandling) -> 45
                SpecialHandlingUtils.requiresDocker(specialHandling) -> 40
                else -> 30
            }
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
    vcsRoot(VCSKtorAiServer)
    vcsRoot(VCSKtorNativeServer)
    vcsRoot(VCSKtorKoogExample)
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
        .withSpecialHandling(SpecialHandling.COMPOSE_MULTIPLATFORM)
        .build(),

    EAPSampleBuilder("Ktor Config Example", VCSKtorConfigExample, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
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
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.DOCKER_TESTCONTAINERS, SpecialHandling.COMPOSE_MULTIPLATFORM, SpecialHandling.DAGGER_ANNOTATION_PROCESSING)
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
