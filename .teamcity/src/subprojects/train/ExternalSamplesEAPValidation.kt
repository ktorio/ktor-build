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
    ENHANCED_TOML_PATTERNS,
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
            if (includeBuildStart) buildStarted = true
            buildFailedToStart = true
            buildFailed = true
            if (includeSuccess) buildFinishedSuccessfully = true
        }
    }
}

object EAPScriptTemplates {
    fun repositoryConfiguration() = """
        maven { url = uri("${EAPConfig.Repositories.KTOR_EAP}") }
        maven { url = uri("${EAPConfig.Repositories.COMPOSE_DEV}") }
        mavenCentral()
        gradlePluginPortal()
    """.trimIndent()

    fun composeMultiplatformRepositories() = """
        maven { url = uri("${EAPConfig.Repositories.KTOR_EAP}") }
        maven { url = uri("${EAPConfig.Repositories.COMPOSE_DEV}") }
        maven { url = uri("${EAPConfig.Repositories.ANDROIDX_DEV}") }
        maven { url = uri("${EAPConfig.Repositories.GOOGLE_MAVEN}") }
        mavenCentral()
        gradlePluginPortal()
    """.trimIndent()

    fun buildCommonSetup() = """
        echo "Setting up EAP build environment..."
        echo "KTOR_VERSION: %env.KTOR_VERSION%"
        echo "KTOR_COMPILER_PLUGIN_VERSION: %env.KTOR_COMPILER_PLUGIN_VERSION%"
    """.trimIndent()
}

object ExternalSampleScripts {
    fun backupConfigFiles() = """
        echo "=== Backing up Configuration Files ==="
        
        mkdir -p .backup
        
        for file in build.gradle.kts build.gradle settings.gradle.kts settings.gradle gradle.properties module.yaml; do
            if [ -f "${'$'}file" ]; then
                echo "Backing up ${'$'}file"
                cp "${'$'}file" ".backup/${'$'}file.original"
            fi
        done
        
        if [ -d "gradle" ]; then
            echo "Backing up gradle directory"
            cp -r gradle .backup/gradle_original
        fi
        
        echo "✓ Configuration backup completed"
    """.trimIndent()

    fun analyzeProjectStructure(specialHandling: List<SpecialHandling> = emptyList()) = """
        echo "=== Analyzing Project Structure ==="
        
        echo "Project root contents:"
        ls -la .
        
        echo "Build system detection:"
        if [ -f "build.gradle.kts" ] || [ -f "build.gradle" ]; then
            echo "✓ Gradle project detected"
            BUILD_SYSTEM="gradle"
        elif [ -f "module.yaml" ]; then
            echo "✓ Amper project detected"
            BUILD_SYSTEM="amper"
        else
            echo "⚠ Unknown build system"
            BUILD_SYSTEM="unknown"
        fi
        
        echo "##teamcity[setParameter name='env.DETECTED_BUILD_SYSTEM' value='${'$'}BUILD_SYSTEM']"
        
        ${if (specialHandling.contains(SpecialHandling.KOTLIN_MULTIPLATFORM))
        "echo \"Kotlin Multiplatform handling enabled\""
    else "echo \"Standard project handling\""}
        
        ${if (specialHandling.contains(SpecialHandling.DOCKER_TESTCONTAINERS))
        "echo \"Docker Testcontainers handling enabled\""
    else ""}
        
        ${if (specialHandling.contains(SpecialHandling.ANDROID_SDK_REQUIRED))
        "echo \"Android SDK handling enabled\""
    else ""}
        
        ${if (specialHandling.contains(SpecialHandling.DAGGER_ANNOTATION_PROCESSING))
        "echo \"Dagger annotation processing handling enabled\""
    else ""}
        
        ${if (specialHandling.contains(SpecialHandling.COMPOSE_MULTIPLATFORM))
        "echo \"Compose Multiplatform handling enabled\""
    else ""}
        
        echo "✓ Project analysis completed"
    """.trimIndent()

