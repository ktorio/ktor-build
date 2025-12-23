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
import subprojects.build.defaultBuildFeatures
import subprojects.build.defaultGradleParams

object EAPConfig {
    object Repositories {
        const val KTOR_EAP = "https://maven.pkg.jetbrains.space/public/p/ktor/eap"
        const val COMPOSE_DEV = "https://maven.pkg.jetbrains.space/public/p/compose/dev"
        const val ANDROIDX_DEV = "https://androidx.dev/storage/compose-compiler/repository"
        const val GOOGLE_MAVEN = "https://maven.google.com"
    }
}

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

object EAPScriptTemplates {
    fun repositoryConfiguration() = """
        repositories {
            maven { url = uri("${EAPConfig.Repositories.KTOR_EAP}") }
            maven { url = uri("${EAPConfig.Repositories.COMPOSE_DEV}") }
            maven { url = uri("${EAPConfig.Repositories.ANDROIDX_DEV}") }
            maven { url = uri("${EAPConfig.Repositories.GOOGLE_MAVEN}") }
            mavenCentral()
            gradlePluginPortal()
        }
    """.trimIndent()

    fun composeMultiplatformRepositories() = """
        repositories {
            maven { url = uri("${EAPConfig.Repositories.COMPOSE_DEV}") }
            maven { url = uri("${EAPConfig.Repositories.ANDROIDX_DEV}") }
            maven { url = uri("${EAPConfig.Repositories.KTOR_EAP}") }
            maven { url = uri("${EAPConfig.Repositories.GOOGLE_MAVEN}") }
            mavenCentral()
            gradlePluginPortal()
        }
    """.trimIndent()

    fun buildCommonSetup() = """
        set -e
        echo "=== Build Environment Setup ==="
        echo "Working directory: $(pwd)"
        echo "Available files:"
        ls -la || echo "Cannot list files"
        echo "Java version:"
        java -version || echo "Java not available"
        echo "Gradle version:"
        ./gradlew --version || gradle --version || echo "Gradle not available"
        echo "==============================="
    """.trimIndent()
}

object ExternalSampleScripts {
    fun backupConfigFiles() = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Backing up configuration files ==="
        for file in build.gradle.kts build.gradle settings.gradle.kts settings.gradle gradle.properties module.yaml; do
            if [ -f "${'$'}file" ]; then
                echo "Backing up ${'$'}file"
                cp "${'$'}file" "${'$'}file.backup"
            fi
        done
        
        if [ -f gradle/libs.versions.toml ]; then
            echo "Backing up gradle/libs.versions.toml"
            cp gradle/libs.versions.toml gradle/libs.versions.toml.backup
        fi
        
        echo "=== Configuration backup complete ==="
    """.trimIndent()

    fun analyzeProjectStructure(specialHandling: List<SpecialHandling> = emptyList()) = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Project Structure Analysis ==="
        echo "Root level files:"
        ls -la *.gradle* *.yaml *.yml *.properties 2>/dev/null || echo "No build files at root"
        
        echo ""
        echo "Gradle directory:"
        ls -la gradle/ 2>/dev/null || echo "No gradle directory"
        
        echo ""
        echo "Source directories:"
        find src -type d -name "*" 2>/dev/null | head -10 || echo "No src directory"
        
        echo ""
        echo "Special handling requirements:"
        ${specialHandling.joinToString("\n") { "echo \"- $it\"" }}
        
        echo "=== Analysis complete ==="
    """.trimIndent()

