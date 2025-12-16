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
        const val GOOGLE_ANDROID = "https://dl.google.com/dl/android/maven2/"
    }
}

enum class SpecialHandling {
    KOTLIN_MULTIPLATFORM,
    AMPER_GRADLE_HYBRID,
    ENHANCED_TOML_PATTERNS,
    DOCKER_TESTCONTAINERS,
    DAGGER_ANNOTATION_PROCESSING
}

enum class ExternalSampleBuildType {
    GRADLE, AMPER
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

object DockerSupport {
    fun requiresDocker(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DOCKER_TESTCONTAINERS)
}

object DaggerSupport {
    fun requiresDagger(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)
}

object EAPBuildFeatures {
    fun BuildFeatures.addEAPSlackNotifications(
        includeSuccess: Boolean = false,
        includeBuildStart: Boolean = false
    ) {
        notifications {
            notifierSettings = slackNotifier {
                connection = "PROJECT_EXT_5"
                sendTo = "#ktor-external-samples-eap"
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
        maven { url = uri("${EAPConfig.Repositories.GOOGLE_ANDROID}") }
        mavenCentral()
        google()
        gradlePluginPortal()
    """.trimIndent()

    fun buildCommonSetup() = """
        echo "Setting up EAP build environment..."
        echo "KTOR_VERSION: %env.KTOR_VERSION%"
        echo "KTOR_COMPILER_PLUGIN_VERSION: %env.KTOR_COMPILER_PLUGIN_VERSION%"
    """.trimIndent()
}

object EAPBuildSteps {

    fun BuildSteps.standardEAPSetup(specialHandling: List<SpecialHandling> = emptyList()) {
        script {
            name = "Standard EAP Setup"
            scriptContent = ExternalSampleScripts.backupConfigFiles()
        }
        script {
            name = "Analyze Project Structure"
            scriptContent = ExternalSampleScripts.analyzeProjectStructure(specialHandling)
        }

        if (DockerSupport.requiresDocker(specialHandling)) {
            setupDockerEnvironment()
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

    fun BuildSteps.gradleEAPBuild(specialHandling: List<SpecialHandling> = emptyList()) {
        standardEAPSetup(specialHandling)
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
            scriptContent = ExternalSampleScripts.setupEnhancedGradleRepositories()
        }

        if (specialHandling.contains(SpecialHandling.KOTLIN_MULTIPLATFORM)) {
            script {
                name = "Configure Kotlin Multiplatform"
                scriptContent = ExternalSampleScripts.configureKotlinMultiplatform()
            }
        }

        if (specialHandling.contains(SpecialHandling.AMPER_GRADLE_HYBRID)) {
            script {
                name = "Handle Amper Gradle Hybrid"
                scriptContent = ExternalSampleScripts.handleAmperGradleHybrid()
            }
        }

        if (DaggerSupport.requiresDagger(specialHandling)) {
            setupDaggerEnvironment()
            script {
                name = "Configure Dagger Annotation Processing"
                scriptContent = ExternalSampleScripts.configureDaggerAnnotationProcessing()
            }
        }

        gradle {
            name = "Build Gradle Project (Enhanced)"
            tasks = if (DaggerSupport.requiresDagger(specialHandling)) {
                "clean compileKotlin kaptKotlin compileTestKotlin kaptTestKotlin test --continue --info --stacktrace"
            } else {
                "clean build --continue --info --stacktrace"
            }
            jdkHome = Env.JDK_LTS
            param("org.gradle.parallel", "false")
            param("org.gradle.daemon", "false")
            param("org.gradle.configureondemand", "false")

            if (DockerSupport.requiresDocker(specialHandling)) {
                dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
                dockerPull = true
                dockerImage = "gradle:latest"
            }
        }
    }

    fun BuildSteps.amperEAPBuild(specialHandling: List<SpecialHandling> = emptyList()) {
        standardEAPSetup(specialHandling)
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
        
        for dir in dagger koin kodein hilt; do
            if [ -d "${'$'}dir" ]; then
                echo "Backing up ${'$'}dir module configuration"
                mkdir -p ".backup/${'$'}dir"
                if [ -f "${'$'}dir/build.gradle.kts" ]; then
                    cp "${'$'}dir/build.gradle.kts" ".backup/${'$'}dir/build.gradle.kts.original"
                fi
                if [ -f "${'$'}dir/build.gradle" ]; then
                    cp "${'$'}dir/build.gradle" ".backup/${'$'}dir/build.gradle.original"
                fi
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
        
        DAGGER_FOUND=false
        if find . -name "*.kt" -o -name "*.java" | xargs grep -l "@Component\|@Module\|@Inject" | head -1; then
            echo "✓ Dagger annotations found in source files"
            DAGGER_FOUND=true
        elif find . -name "build.gradle*" -exec grep -l "dagger" {} \; | head -1; then
            echo "✓ Dagger dependencies found in build files"
            DAGGER_FOUND=true
        fi
        
        if [ "${'$'}DAGGER_FOUND" = true ]; then
            echo "PROJECT_USES_DAGGER=true" >> gradle.properties
        else
            echo "PROJECT_USES_DAGGER=false" >> gradle.properties
        fi
        
        ${if (specialHandling.contains(SpecialHandling.DOCKER_TESTCONTAINERS)) {
        """
            echo "Docker/Testcontainers support enabled"
            
            TESTCONTAINERS_FOUND=false
            if find . -name "build.gradle*" -exec grep -l "testcontainers" {} \; | head -1; then
                echo "✓ Testcontainers found in Gradle build files"
                TESTCONTAINERS_FOUND=true
            fi
            
            if [ "${'$'}TESTCONTAINERS_FOUND" = true ]; then
                echo "PROJECT_REQUIRES_DOCKER=true" >> gradle.properties
            else
                echo "⚠ Docker support enabled but no Testcontainers dependencies found"
                echo "PROJECT_REQUIRES_DOCKER=false" >> gradle.properties
            fi
            """
    } else {
        """
            echo "Standard project handling (no Docker support)"
            echo "PROJECT_REQUIRES_DOCKER=false" >> gradle.properties
            """
    }}
        
        ${if (specialHandling.contains(SpecialHandling.KOTLIN_MULTIPLATFORM))
        "echo \"Kotlin Multiplatform handling enabled\""
    else "echo \"Standard project handling\""}
        
        echo "✓ Project analysis completed"
    """.trimIndent()

    fun setupDockerEnvironment() = """
        echo "=== Docker Environment Setup ==="
        
        if ! command -v docker &> /dev/null; then
            echo "ERROR: Docker is not installed"
            exit 1
        fi
        
        if ! docker info &> /dev/null; then
            echo "ERROR: Docker daemon is not running or accessible"
            docker info || true
            exit 1
        fi
        
        echo "Docker version:"
        docker --version
        
        echo "Docker info:"
        docker info --format "{{.ServerVersion}}"
        
        echo "Pre-pulling common test containers..."
        docker pull postgres:latest || echo "Failed to pull postgres, will try during test"
        docker pull testcontainers/ryuk:latest || echo "Failed to pull ryuk, will try during test"
        docker pull mysql:latest || echo "Failed to pull mysql, will try during test"
        docker pull redis:alpine || echo "Failed to pull redis, will try during test"
        
        echo "DOCKER_HOST=unix:///var/run/docker.sock" >> gradle.properties
        echo "TESTCONTAINERS_RYUK_DISABLED=true" >> gradle.properties
        echo "TESTCONTAINERS_CHECKS_DISABLE=true" >> gradle.properties
        echo "TESTCONTAINERS_HOST_OVERRIDE=localhost" >> gradle.properties
        
        export TESTCONTAINERS_RYUK_DISABLED=true
        export TESTCONTAINERS_CHECKS_DISABLE=true
        export TESTCONTAINERS_HOST_OVERRIDE=localhost
        
        echo "=== Docker Environment Setup Complete ==="
    """.trimIndent()

    fun setupDaggerEnvironment() = """
        echo "=== Setting up Dagger Environment ==="
        
        echo "Configuring Dagger-specific CI properties..."
        
        echo "kapt.include.compile.classpath=false" >> gradle.properties
        echo "kapt.incremental.apt=false" >> gradle.properties
        echo "kapt.use.worker.api=false" >> gradle.properties
        echo "kapt.incremental.apt.keep.annotation.file=false" >> gradle.properties
        echo "kapt.verbose=true" >> gradle.properties
        
        echo "org.gradle.parallel=false" >> gradle.properties
        echo "org.gradle.daemon=false" >> gradle.properties
        echo "org.gradle.configureondemand=false" >> gradle.properties
        echo "org.gradle.caching=false" >> gradle.properties
        
        echo "DAGGER_CI_MODE=true" >> gradle.properties
        
        echo "✓ Dagger environment configured for CI"
    """.trimIndent()

    fun configureDaggerAnnotationProcessing() = """
        echo "=== Configuring Dagger Annotation Processing ==="
        
        for module_dir in dagger koin kodein hilt; do
            if [ -d "${'$'}module_dir" ]; then
                echo "Found ${'$'}module_dir module, checking Dagger configuration..."
                
                build_file=""
                if [ -f "${'$'}module_dir/build.gradle.kts" ]; then
                    build_file="${'$'}module_dir/build.gradle.kts"
                elif [ -f "${'$'}module_dir/build.gradle" ]; then
                    build_file="${'$'}module_dir/build.gradle"
                fi
                
                if [ -n "${'$'}build_file" ]; then
                    echo "Checking ${'$'}build_file for Dagger configuration..."
                    
                    if [ "${'$'}module_dir" = "dagger" ]; then
                        echo "Processing dagger module build file..."
                        
                        cp "${'$'}build_file" "${'$'}build_file.backup"
                        
                        if grep -q "dagger" "${'$'}build_file"; then
                            echo "✓ Dagger dependencies found in ${'$'}build_file"
                            
                            if ! grep -q "kotlin-kapt" "${'$'}build_file" && ! grep -q "id.*kapt" "${'$'}build_file"; then
                                echo "⚠ KAPT plugin not found, this may cause issues"
                                echo "Consider adding 'kotlin-kapt' plugin to ${'$'}build_file"
                            else
                                echo "✓ KAPT plugin found in ${'$'}build_file"
                            fi
                            
                            if ! grep -q "testImplementation.*dagger" "${'$'}build_file"; then
                                echo "⚠ Test Dagger dependencies may be missing"
                            fi
                            
                            if ! grep -q "kaptTest.*dagger-compiler" "${'$'}build_file" && ! grep -q "testAnnotationProcessor.*dagger-compiler" "${'$'}build_file"; then
                                echo "⚠ Test annotation processor may be missing"
                            fi
                        fi
                    fi
                    
                    echo "Build file analysis complete for ${'$'}module_dir"
                else
                    echo "No build file found in ${'$'}module_dir"
                fi
            fi
        done
        
        echo "=== Dagger Annotation Processing Configuration Complete ==="
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

    fun setupEnhancedGradleRepositories() = """
        echo "=== Setting up Enhanced Gradle Repositories ==="
        
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
        ${EAPScriptTemplates.repositoryConfiguration()}
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
        ${EAPScriptTemplates.repositoryConfiguration()}
    }
}
EOF
        fi
        
        cat > gradle-eap-init.gradle << 'EOF'
allprojects {
    repositories {
        ${EAPScriptTemplates.repositoryConfiguration()}
    }
}
EOF
        
        echo "✓ Enhanced Gradle repositories configuration completed"
    """.trimIndent()

    fun configureKotlinMultiplatform() = """
        echo "=== Configuring Kotlin Multiplatform ==="
        
        if [ -f "build.gradle.kts" ] && grep -q "kotlin.*multiplatform" build.gradle.kts; then
            echo "Kotlin Multiplatform project detected"
            
            if grep -q "org.jetbrains.compose" build.gradle.kts || grep -q "compose" build.gradle.kts; then
                echo "Compose Multiplatform detected, ensuring all required repositories are available"
                
                if ! grep -q "repositories {" build.gradle.kts; then
                    echo "Adding repositories block to build.gradle.kts"
                    sed -i '1i\repositories {\
    maven { url = uri("${EAPConfig.Repositories.KTOR_EAP}") }\
    maven { url = uri("${EAPConfig.Repositories.COMPOSE_DEV}") }\
    maven { url = uri("${EAPConfig.Repositories.GOOGLE_ANDROID}") }\
    google()\
    mavenCentral()\
    gradlePluginPortal()\
}\
' build.gradle.kts
                else
                    echo "Repositories block exists, ensuring Google repository is present"
                    if ! grep -q "google()" build.gradle.kts && ! grep -q "${EAPConfig.Repositories.GOOGLE_ANDROID}" build.gradle.kts; then
                        sed -i '/repositories {/a\    maven { url = uri("${EAPConfig.Repositories.GOOGLE_ANDROID}") }\
    google()' build.gradle.kts
                    fi
                fi
                
                echo "✓ Compose Multiplatform repositories configured"
            fi
            
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

interface ExternalEAPSampleConfig {
    val projectName: String
    fun createEAPBuildType(): BuildType
}

fun BuildType.addEAPSampleFailureConditions(
    sampleName: String,
    specialHandling: List<SpecialHandling> = emptyList()
) {
    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "BUILD FAILED"
            failureMessage = "Build failed for external sample $sampleName"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "FAILURE:"
            failureMessage = "Build failure detected in external sample $sampleName"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "IllegalStateException"
            failureMessage = "IllegalStateException detected in $sampleName - likely Dagger component generation issue"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "cannot be provided without an @Provides"
            failureMessage = "Dagger dependency injection error in $sampleName"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Component must be created"
            failureMessage = "Dagger component creation error in $sampleName"
            stopBuildOnFailure = true
        }

        if (DockerSupport.requiresDocker(specialHandling)) {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "DockerClientProviderStrategy"
                failureMessage = "Docker client provider failed in $sampleName - Docker may not be available"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "Could not find a valid Docker environment"
                failureMessage = "Docker environment not found for $sampleName sample"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "TestcontainersException"
                failureMessage = "Testcontainers exception in $sampleName sample"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "Cannot connect to the Docker daemon"
                failureMessage = "Cannot connect to Docker daemon for $sampleName sample"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 35
        } else if (specialHandling.contains(SpecialHandling.KOTLIN_MULTIPLATFORM)) {
            executionTimeoutMin = 45
        } else if (DaggerSupport.requiresDagger(specialHandling)) {
            executionTimeoutMin = 40
        } else {
            executionTimeoutMin = 30
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
        id("KtorExternalEAPSample_${projectName.replace('-', '_').replace('.', '_')}")
        name = "EAP External Sample: $projectName"
        description = "Validate $projectName against EAP versions of Ktor with enhanced configuration preservation"

        vcs {
            root(vcsRoot)
        }

        requirements {
            agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)

            if (DockerSupport.requiresDocker(specialHandling)) {
                contains("docker.server.version", "")
                contains("docker.daemon.available", "true")
            }
        }

        params {
            defaultGradleParams()
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_COMPILER_PLUGIN_VERSION%")
            param("env.PROJECT_NAME", projectName)
            param("env.BUILD_TYPE", buildType.name)

            if (DockerSupport.requiresDocker(specialHandling)) {
                param("env.DOCKER_HOST", "unix:///var/run/docker.sock")
                param("env.TESTCONTAINERS_RYUK_DISABLED", "true")
                param("env.TESTCONTAINERS_CHECKS_DISABLE", "true")
                param("env.TESTCONTAINERS_HOST_OVERRIDE", "localhost")
            }

            if (DaggerSupport.requiresDagger(specialHandling)) {
                param("env.DAGGER_CI_MODE", "true")
                param("kapt.include.compile.classpath", "false")
                param("kapt.incremental.apt", "false")
                param("kapt.use.worker.api", "false")
            }
        }

        steps {
            script {
                name = "Environment Debug"
                scriptContent = EAPScriptTemplates.buildCommonSetup()
            }

            when (buildType) {
                ExternalSampleBuildType.GRADLE -> {
                    with(EAPBuildSteps) {
                        gradleEAPBuild(specialHandling)
                    }
                }
                ExternalSampleBuildType.AMPER -> {
                    with(EAPBuildSteps) {
                        amperEAPBuild(specialHandling)
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
                        
                        for dir in dagger koin kodein hilt; do
                            if [ -d ".backup/${'$'}dir" ]; then
                                echo "Restoring ${'$'}dir module configuration"
                                for backup_file in .backup/${'$'}dir/*.original; do
                                    if [ -f "${'$'}backup_file" ]; then
                                        original_name=$(basename "${'$'}backup_file" .original)
                                        echo "Restoring ${'$'}dir/${'$'}original_name"
                                        cp "${'$'}backup_file" "${'$'}dir/${'$'}original_name"
                                    fi
                                done
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

        addEAPSampleFailureConditions(projectName, specialHandling)

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
    description =
        "Enhanced validation of external GitHub samples against EAP versions of Ktor with configuration preservation"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
        param("toml.comprehensive.handling", "true")
        param("configuration.preservation.enabled", "true")
        param("docker.testcontainers.support", "true")
        param("dagger.annotation.processing.support", "true")
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
    EAPSampleBuilder("ktor-arrow-example", VCSKtorArrowExample, versionResolver)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("ktor-ai-server", VCSKtorAiServer, versionResolver)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("ktor-native-server", VCSKtorNativeServer, versionResolver)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM).build(),

    EAPSampleBuilder("ktor-koog-example", VCSKtorKoogExample, versionResolver)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM).build(),

    EAPSampleBuilder("full-stack-ktor-talk", VCSFullStackKtorTalk, versionResolver).build(),

    EAPSampleBuilder("ktor-config-example", VCSKtorConfigExample, versionResolver)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("ktor-workshop-2025", VCSKtorWorkshop2025, versionResolver)
        .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS).build(),

    EAPSampleBuilder("amper-ktor-sample", VCSAmperKtorSample, versionResolver)
        .withBuildType(ExternalSampleBuildType.AMPER)
        .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID).build(),

    EAPSampleBuilder("ktor-di-overview", VCSKtorDIOverview, versionResolver)
        .withSpecialHandling(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)
        .build(),

    EAPSampleBuilder("ktor-full-stack-real-world", VCSKtorFullStackRealWorld, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(
            SpecialHandling.KOTLIN_MULTIPLATFORM,
            SpecialHandling.ENHANCED_TOML_PATTERNS,
            SpecialHandling.DOCKER_TESTCONTAINERS
        ).build()
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
        executionTimeoutMin = 60
    }
}