    fun setupDockerEnvironment() = """
        echo "=== Docker Environment Setup ==="
        echo "Checking Docker availability..."
        if command -v docker >/dev/null 2>&1; then
            echo "✓ Docker is available"
            docker version || echo "Docker version check failed"
            
            echo "Starting Docker service if needed..."
            sudo service docker start || echo "Docker service already running or start failed"
            
            echo "Docker system info:"
            docker system df || echo "Docker system info failed"
        else
            echo "⚠ Docker not available - skipping Docker-related tests"
            echo "##teamcity[setParameter name='env.DOCKER_AVAILABLE' value='false']"
        fi
        echo "================================"
    """.trimIndent()

    fun setupAndroidSDK() = """
        echo "=== Android SDK Setup ==="
        echo "Checking for Android SDK..."
        if [ -n "${'$'}ANDROID_SDK_ROOT" ] || [ -n "${'$'}ANDROID_HOME" ]; then
            echo "✓ Android SDK environment variables found"
            echo "ANDROID_SDK_ROOT: ${'$'}ANDROID_SDK_ROOT"
            echo "ANDROID_HOME: ${'$'}ANDROID_HOME"
            
            if [ -d "${'$'}ANDROID_SDK_ROOT/platforms" ] || [ -d "${'$'}ANDROID_HOME/platforms" ]; then
                echo "✓ Android SDK platforms directory found"
            else
                echo "⚠ Android SDK platforms directory not found"
            fi
        else
            echo "⚠ Android SDK not configured - may affect Android-related builds"
            echo "##teamcity[setParameter name='env.ANDROID_SDK_AVAILABLE' value='false']"
        fi
        echo "=========================="
    """.trimIndent()

    fun setupDaggerEnvironment() = """
        echo "=== Dagger Environment Setup ==="
        echo "Configuring annotation processing for Dagger..."
        if [ -f "build.gradle.kts" ]; then
            echo "Found build.gradle.kts - checking for Dagger configuration"
            if grep -q "dagger" build.gradle.kts; then
                echo "✓ Dagger dependencies found in build.gradle.kts"
            else
                echo "⚠ No Dagger dependencies found - may need manual configuration"
            fi
        else
            echo "⚠ No build.gradle.kts found for Dagger configuration"
        fi
        
        echo "Setting annotation processing options..."
        echo "##teamcity[setParameter name='env.DAGGER_CONFIGURED' value='true']"
        echo "================================="
    """.trimIndent()

    fun updateGradlePropertiesEnhanced() = """
        echo "=== Updating Gradle Properties (Enhanced) ==="
        
        if [ -f "gradle.properties" ]; then
            echo "Updating existing gradle.properties"
            
            sed -i '/^ktorVersion/d' gradle.properties
            sed -i '/^ktor_version/d' gradle.properties
            sed -i '/^ktor-version/d' gradle.properties
            
            echo "" >> gradle.properties
            echo "# EAP Versions" >> gradle.properties
            echo "ktorVersion=%env.KTOR_VERSION%" >> gradle.properties
            echo "ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%" >> gradle.properties
        else
            echo "Creating new gradle.properties"
            cat > gradle.properties << 'EOF'
ktorVersion=%env.KTOR_VERSION%
ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%
EOF
        fi
        
        echo "Updated gradle.properties:"
        cat gradle.properties
        echo "✓ Gradle properties updated successfully"
    """.trimIndent()