    fun setupTestcontainersEnvironment() = """
        #!/bin/bash
        set -e
        echo "=== Setting up Testcontainers Environment ==="
        
        echo "✓ Configuring Testcontainers Cloud environment"
        
        mkdir -p ${'$'}HOME/.testcontainers
        cat > ${'$'}HOME/.testcontainers/testcontainers.properties << 'EOF'
testcontainers.reuse.enable=false
ryuk.container.privileged=true
testcontainers.cloud.token=%testcontainers-cloud-token%
EOF
        
        export TESTCONTAINERS_CLOUD_TOKEN="%testcontainers-cloud-token%"
        export TESTCONTAINERS_RYUK_DISABLED=true
        export TESTCONTAINERS_DOCKER_API_VERSION=1.44
        export TESTCONTAINERS_CHECKS_DISABLE=true
        
        echo "##teamcity[setParameter name='env.TESTCONTAINERS_CLOUD_TOKEN' value='%testcontainers-cloud-token%']"
        echo "##teamcity[setParameter name='env.TESTCONTAINERS_RYUK_DISABLED' value='true']"
        echo "##teamcity[setParameter name='env.TESTCONTAINERS_DOCKER_API_VERSION' value='1.44']"
        echo "##teamcity[setParameter name='env.TESTCONTAINERS_CHECKS_DISABLE' value='true']"
        echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='cloud']"
        
        if [ -f "gradle.properties" ]; then
            echo "Updating existing gradle.properties with Testcontainers Cloud configuration"
            sed -i '/^testcontainers\./d' gradle.properties
            sed -i '/^TESTCONTAINERS_/d' gradle.properties
        else
            echo "Creating gradle.properties with Testcontainers Cloud configuration"
            touch gradle.properties
        fi
        
        echo "" >> gradle.properties
        echo "# Testcontainers Cloud Configuration" >> gradle.properties
        echo "testcontainers.cloud.token=%testcontainers-cloud-token%" >> gradle.properties
        echo "testcontainers.ryuk.disabled=true" >> gradle.properties
        echo "testcontainers.docker.api.version=1.44" >> gradle.properties
        echo "testcontainers.checks.disable=true" >> gradle.properties
        echo "systemProp.testcontainers.cloud.token=%testcontainers-cloud-token%" >> gradle.properties
        echo "systemProp.testcontainers.ryuk.disabled=true" >> gradle.properties
        echo "systemProp.testcontainers.docker.api.version=1.44" >> gradle.properties
        echo "systemProp.testcontainers.checks.disable=true" >> gradle.properties
        
        echo "Contents of gradle.properties:"
        cat gradle.properties
        echo ""
        echo "Contents of testcontainers.properties:"
        cat ${'$'}HOME/.testcontainers/testcontainers.properties
        
        echo "✓ Testcontainers Cloud configured successfully"
        echo "=== Testcontainers Environment Setup Complete ==="
    """.trimIndent()

    fun setupAndroidSDK() = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Android SDK Setup ==="
        if [ -n "${'$'}ANDROID_HOME" ] && [ -d "${'$'}ANDROID_HOME" ]; then
            echo "✓ Android SDK found at: ${'$'}ANDROID_HOME"
            echo "##teamcity[setParameter name='env.ANDROID_SDK_SETUP' value='true']"
        else
            echo "⚠ Android SDK not found, setting up..."
            echo "##teamcity[setParameter name='env.ANDROID_SDK_SETUP' value='false']"
        fi
        echo "=== Android SDK Setup Complete ==="
    """.trimIndent()

    fun setupDaggerEnvironment() = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Dagger Environment Setup ==="
        echo "Configuring annotation processing for Dagger..."
        
        if [ -f "gradle.properties" ]; then
            echo "kapt.incremental.apt=false" >> gradle.properties
            echo "kapt.use.worker.api=false" >> gradle.properties
        fi
        
        echo "##teamcity[setParameter name='env.DAGGER_SETUP' value='true']"
        echo "=== Dagger Environment Setup Complete ==="
    """.trimIndent()

    fun updateGradlePropertiesEnhanced() = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Enhanced Gradle Properties Update ==="
        
        if [ ! -f "gradle.properties" ]; then
            echo "Creating gradle.properties"
            touch gradle.properties
        fi
        
        echo "Updating gradle.properties with EAP configuration..."
        
        sed -i '/^kotlin\.version/d' gradle.properties
        sed -i '/^ktor\.version/d' gradle.properties
        sed -i '/^compose\.version/d' gradle.properties
        
        echo "" >> gradle.properties
        echo "# EAP Versions" >> gradle.properties
        echo "kotlin.version=%env.KTOR_VERSION%" >> gradle.properties
        echo "ktor.version=%env.KTOR_VERSION%" >> gradle.properties
        echo "ktor.compiler.plugin.version=%env.KTOR_COMPILER_PLUGIN_VERSION%" >> gradle.properties
        