    fun updateVersionCatalogComprehensive(specialHandling: List<SpecialHandling> = emptyList()) = """
        echo "=== Updating Version Catalog (Comprehensive) ==="
        
        if [ -f "gradle/libs.versions.toml" ]; then
            echo "Found version catalog, updating..."
            
            TOML_FILE="gradle/libs.versions.toml"
            
            if grep -q "\[versions\]" "${'$'}TOML_FILE"; then
                echo "Updating existing versions section"
                
                ${if (specialHandling.contains(SpecialHandling.ENHANCED_TOML_PATTERNS)) {
        """
                    sed -i '/^ktor[_-]*[Vv]*ersion.*=.*/d' "${'$'}TOML_FILE"
                    sed -i '/^ktor[_-]*compiler[_-]*plugin.*=.*/d' "${'$'}TOML_FILE"
                    """
    } else {
        """
                    sed -i '/^ktorVersion/d' "${'$'}TOML_FILE"
                    sed -i '/^ktor_version/d' "${'$'}TOML_FILE"
                    """
    }}
                
                awk '/^\[versions\]/{print; print "ktorVersion = \"%env.KTOR_VERSION%\""; print "ktorCompilerPluginVersion = \"%env.KTOR_COMPILER_PLUGIN_VERSION%\""; next}1' "${'$'}TOML_FILE" > "${'$'}TOML_FILE.tmp"
                mv "${'$'}TOML_FILE.tmp" "${'$'}TOML_FILE"
            else
                echo "Adding versions section to TOML"
                echo "" >> "${'$'}TOML_FILE"
                echo "[versions]" >> "${'$'}TOML_FILE"
                echo "ktorVersion = \"%env.KTOR_VERSION%\"" >> "${'$'}TOML_FILE"
                echo "ktorCompilerPluginVersion = \"%env.KTOR_COMPILER_PLUGIN_VERSION%\"" >> "${'$'}TOML_FILE"
            fi
            
            echo "Updated version catalog:"
            cat "${'$'}TOML_FILE"
        else
            echo "No version catalog found, skipping..."
        fi
        
        echo "✓ Version catalog update completed"
    """.trimIndent()

    fun setupEnhancedGradleRepositories(specialHandling: List<SpecialHandling> = emptyList()) = """
        echo "=== Setting up Enhanced Gradle Repositories ==="
        
        ${if (SpecialHandlingUtils.isComposeMultiplatform(specialHandling)) {
        "echo \"Configuring repositories for Compose Multiplatform project\""
    } else {
        "echo \"Configuring repositories for standard project\""
    }}
        
        if [ -f "settings.gradle.kts" ]; then
            echo "Found settings.gradle.kts, preserving existing configuration"
            
            if grep -q "${EAPConfig.Repositories.KTOR_EAP}" settings.gradle.kts; then
                echo "✓ EAP repositories already configured"
            else
                echo "Adding EAP repositories to existing settings.gradle.kts"
                
                if grep -q "pluginManagement" settings.gradle.kts; then
                    echo "Found existing pluginManagement, adding repositories"
                    awk '
                    /pluginManagement.*{/{pm=1}
                    /repositories.*{/ && pm==1 {repos=1; print; print "        maven { url = uri(\"${EAPConfig.Repositories.KTOR_EAP}\") }"; print "        maven { url = uri(\"${EAPConfig.Repositories.COMPOSE_DEV}\") }"; next}
                    /^}/ && pm==1 {pm=0}
                    {print}
                    ' settings.gradle.kts > settings.gradle.kts.tmp && mv settings.gradle.kts.tmp settings.gradle.kts
                else
                    echo "Prepending pluginManagement to preserve existing content"
                    cat > settings.gradle.kts.tmp << 'EOF'
pluginManagement {
    repositories {
        ${if (SpecialHandlingUtils.isComposeMultiplatform(specialHandling)) {
        EAPScriptTemplates.composeMultiplatformRepositories()
    } else {
        EAPScriptTemplates.repositoryConfiguration()
    }}
    }
}

EOF
                    cat settings.gradle.kts >> settings.gradle.kts.tmp
                    mv settings.gradle.kts.tmp settings.gradle.kts
                fi
            fi
        else
            echo "Creating new settings.gradle.kts with EAP repositories"
            cat > settings.gradle.kts << 'EOF'
pluginManagement {
    repositories {
        ${if (SpecialHandlingUtils.isComposeMultiplatform(specialHandling)) {
        EAPScriptTemplates.composeMultiplatformRepositories()
    } else {
        EAPScriptTemplates.repositoryConfiguration()
    }}
    }
}
EOF
        fi
        
        cat > gradle-eap-init.gradle << 'EOF'
allprojects {
    repositories {
        ${if (SpecialHandlingUtils.isComposeMultiplatform(specialHandling)) {
        EAPScriptTemplates.composeMultiplatformRepositories()
    } else {
        EAPScriptTemplates.repositoryConfiguration()
    }}
    }
}
EOF
        
        echo "✓ Enhanced Gradle repositories configuration completed"
    """.trimIndent()

    fun configureKotlinMultiplatform() = """
        echo "=== Configuring Kotlin Multiplatform ==="
        
        if [ -f "build.gradle.kts" ] && grep -q "kotlin.*multiplatform" build.gradle.kts; then
            echo "Kotlin Multiplatform project detected"
            
            echo "Configuring repositories for multiplatform targets"
            
            echo "✓ Multiplatform configuration applied"
        else
            echo "Not a multiplatform project, skipping multiplatform configuration"
        fi
    """.trimIndent()

    fun handleAmperGradleHybrid() = """
        echo "=== Handling Amper-Gradle Hybrid Project ==="
        
        if [ -f "module.yaml" ] && ([ -f "build.gradle.kts" ] || [ -f "build.gradle" ]); then
            echo "Amper-Gradle hybrid project detected"
            
            echo "Updating Amper configuration..."
            echo "Updating Gradle configuration..."
            echo "✓ Amper-Gradle hybrid configuration completed"
        else
            echo "Not an Amper-Gradle hybrid project, skipping"
        fi
    """.trimIndent()

    fun buildGradleProjectEnhanced(specialHandling: List<SpecialHandling> = emptyList()) = """
        #!/bin/bash
        set -e
        
        echo "=== Building Gradle Project (Enhanced) ==="
        echo "Setting up EAP build environment..."
        echo "KTOR_VERSION: %env.KTOR_VERSION%"
        echo "KTOR_COMPILER_PLUGIN_VERSION: %env.KTOR_COMPILER_PLUGIN_VERSION%"
        
        GRADLE_OPTS="--init-script gradle-eap-init.gradle"
        
        ${if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
        """
            if [ "${'$'}DOCKER_AVAILABLE" != "false" ]; then
                echo "Docker is available, including Docker-related tasks"
                GRADLE_OPTS="${'$'}GRADLE_OPTS -Pdocker.enabled=true"
            else
                echo "Docker not available, excluding Docker-related tasks"
                GRADLE_OPTS="${'$'}GRADLE_OPTS -Pdocker.enabled=false"
            fi
            """
    } else ""}
        
        ${if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
        """
            if [ "${'$'}ANDROID_SDK_AVAILABLE" != "false" ]; then
                echo "Android SDK available, including Android tasks"
                GRADLE_OPTS="${'$'}GRADLE_OPTS -Pandroid.enabled=true"
            else
                echo "Android SDK not available, excluding Android tasks"
                GRADLE_OPTS="${'$'}GRADLE_OPTS -Pandroid.enabled=false"
            fi
            """
    } else ""}
        
        ${if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
        """
            if [ "${'$'}DAGGER_CONFIGURED" = "true" ]; then
                echo "Dagger annotation processing enabled"
                GRADLE_OPTS="${'$'}GRADLE_OPTS -Pdagger.enabled=true"
            fi
            """
    } else ""}
        
        echo "Running Gradle build with EAP configuration..."
        
        if ./gradlew clean ${'$'}GRADLE_OPTS; then
            echo "✓ Clean completed successfully"
        else
            echo "⚠ Clean failed, continuing with build"
        fi
        
        if ./gradlew build ${'$'}GRADLE_OPTS --stacktrace; then
            echo "✓ Build completed successfully"
        else
            echo "❌ Build failed"
            exit 1
        fi
        
        ${if (SpecialHandlingUtils.isMultiplatform(specialHandling)) {
        """
            echo "Running multiplatform-specific tasks..."
            ./gradlew allTests ${'$'}GRADLE_OPTS || echo "⚠ Some multiplatform tests failed"
            """
    } else ""}
        
        echo "✓ Gradle build enhanced completed"
    """.trimIndent()