        echo "" >> gradle.properties
        echo "# Build Performance" >> gradle.properties
        echo "org.gradle.parallel=true" >> gradle.properties
        echo "org.gradle.caching=true" >> gradle.properties
        echo "org.gradle.configureondemand=true" >> gradle.properties
        echo "kotlin.code.style=official" >> gradle.properties
        
        echo "Updated gradle.properties:"
        cat gradle.properties
        echo "=== Enhanced Gradle Properties Update Complete ==="
    """.trimIndent()

    fun updateVersionCatalogComprehensive(specialHandling: List<SpecialHandling> = emptyList()) = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Comprehensive Version Catalog Update ==="
        
        TOML_FILE="gradle/libs.versions.toml"
        
        if [ ! -f "${'$'}TOML_FILE" ]; then
            echo "Creating version catalog at ${'$'}TOML_FILE"
            mkdir -p gradle
            cat > "${'$'}TOML_FILE" << 'EOF'
[versions]
kotlin = "PLACEHOLDER"
ktor = "PLACEHOLDER"

[libraries]

[plugins]
EOF
        fi
        
        echo "Updating version catalog with EAP versions..."
        
        sed -i 's/kotlin = "[^"]*"/kotlin = "%env.KTOR_VERSION%"/' "${'$'}TOML_FILE"
        sed -i 's/ktor = "[^"]*"/ktor = "%env.KTOR_VERSION%"/' "${'$'}TOML_FILE"
        
        if ! grep -q "kotlin.*=" "${'$'}TOML_FILE"; then
            sed -i '/\[versions\]/a kotlin = "%env.KTOR_VERSION%"' "${'$'}TOML_FILE"
        fi
        
        if ! grep -q "ktor.*=" "${'$'}TOML_FILE"; then
            sed -i '/\[versions\]/a ktor = "%env.KTOR_VERSION%"' "${'$'}TOML_FILE"
        fi
        
        ${if (specialHandling.contains(SpecialHandling.COMPOSE_MULTIPLATFORM)) """
        echo "Adding Compose Multiplatform specific versions..."
        if ! grep -q "compose.*=" "${'$'}TOML_FILE"; then
            sed -i '/\[versions\]/a compose = "1.6.0"' "${'$'}TOML_FILE"
        fi
        """ else ""}
        
        echo "Updated version catalog:"
        cat "${'$'}TOML_FILE"
        echo "=== Comprehensive Version Catalog Update Complete ==="
    """.trimIndent()

    fun setupEnhancedGradleRepositories(specialHandling: List<SpecialHandling> = emptyList()) = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Enhanced Gradle Repositories Setup ==="
        
        REPO_CONFIG="${if (specialHandling.contains(SpecialHandling.COMPOSE_MULTIPLATFORM))
        EAPScriptTemplates.composeMultiplatformRepositories()
    else
        EAPScriptTemplates.repositoryConfiguration()}"
        
        cat > gradle-repositories-init.gradle <<EOF
allprojects {
    ${'$'}REPO_CONFIG
}