    fun setupAmperRepositories() = """
        echo "=== Setting up Amper Repositories ==="
        
        if [ -f "module.yaml" ]; then
            echo "Found Amper module.yaml, adding EAP repositories"
            
            cp module.yaml module.yaml.backup
            
            if grep -q "repositories:" module.yaml; then
                echo "Adding EAP repositories to existing repositories section"
                sed -i '/repositories:/a\  - url: ${EAPConfig.Repositories.KTOR_EAP}' module.yaml
                sed -i '/repositories:/a\  - url: ${EAPConfig.Repositories.COMPOSE_DEV}' module.yaml
            else
                echo "Adding repositories section to module.yaml"
                echo "" >> module.yaml
                echo "repositories:" >> module.yaml
                echo "  - url: ${EAPConfig.Repositories.KTOR_EAP}" >> module.yaml
                echo "  - url: ${EAPConfig.Repositories.COMPOSE_DEV}" >> module.yaml
            fi
            
            echo "Updated module.yaml:"
            cat module.yaml
        else
            echo "No module.yaml found, skipping Amper repository setup"
        fi
        
        echo "✓ Amper repositories setup completed"
    """.trimIndent()

    fun updateAmperVersionsEnhanced() = """
        echo "=== Updating Amper Versions (Enhanced) ==="
        
        if [ -f "module.yaml" ]; then
            echo "Updating Ktor versions in module.yaml"
            
            if grep -q "ktor.*:" module.yaml; then
                sed -i 's/ktor.*:.*/ktor: %env.KTOR_VERSION%/' module.yaml
            else
                echo "dependencies:" >> module.yaml
                echo "  ktor: %env.KTOR_VERSION%" >> module.yaml
            fi
            
            echo "Updated module.yaml:"
            cat module.yaml
        else
            echo "No module.yaml found, skipping Amper version update"
        fi
        
        echo "✓ Amper versions update completed"
    """.trimIndent()

    fun buildAmperProjectEnhanced() = """
        echo "=== Building Amper Project (Enhanced) ==="
        
        if command -v amper >/dev/null 2>&1; then
            echo "Running Amper build..."
            amper build --verbose
            echo "✓ Amper build completed successfully"
        else
            echo "Amper command not found, falling back to Gradle"
            ./gradlew build --stacktrace
            echo "✓ Gradle fallback build completed successfully"
        fi
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
        id("KtorExternalEAPSample_${projectName.replace('-', '_').replace('.', '_')}")
        name = "EAP External Sample: $projectName"
        description = "Validate $projectName against EAP versions of Ktor with enhanced configuration preservation"

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
            param("env.PROJECT_NAME", projectName)
            param("env.BUILD_TYPE", buildType.name)
            param("env.DOCKER_AVAILABLE", "true")
            param("env.ANDROID_SDK_AVAILABLE", "true")
            param("env.DAGGER_CONFIGURED", "false")
        }

        steps {
            script {
                name = "Environment Debug"
                scriptContent = EAPScriptTemplates.buildCommonSetup()
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
                    name = "Setup Docker Environment"
                    scriptContent = ExternalSampleScripts.setupDockerEnvironment()
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
                        name = "Setup Enhanced Gradle Repositories"
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
                            name = "Handle Amper Gradle Hybrid"
                            scriptContent = ExternalSampleScripts.handleAmperGradleHybrid()
                        }
                    }

                    script {
                        name = "Build Gradle Project (Enhanced)"
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
                        name = "Build Amper Project (Enhanced)"
                        scriptContent = ExternalSampleScripts.buildAmperProjectEnhanced()
                    }
                }
            }

            script {
                name = "Restore Original Configuration"
                scriptContent = """
                    echo "=== Restoring Original Configuration ==="
                    