pluginManagement {
    ${'$'}REPO_CONFIG
}
EOF
        
        echo "Enhanced repositories configuration created"
        echo "=== Enhanced Gradle Repositories Setup Complete ==="
    """.trimIndent()

    fun configureKotlinMultiplatform() = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Kotlin Multiplatform Configuration ==="
        
        if [ -f "gradle.properties" ]; then
            echo "kotlin.mpp.stability.nowarn=true" >> gradle.properties
            echo "kotlin.native.ignoreDisabledTargets=true" >> gradle.properties
            echo "kotlin.incremental.multiplatform=true" >> gradle.properties
        fi
        
        echo "##teamcity[setParameter name='env.KMP_CONFIGURED' value='true']"
        echo "=== Kotlin Multiplatform Configuration Complete ==="
    """.trimIndent()

    fun handleAmperGradleHybrid() = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Amper-Gradle Hybrid Handling ==="
        
        if [ -f "module.yaml" ]; then
            echo "Found Amper module.yaml, backing up..."
            cp module.yaml module.yaml.backup
            
            if command -v yq >/dev/null 2>&1; then
                echo "Using yq to update module.yaml"
                yq eval '.dependencies.ktor = "%env.KTOR_VERSION%"' -i module.yaml
            else
                echo "yq not available, using sed for basic updates"
                sed -i 's/ktor: .*/ktor: "%env.KTOR_VERSION%"/' module.yaml
            fi
        fi
        
        echo "##teamcity[setParameter name='env.AMPER_HYBRID_CONFIGURED' value='true']"
        echo "=== Amper-Gradle Hybrid Handling Complete ==="
    """.trimIndent()

    fun buildGradleProjectEnhanced(specialHandling: List<SpecialHandling> = emptyList()) = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Enhanced Gradle Build ==="
        
        BUILD_ARGS="clean build"
        GRADLE_OPTS=""
        
        ${if (specialHandling.contains(SpecialHandling.DOCKER_TESTCONTAINERS)) """
        echo "Adding Testcontainers specific arguments..."
        BUILD_ARGS="${'$'}BUILD_ARGS -Ptestcontainers.cloud.enabled=true"
        """ else ""}
        
        ${if (specialHandling.contains(SpecialHandling.KOTLIN_MULTIPLATFORM)) """
        echo "Adding Kotlin Multiplatform specific arguments..."
        BUILD_ARGS="${'$'}BUILD_ARGS --no-parallel"
        """ else ""}
        
        ${if (specialHandling.contains(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)) """
        echo "Adding Dagger annotation processing arguments..."
        BUILD_ARGS="${'$'}BUILD_ARGS -Pkapt.incremental.apt=false"
        """ else ""}
        
        echo "Build command: ./gradlew ${'$'}BUILD_ARGS"
        
        if [ -f "./gradlew" ]; then
            ./gradlew ${'$'}BUILD_ARGS --init-script gradle-repositories-init.gradle || {
                echo "Build failed, attempting without init script..."
                ./gradlew ${'$'}BUILD_ARGS
            }
        else
            gradle ${'$'}BUILD_ARGS
        fi
        
        echo "=== Enhanced Gradle Build Complete ==="
    """.trimIndent()

    fun setupAmperRepositories() = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Amper Repositories Setup ==="
        
        cat > amper-repositories.yaml <<EOF
repositories:
  - url: "${EAPConfig.Repositories.KTOR_EAP}"
  - url: "${EAPConfig.Repositories.COMPOSE_DEV}"
  - url: "https://repo1.maven.org/maven2/"
EOF
        
        echo "Amper repositories configuration created"
        echo "=== Amper Repositories Setup Complete ==="
    """.trimIndent()

    fun updateAmperVersionsEnhanced() = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Enhanced Amper Versions Update ==="
        
        if [ -f "module.yaml" ]; then
            echo "Updating Amper module.yaml with EAP versions..."
            
            cp module.yaml module.yaml.backup
            
            if command -v yq >/dev/null 2>&1; then
                yq eval '.dependencies.ktor = "%env.KTOR_VERSION%"' -i module.yaml
                yq eval '.dependencies.kotlin = "%env.KTOR_VERSION%"' -i module.yaml
            else
                echo "Using sed for Amper version updates"
                sed -i 's/ktor: .*/ktor: "%env.KTOR_VERSION%"/' module.yaml
                sed -i 's/kotlin: .*/kotlin: "%env.KTOR_VERSION%"/' module.yaml
            fi
            
            echo "Updated module.yaml:"
            cat module.yaml
        fi
        
        echo "=== Enhanced Amper Versions Update Complete ==="
    """.trimIndent()

    fun buildAmperProjectEnhanced() = """
        #!/bin/bash
        ${EAPScriptTemplates.buildCommonSetup()}
        
        echo "=== Enhanced Amper Build ==="
        
        if command -v amper >/dev/null 2>&1; then
            echo "Building with Amper..."
            amper build
        else
            echo "Amper not available, falling back to Gradle..."
            if [ -f "./gradlew" ]; then
                ./gradlew build
            else
                gradle build
            fi
        fi
        
        echo "=== Enhanced Amper Build Complete ==="
    """.trimIndent()
}

data class ExternalSampleConfig(
    override val projectName: String,
    val vcsRoot: VcsRoot,
    val buildType: ExternalSampleBuildType,
    val versionResolver: BuildType,
    val specialHandling: List<SpecialHandling>
) : ExternalEAPSampleConfig {

    override fun createEAPBuildType(): BuildType = BuildType {
        id("KtorExternalEAPSample_${projectName.replace(Regex("[^a-zA-Z0-9_]"), "_")}")
        name = "EAP Validate External $projectName"
        description = "Validate $projectName against EAP versions of Ktor with enhanced handling"

        vcs {
            root(vcsRoot)
        }

        requirements {
            agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)

            if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                exists("env.ANDROID_HOME")
            }

            if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                exists("docker.version")
            }
        }

        params {
            defaultGradleParams()

            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_COMPILER_PLUGIN_VERSION%")

            param("testcontainers.cloud.enabled", "true")
            param("env.TESTCONTAINERS_CLOUD_TOKEN", "%testcontainers-cloud-token%")
            param("env.TESTCONTAINERS_RYUK_DISABLED", "true")
            param("env.TESTCONTAINERS_DOCKER_API_VERSION", "1.44")
            param("env.TESTCONTAINERS_CHECKS_DISABLE", "true")

            specialHandling.forEach { handling ->
                param("env.SPECIAL_HANDLING_${handling.name}", "true")
            }
        }

        steps {
            script {
                name = "Debug Environment"
                scriptContent = """
                    #!/bin/bash
                    echo "=== Environment Debug ==="
                    echo "KTOR_VERSION: %env.KTOR_VERSION%"
                    echo "KTOR_COMPILER_PLUGIN_VERSION: %env.KTOR_COMPILER_PLUGIN_VERSION%"
                    echo "TESTCONTAINERS_CLOUD_TOKEN: [CONFIGURED]"
                    echo "Special handling: ${specialHandling.joinToString(", ")}"
                    echo "==============================="
                """.trimIndent()
            }

            script {
                name = "Backup Configuration Files"
                scriptContent = ExternalSampleScripts.backupConfigFiles()
            }

            script {
                name = "Analyze Project Structure"
                scriptContent = ExternalSampleScripts.analyzeProjectStructure(specialHandling)
            }

            if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                script {
                    name = "Setup Testcontainers Cloud"
                    scriptContent = ExternalSampleScripts.setupTestcontainersEnvironment()
                }
            }

            if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                script {
                    name = "Setup Android SDK"
                    scriptContent = ExternalSampleScripts.setupAndroidSDK()
                }
            }

            if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
                script {
                    name = "Setup Dagger Environment"
                    scriptContent = ExternalSampleScripts.setupDaggerEnvironment()
                }
            }

            when (buildType) {
                ExternalSampleBuildType.GRADLE -> {
                    script {
                        name = "Update Gradle Properties"
                        scriptContent = ExternalSampleScripts.updateGradlePropertiesEnhanced()
                    }

                    script {
                        name = "Update Version Catalog"
                        scriptContent = ExternalSampleScripts.updateVersionCatalogComprehensive(specialHandling)
                    }

                    script {
                        name = "Setup Enhanced Repositories"
                        scriptContent = ExternalSampleScripts.setupEnhancedGradleRepositories(specialHandling)
                    }

                    if (SpecialHandlingUtils.isMultiplatform(specialHandling)) {
                        script {
                            name = "Configure Kotlin Multiplatform"
                            scriptContent = ExternalSampleScripts.configureKotlinMultiplatform()
                        }
                    }

                    if (SpecialHandlingUtils.isAmperHybrid(specialHandling)) {
                        script {
                            name = "Handle Amper-Gradle Hybrid"
                            scriptContent = ExternalSampleScripts.handleAmperGradleHybrid()
                        }
                    }

                    script {
                        name = "Build Enhanced Gradle Project"
                        scriptContent = ExternalSampleScripts.buildGradleProjectEnhanced(specialHandling)
                    }
                }

                ExternalSampleBuildType.AMPER -> {
                    script {
                        name = "Setup Amper Repositories"
                        scriptContent = ExternalSampleScripts.setupAmperRepositories()
                    }

                    script {
                        name = "Update Amper Versions"
                        scriptContent = ExternalSampleScripts.updateAmperVersionsEnhanced()
                    }

                    script {
                        name = "Build Enhanced Amper Project"
                        scriptContent = ExternalSampleScripts.buildAmperProjectEnhanced()
                    }
                }
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "BUILD FAILED"
                failureMessage = "Build failed for $projectName external sample"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "client version.*is too old"
                failureMessage = "Docker API version compatibility issue in $projectName"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 25
        }

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