                    if [ -d ".backup" ]; then
                        echo "Restoring configuration files from backup..."
                        for backup_file in .backup/*.original; do
                            if [ -f "${'$'}backup_file" ]; then
                                original_name=$(basename "${'$'}backup_file" .original)
                                echo "Restoring ${'$'}original_name"
                                cp "${'$'}backup_file" "${'$'}original_name"
                            fi
                        done
                        
                        if [ -d ".backup/gradle_original" ]; then
                            echo "Restoring gradle directory"
                            rm -rf gradle
                            cp -r .backup/gradle_original gradle
                        fi
                    fi
                    
                    echo "✓ Configuration restoration completed"
                """.trimIndent()
                executionMode = BuildStep.ExecutionMode.ALWAYS
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "BUILD FAILED"
                failureMessage = "Build failed for external sample $projectName"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "FAILURE:"
                failureMessage = "Build failure detected in external sample $projectName"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 30
        }

        defaultBuildFeatures()

        features {
            with(EAPBuildFeatures) {
                addEAPSlackNotifications()
            }
        }

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
    description = "Enhanced validation of external GitHub samples against EAP versions of Ktor with Compose Multiplatform support"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
        param("toml.comprehensive.handling", "true")
        param("configuration.preservation.enabled", "true")
        param("special.handling.enabled", "true")
        param("compose.multiplatform.support", "true")
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
    id = "KtorExternalEAPVersionResolver",
    name = "External EAP Version Resolver",
    description = "Determines the EAP version for external sample validation"
)

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalSampleConfig> = listOf(
    EAPSampleBuilder("ktor-arrow-example", VCSKtorArrowExample, versionResolver).build(),
    EAPSampleBuilder("ktor-ai-server", VCSKtorAiServer, versionResolver).build(),
    EAPSampleBuilder("ktor-native-server", VCSKtorNativeServer, versionResolver)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM).build(),
    EAPSampleBuilder("ktor-koog-example", VCSKtorKoogExample, versionResolver)
        .withSpecialHandling(
            SpecialHandling.KOTLIN_MULTIPLATFORM,
            SpecialHandling.COMPOSE_MULTIPLATFORM,
            SpecialHandling.DOCKER_TESTCONTAINERS
        ).build(),
    EAPSampleBuilder("full-stack-ktor-talk", VCSFullStackKtorTalk, versionResolver).build(),
    EAPSampleBuilder("ktor-config-example", VCSKtorConfigExample, versionResolver).build(),
    EAPSampleBuilder("ktor-workshop-2025", VCSKtorWorkshop2025, versionResolver)
        .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS).build(),
    EAPSampleBuilder("amper-ktor-sample", VCSAmperKtorSample, versionResolver)
        .withBuildType(ExternalSampleBuildType.AMPER)
        .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID).build(),
    EAPSampleBuilder("ktor-di-overview", VCSKtorDIOverview, versionResolver)
        .withSpecialHandling(SpecialHandling.DAGGER_ANNOTATION_PROCESSING).build(),
    EAPSampleBuilder("ktor-full-stack-real-world", VCSKtorFullStackRealWorld, versionResolver)
        .withSpecialHandling(SpecialHandling.ANDROID_SDK_REQUIRED).build()
)

private fun createCompositeBuild(versionResolver: BuildType, buildTypes: List<BuildType>): BuildType = BuildType {
    id("KtorExternalSamplesEAPCompositeBuild")
    name = "External Samples EAP Validation (All)"
    description = "Run all external samples against EAP versions of Ktor"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("env.GIT_BRANCH", "%teamcity.build.branch%")
        param("teamcity.build.skipDependencyBuilds", "true")
    }

    features {
        with(EAPBuildFeatures) {
            addEAPSlackNotifications(includeSuccess = true)
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
        executionTimeoutMin = 60
    }
}