private fun createVersionResolver(): BuildType = BuildType {
    id("KtorExternalEAPVersionResolver")
    name = "Resolve EAP Versions for External Samples"
    description = "Determines EAP versions for external sample validation"

    vcs {
        root(VCSCore)
    }

    steps {
        script {
            name = "Resolve EAP Versions"
            scriptContent = """
                #!/bin/bash
                set -e
                
                echo "=== EAP Version Resolution ==="
                
                KTOR_VERSION=$(curl -s "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml" | grep -oP '(?<=<latest>).*?(?=</latest>)' || echo "3.0.0-eap-1")
                COMPILER_VERSION=$(curl -s "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-compiler-plugin/maven-metadata.xml" | grep -oP '(?<=<latest>).*?(?=</latest>)' || echo "${'$'}KTOR_VERSION")
                
                echo "Resolved KTOR_VERSION: ${'$'}KTOR_VERSION"
                echo "Resolved COMPILER_VERSION: ${'$'}COMPILER_VERSION"
                
                echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}KTOR_VERSION']"
                echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}COMPILER_VERSION']"
                
                echo "=== EAP Version Resolution Complete ==="
            """.trimIndent()
        }
    }

    requirements {
        agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)
    }
}

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalSampleConfig> = listOf(
    EAPSampleBuilder("ktor-arrow-example", VCSKtorArrowExample, versionResolver)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("ktor-ai-server", VCSKtorAiServer, versionResolver)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("ktor-native-server", VCSKtorNativeServer, versionResolver)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
        .build(),

    EAPSampleBuilder("ktor-koog-example", VCSKtorKoogExample, versionResolver)
        .withSpecialHandling(SpecialHandling.COMPOSE_MULTIPLATFORM, SpecialHandling.ANDROID_SDK_REQUIRED)
        .build(),

    EAPSampleBuilder("full-stack-ktor-talk", VCSFullStackKtorTalk, versionResolver)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
        .build(),

    EAPSampleBuilder("ktor-config-example", VCSKtorConfigExample, versionResolver)
        .build(),

    EAPSampleBuilder("ktor-workshop-2025", VCSKtorWorkshop2025, versionResolver)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("amper-ktor-sample", VCSAmperKtorSample, versionResolver)
        .withBuildType(ExternalSampleBuildType.AMPER)
        .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID)
        .build(),

    EAPSampleBuilder("ktor-di-overview", VCSKtorDIOverview, versionResolver)
        .withSpecialHandling(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)
        .build(),

    EAPSampleBuilder("ktor-full-stack-real-world", VCSKtorFullStackRealWorld, versionResolver)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.DOCKER_TESTCONTAINERS)
        .build()
)

private fun createCompositeBuild(versionResolver: BuildType, buildTypes: List<BuildType>): BuildType = BuildType {
    id("KtorExternalEAPSamplesCompositeBuild")
    name = "Validate All External Samples with EAP"
    description = "Run all external samples against EAP version of Ktor with Testcontainers Cloud support"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("env.GIT_BRANCH", "%teamcity.build.branch%")
        param("teamcity.build.skipDependencyBuilds", "true")
        param("testcontainers.cloud.enabled", "true")
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
            buildType = "KtorPublish_AllEAP"
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

        buildTypes.forEach { sampleBuild ->
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
            pattern = "client version.*is too old"
            failureMessage = "Docker API version compatibility issue in external samples"
            stopBuildOnFailure = true
        }
        executionTimeoutMin = 45
    }
}
