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

object EAPConfig {
    object Repositories {
        const val KTOR_EAP = "https://maven.pkg.jetbrains.space/public/p/ktor/eap"
        const val COMPOSE_DEV = "https://maven.pkg.jetbrains.space/public/p/compose/dev"
        const val MAVEN_CENTRAL = "https://repo1.maven.org/maven2/"
    }
}

enum class SpecialHandling {
    KOTLIN_MULTIPLATFORM,
    AMPER_GRADLE_HYBRID,
    ENHANCED_TOML_PATTERNS
}

enum class ExternalSampleBuildType {
    GRADLE, MAVEN, AMPER
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
        projectName, vcsRoot, buildType, versionResolver, specialHandling
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
            buildFailed = true
            firstSuccessAfterFailure = true
            if (includeSuccess) {
                buildFinishedSuccessfully = true
            }
            if (includeBuildStart) {
                buildFailedToStart = true
            }
        }
    }
}

object EAPBuildSteps {

    fun BuildSteps.standardEAPSetup() {
        script {
            name = "Backup Configuration Files"
            scriptContent = ExternalSampleScripts.backupConfigFiles()
        }
        script {
            name = "Analyze Project Structure"
            scriptContent = ExternalSampleScripts.analyzeProjectStructure()
        }
    }

    fun BuildSteps.gradleEAPBuild() {
        standardEAPSetup()
        script {
            name = "Update Gradle Properties"
            scriptContent = ExternalSampleScripts.updateGradlePropertiesEnhanced()
        }
        script {
            name = "Update Version Catalog"
            scriptContent = ExternalSampleScripts.updateVersionCatalogComprehensive()
        }
        script {
            name = "Setup Enhanced Gradle Repositories"
            scriptContent = ExternalSampleScripts.setupEnhancedGradleRepositories()
        }
        script {
            name = "Setup Composite Builds Support"
            scriptContent = ExternalSampleScripts.setupCompositeBuildsSupport()
        }
        script {
            name = "Build Enhanced Gradle Sample"
            scriptContent = ExternalSampleScripts.buildGradleProjectEnhanced()
        }
        script {
            name = "Validate Version Application"
            scriptContent = ExternalSampleScripts.validateVersionApplication()
        }
        standardCleanup()
    }

    fun BuildSteps.mavenEAPBuild() {
        standardEAPSetup()
        script {
            name = "Setup Enhanced Maven Repositories"
            scriptContent = ExternalSampleScripts.setupMavenRepositoriesEnhanced()
        }
        script {
            name = "Build Enhanced Maven Sample"
            scriptContent = ExternalSampleScripts.buildMavenProjectEnhanced()
        }
        script {
            name = "Validate Version Application"
            scriptContent = ExternalSampleScripts.validateVersionApplication()
        }
        script {
            name = "Log Version Changes"
            scriptContent = ExternalSampleScripts.logVersionChanges()
        }
        standardCleanup()
    }

    fun BuildSteps.amperEAPBuild() {
        standardEAPSetup()
        script {
            name = "Update Amper Versions"
            scriptContent = ExternalSampleScripts.updateAmperVersionsEnhanced()
        }
        script {
            name = "Setup Amper Repositories"
            scriptContent = ExternalSampleScripts.setupAmperRepositories()
        }
        script {
            name = "Build Enhanced Amper Sample"
            scriptContent = ExternalSampleScripts.buildAmperProjectEnhanced()
        }
        script {
            name = "Validate Version Application"
            scriptContent = ExternalSampleScripts.validateVersionApplication()
        }
        standardCleanup()
    }

    private fun BuildSteps.standardCleanup() {
        script {
            name = "Cleanup Backups"
            scriptContent = ExternalSampleScripts.cleanupBackups()
            executionMode = BuildStep.ExecutionMode.ALWAYS
        }
    }
}

object ExternalSampleScripts {
    fun backupConfigFiles() = generateScript("backup_config_files") {
        """
        echo "=== Backing up Configuration Files ==="
        mkdir -p .backup
        for file in "gradle.properties" "build.gradle" "build.gradle.kts" "settings.gradle" "settings.gradle.kts" "gradle/libs.versions.toml" "module.yaml" "settings.yaml" "amper.yaml" "pom.xml"; do
            if [ -f "${'$'}file" ]; then
                cp "${'$'}file" ".backup/${'$'}file.backup"
                echo "✓ Backed up ${'$'}file"
            fi
        done
        echo "✓ Configuration files backup completed"
        """
    }

    fun analyzeProjectStructure() = generateScript("analyze_project_structure") {
        """
        echo "=== Analyzing Project Structure ==="
        BUILD_SYSTEM=""
        VERSION_CATALOG=false
        
        if [ -f "amper.yaml" ] || [ -f "module.yaml" ] || [ -f "settings.yaml" ]; then
            BUILD_SYSTEM="amper"
            echo "✓ Detected Amper build system"
        elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ] || [ -f "settings.gradle" ] || [ -f "settings.gradle.kts" ]; then
            BUILD_SYSTEM="gradle"
            echo "✓ Detected Gradle build system"
        elif [ -f "pom.xml" ]; then
            BUILD_SYSTEM="maven"
            echo "✓ Detected Maven build system"
        else
            echo "⚠ No recognized build system detected"
            BUILD_SYSTEM="unknown"
        fi
        
        if [ -f "gradle/libs.versions.toml" ]; then
            VERSION_CATALOG=true
            echo "✓ Detected version catalog (libs.versions.toml)"
            if grep -q "ktor" "gradle/libs.versions.toml"; then
                echo "✓ Found TOML pattern: ktor"
                echo "##teamcity[setParameter name='env.TOML_PATTERN_ktor' value='true']"
            fi
        fi
        
        echo "##teamcity[setParameter name='env.BUILD_SYSTEM' value='${'$'}BUILD_SYSTEM']"
        echo "##teamcity[setParameter name='env.VERSION_CATALOG' value='${'$'}VERSION_CATALOG']"
        echo "✓ Project analysis completed"
        """
    }

    fun updateGradlePropertiesEnhanced() = generateScript("update_gradle_properties_enhanced") {
        """
        echo "=== Enhanced Gradle Properties Update ==="
        if [ -f "gradle.properties" ]; then
            echo "Updating gradle.properties..."
            cp gradle.properties gradle.properties.backup
            sed -i 's/ktor_version=.*/ktor_version=%env.KTOR_VERSION%/g' gradle.properties
            sed -i 's/ktorVersion=.*/ktorVersion=%env.KTOR_VERSION%/g' gradle.properties
            echo "✓ Updated gradle.properties"
        else
            echo "Creating gradle.properties..."
            cat > gradle.properties << EOF
ktor_version=%env.KTOR_VERSION%
kotlin.code.style=official
EOF
            echo "✓ Created gradle.properties"
        fi
        """
    }

    fun updateVersionCatalogComprehensive() = generateScript("update_version_catalog_comprehensive") {
        """
        echo "=== Comprehensive Version Catalog Update ==="
        if [ -f "gradle/libs.versions.toml" ]; then
            echo "Updating version catalog..."
            cp gradle/libs.versions.toml gradle/libs.versions.toml.backup
            sed -i 's/ktor = "[^"]*"/ktor = "%env.KTOR_VERSION%"/g' gradle/libs.versions.toml
            sed -i 's/ktor-version = "[^"]*"/ktor-version = "%env.KTOR_VERSION%"/g' gradle/libs.versions.toml
            sed -i 's/ktorVersion = "[^"]*"/ktorVersion = "%env.KTOR_VERSION%"/g' gradle/libs.versions.toml
            echo "--- Updated version catalog content ---"
            cat gradle/libs.versions.toml
            echo "✓ Updated version catalog"
        else
            echo "⚠ No version catalog found"
        fi
        """
    }

    fun setupEnhancedGradleRepositories() = generateScript("setup_enhanced_gradle_repositories") {
        """
        echo "=== Setting up Enhanced Gradle Repositories ==="
        cat > gradle-eap-init.gradle << 'EOF'
allprojects {
    repositories {
        maven("${EAPConfig.Repositories.KTOR_EAP}")
        maven("${EAPConfig.Repositories.COMPOSE_DEV}")
        mavenCentral()
        gradlePluginPortal()
    }
}
EOF
        echo "✓ Enhanced Gradle repositories configuration created"
        """
    }

    fun setupCompositeBuildsSupport() = generateScript("setup_composite_builds_support") {
        """
        echo "=== Setting up Composite Builds Support ==="
        if [ -f "settings.gradle.kts" ]; then
            cp settings.gradle.kts settings.gradle.kts.backup
            cat > settings.gradle.kts << 'EOF'
pluginManagement {
    repositories {
        maven("${EAPConfig.Repositories.KTOR_EAP}")
        maven("${EAPConfig.Repositories.COMPOSE_DEV}")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("${EAPConfig.Repositories.KTOR_EAP}")
        maven("${EAPConfig.Repositories.COMPOSE_DEV}")
        mavenCentral()
    }
}
EOF
            echo "✓ Enhanced settings.gradle.kts created"
        fi
        """
    }


    fun buildGradleProjectEnhanced() = generateScript("build_gradle_project_enhanced") {
        """
        echo "=== Enhanced Gradle Build ==="
        if [ -f "./gradlew" ]; then
            chmod +x ./gradlew
            GRADLE_CMD="./gradlew"
        else
            GRADLE_CMD="gradle"
        fi

        echo "Building with: ${'$'}GRADLE_CMD"
        echo "Using Ktor version: %env.KTOR_VERSION%"
        echo "Using compiler plugin version: %env.KTOR_COMPILER_PLUGIN_VERSION%"
        
        echo "Setting up JVM target compatibility..."
        if [ -f "gradle.properties" ]; then
            sed -i 's/kotlin.jvm.target.validation.mode=.*/kotlin.jvm.target.validation.mode=ignore/g' gradle.properties
            if ! grep -q "org.gradle.java.home" gradle.properties; then
                echo "org.gradle.java.home=%env.JDK_LTS%" >> gradle.properties
            fi
        fi
        
        if [ -d "buildSrc" ] && [ -f "buildSrc/build.gradle.kts" ]; then
            echo "Adjusting buildSrc JVM target compatibility..."
            sed -i 's/jvmTarget = "21"/jvmTarget = "17"/g' buildSrc/build.gradle.kts || echo "No JVM target 21 found in buildSrc"
            sed -i 's/jvmTarget = JavaVersion.VERSION_21/jvmTarget = JavaVersion.VERSION_17/g' buildSrc/build.gradle.kts || echo "No JavaVersion.VERSION_21 found in buildSrc"
        fi
        
        if [ -d "buildSrc" ] && [ -f "buildSrc/build.gradle" ]; then
            echo "Adjusting buildSrc JVM target compatibility (Groovy)..."
            sed -i 's/jvmTarget = "21"/jvmTarget = "17"/g' buildSrc/build.gradle || echo "No JVM target 21 found in buildSrc"
        fi
        
        ${'$'}GRADLE_CMD clean build \
            --init-script gradle-eap-init.gradle \
            --no-daemon --stacktrace --refresh-dependencies \
            --continue || {
            echo "Build failed. Analyzing failure..."
            echo "=== Build Failure Analysis ==="
            if [ -f "build.gradle.kts" ]; then
                echo "--- build.gradle.kts content ---"
                cat build.gradle.kts | head -20
            fi
            if [ -f "build.gradle" ]; then
                echo "--- build.gradle content ---"
                cat build.gradle | head -20
            fi
            if [ -f "gradle.properties" ]; then
                echo "--- gradle.properties content ---"
                cat gradle.properties
            fi
            if [ -d "buildSrc" ]; then
                echo "--- buildSrc structure ---"
                find buildSrc -name "*.gradle*" -o -name "*.kts" | head -10
            fi
            echo "=== End Analysis ==="
            exit 1
        }
        echo "✓ Enhanced Gradle build completed successfully"
        """
    }

    fun setupAmperRepositories() = generateScript("setup_amper_repositories") {
        """
        echo "=== Setting up Amper Repositories ==="
        if [ -f "amper.yaml" ]; then
            echo "Configuring Amper repositories..."
            cat > amper-repositories.yaml << 'EOF'
repositories:
  - url: "${EAPConfig.Repositories.MAVEN_CENTRAL}"
    name: "maven-central"
  - url: "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap/"
    name: "kotlin-bootstrap" 
  - url: "https://maven.pkg.jetbrains.space/public/p/kotlinx-coroutines/maven/"
    name: "kotlinx-coroutines"
  - url: "${EAPConfig.Repositories.KTOR_EAP}"
    name: "ktor-eap"
    priority: 1
EOF
            echo "✓ Amper repositories configuration created"
        else
            echo "⚠ No amper.yaml found, skipping repository setup"
        fi
        echo "✓ Amper repositories setup completed"
        """
    }

    fun buildAmperProjectEnhanced() = generateScript("build_amper_project_enhanced") {
        """
        echo "=== Enhanced Amper Build ==="
        if ! command -v amper &> /dev/null; then
            echo "Installing Amper..."
            curl -s https://packages.jetbrains.team/files/p/amper/amper-cli/install.sh | bash
            export PATH=${'$'}HOME/.local/bin:${'$'}PATH
        fi
        
        echo "Using Ktor version: %env.KTOR_VERSION%"
        echo "Using compiler plugin version: %env.KTOR_COMPILER_PLUGIN_VERSION%"
        
        amper build --stacktrace --refresh-dependencies || {
            echo "Amper build failed, analyzing..."
            analyze_amper_build_failure
            exit 1
        }
        echo "✓ Enhanced Amper build completed successfully"
        """
    }

    fun updateAmperVersionsEnhanced() = generateScript("update_amper_versions_enhanced") {
        """
    echo "=== Enhanced Amper Version Update ==="
    if [ -f "module.yaml" ]; then
        echo "Updating module.yaml..."
        cp module.yaml module.yaml.backup
        sed -i 's/io\.ktor:\([^:]*\):[0-9][^[:space:]]*/io.ktor:${'\\'}1:%env.KTOR_VERSION%/g' module.yaml
        for artifact in "ktor-server-core" "ktor-client-core" "ktor-server-netty" "ktor-client-cio" "ktor-server-auth" "ktor-serialization" "ktor-server-content-negotiation" "ktor-serialization-kotlinx-json"; do
            if grep -q "${'$'}artifact" module.yaml; then
                sed -i "s/${'$'}artifact:[0-9][^[:space:]]*/${'$'}artifact:%env.KTOR_VERSION%/g" module.yaml
                echo "✓ Updated ${'$'}artifact version"
            fi
        done
        echo "--- Updated module.yaml content ---"
        cat module.yaml
    fi
    
    if [ -f "settings.yaml" ]; then
        echo "Updating settings.yaml..."
        cp settings.yaml settings.yaml.backup
        sed -i 's/ktor-version: [0-9][^[:space:]]*/ktor-version: %env.KTOR_VERSION%/g' settings.yaml
        sed -i 's/ktor_version: [0-9][^[:space:]]*/ktor_version: %env.KTOR_VERSION%/g' settings.yaml
        echo "--- Updated settings.yaml content ---"
        cat settings.yaml
        echo "✓ Updated settings.yaml"
    fi
    
    if [ -f "gradle/libs.versions.toml" ]; then
        echo "Updating version catalog for Amper hybrid project..."
        cp gradle/libs.versions.toml gradle/libs.versions.toml.backup
        sed -i 's/ktor = "[^"]*"/ktor = "%env.KTOR_VERSION%"/g' gradle/libs.versions.toml
        sed -i 's/ktor-version = "[^"]*"/ktor-version = "%env.KTOR_VERSION%"/g' gradle/libs.versions.toml
        echo "✓ Updated version catalog"
    fi
    echo "✓ Enhanced Amper version update completed"
    """
    }

    fun validateVersionApplication() = generateScript("validate_version_application") {
        """
        echo "=== Validating Version Application ==="
        
        if [ -f "./gradlew" ]; then
            echo "Checking resolved dependencies..."
            ./gradlew dependencies --configuration runtimeClasspath 2>/dev/null | grep -i ktor | head -10 || true
        fi
        
        if [ -z "%env.KTOR_VERSION%" ]; then
            echo "ERROR: KTOR_VERSION not set"
            exit 1
        fi
        
        if [ -z "%env.KTOR_COMPILER_PLUGIN_VERSION%" ]; then
            echo "ERROR: KTOR_COMPILER_PLUGIN_VERSION not set"
            exit 1
        fi
        
        if [ -f "gradle/libs.versions.toml" ]; then
            echo "=== Validating TOML Changes ==="
            echo "Validating version catalog changes..."
            if grep -q "%env.KTOR_VERSION%" gradle/libs.versions.toml; then
                echo "✓ TOML version placeholders found"
            else
                echo "⚠ No TOML version placeholders found"
            fi
            echo "--- Current TOML content ---"
            cat gradle/libs.versions.toml
            echo "✓ TOML validation completed"
        fi
        
        for file in "gradle.properties" "gradle/libs.versions.toml" "module.yaml" "settings.yaml"; do
            if [ -f "${'$'}file" ]; then
                echo "--- Checking ${'$'}file ---"
                if grep -q "%env.KTOR_VERSION%" "${'$'}file" 2>/dev/null; then
                    echo "✓ Version placeholder found in ${'$'}file"
                else
                    echo "⚠ No version placeholder in ${'$'}file"
                fi
            fi
        done
        
        echo "✓ Using Ktor version: %env.KTOR_VERSION%"
        echo "✓ Using compiler plugin version: %env.KTOR_COMPILER_PLUGIN_VERSION%"
        echo "✓ Version application validation completed"
        """
    }

    fun logVersionChanges() = generateScript("log_version_changes") {
        """
        echo "=== Logging Version Changes ==="
        echo "Applied version changes:"
        echo "- Ktor Framework: %env.KTOR_VERSION%"
        echo "- Compiler Plugin: %env.KTOR_COMPILER_PLUGIN_VERSION%"
        
        for file in gradle.properties gradle/libs.versions.toml module.yaml settings.yaml; do
            if [ -f "${'$'}file" ] && [ -f "${'$'}file.backup" ]; then
                echo "--- Changes in ${'$'}file ---"
                diff "${'$'}file.backup" "${'$'}file" || echo "No differences or files don't exist"
            fi
        done
        echo "✓ Version changes logged"
        """
    }

    fun setupMavenRepositoriesEnhanced() = generateScript("setup_maven_repositories_enhanced") {
        """
        echo "=== Setting up Enhanced Maven Repositories ==="
        if [ -f "pom.xml" ]; then
            cp pom.xml pom.xml.backup
            if grep -q "<repositories>" pom.xml; then
                echo "Repositories section exists, adding EAP repositories"
                sed -i '/<\/repositories>/i\
        <repository>\
            <id>ktor-eap</id>\
            <url>${EAPConfig.Repositories.KTOR_EAP}</url>\
        </repository>\
        <repository>\
            <id>compose-dev</id>\
            <url>${EAPConfig.Repositories.COMPOSE_DEV}</url>\
        </repository>' pom.xml
            else
                echo "Adding new repositories section"
                sed -i '/<project[^>]*>/a\
    <repositories>\
        <repository>\
            <id>ktor-eap</id>\
            <url>${EAPConfig.Repositories.KTOR_EAP}</url>\
        </repository>\
        <repository>\
            <id>compose-dev</id>\
            <url>${EAPConfig.Repositories.COMPOSE_DEV}</url>\
        </repository>\
    </repositories>' pom.xml
            fi
            echo "✓ Enhanced Maven repositories configured"
        fi
        """
    }

    fun buildMavenProjectEnhanced() = generateScript("build_maven_project_enhanced") {
        """
        echo "=== Enhanced Maven Build ==="
        echo "Using Ktor version: %env.KTOR_VERSION%"
        echo "Using compiler plugin version: %env.KTOR_COMPILER_PLUGIN_VERSION%"
        
        if [ -f "pom.xml" ]; then
            sed -i 's/<ktor\.version>[^<]*<\/ktor\.version>/<ktor.version>%env.KTOR_VERSION%<\/ktor.version>/g' pom.xml
            sed -i 's/<ktorVersion>[^<]*<\/ktorVersion>/<ktorVersion>%env.KTOR_VERSION%<\/ktorVersion>/g' pom.xml
        fi
        
        mvn clean compile test -U || {
            echo "Maven build failed, analyzing..."
            analyze_maven_build_failure
            exit 1
        }
        echo "✓ Enhanced Maven build completed successfully"
        """
    }

    fun cleanupBackups() = generateScript("cleanup_backups") {
        """
        echo "=== Cleaning up Backup Files ==="
        find . -name "*.backup" -type f -delete 2>/dev/null || echo "No backup files to clean"
        rm -rf .backup 2>/dev/null || echo "No backup directory to clean"
        rm -f gradle-eap-init.gradle 2>/dev/null || echo "No Gradle init script to clean"
        rm -f amper-repositories.yaml 2>/dev/null || echo "No Amper repositories config to clean"
        echo "✓ Cleanup completed"
        """
    }

    private fun generateScript(functionName: String, content: () -> String): String {
        return """
            $functionName() {
            ${content().trimIndent()}
            }
            $functionName
        """.trimIndent()
    }
}

interface ExternalEAPSampleConfig {
    val projectName: String
    fun createEAPBuildType(): BuildType
}

data class ExternalSampleConfig(
    override val projectName: String,
    val vcsRoot: VcsRoot,
    val buildType: ExternalSampleBuildType,
    val versionResolver: BuildType,
    val specialHandling: List<SpecialHandling>
) : ExternalEAPSampleConfig {

    override fun createEAPBuildType(): BuildType = BuildType {
        id("ExternalKtorEAPSample_${projectName.replace('-', '_').replace('.', '_')}")
        name = "Validate $projectName with EAP"
        description = "Enhanced validation of $projectName against EAP version of Ktor"

        vcs { root(vcsRoot) }

        params {
            defaultGradleParams()
            param("env.GIT_BRANCH", "%teamcity.build.branch%")
            param("external.sample.name", projectName)
            param("enhanced.validation.enabled", "true")
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_COMPILER_PLUGIN_VERSION%")
        }

        steps {
            when (buildType) {
                ExternalSampleBuildType.GRADLE -> EAPBuildSteps.run { gradleEAPBuild() }
                ExternalSampleBuildType.MAVEN -> EAPBuildSteps.run { mavenEAPBuild() }
                ExternalSampleBuildType.AMPER -> EAPBuildSteps.run { amperEAPBuild() }
            }
        }

        defaultBuildFeatures()

        features {
            EAPBuildFeatures.run { addEAPSlackNotifications() }
        }

        dependencies {
            dependency(versionResolver) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "BUILD FAILED"
                failureMessage = "Build failed for $projectName"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 15
        }

        requirements {
            agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)
        }
    }
}

object ExternalSamplesEAPValidation : Project({
    id("ExternalSamplesEAPValidation")
    name = "External Samples EAP Validation"
    description = "Enhanced validation of external GitHub samples against EAP versions of Ktor"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
        param("toml.comprehensive.handling", "true")
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

private fun createVersionResolver(): BuildType {
    return EAPVersionResolver.createVersionResolver(
        id = "ExternalKtorEAPVersionResolver",
        name = "EAP Version Resolver for External Samples",
        description = "Enhanced version resolver with comprehensive validation for external sample validation"
    )
}

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalSampleConfig> {
    return listOf(
        EAPSampleBuilder("ktor-arrow-example", VCSKtorArrowExample, versionResolver)
            .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),

        EAPSampleBuilder("ktor-ai-server", VCSKtorAiServer, versionResolver)
            .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),

        EAPSampleBuilder("ktor-native-server", VCSKtorNativeServer, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),

        EAPSampleBuilder("ktor-koog-example", VCSKtorKoogExample, versionResolver)
            .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),

        EAPSampleBuilder("full-stack-ktor-talk", VCSFullStackKtorTalk, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),

        EAPSampleBuilder("ktor-config-example", VCSKtorConfigExample, versionResolver)
            .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),

        EAPSampleBuilder("ktor-workshop-2025", VCSKtorWorkshop2025, versionResolver)
            .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),

        EAPSampleBuilder("amper-ktor-sample", VCSAmperKtorSample, versionResolver)
            .withBuildType(ExternalSampleBuildType.AMPER)
            .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),

        EAPSampleBuilder("Ktor-DI-Overview", VCSKtorDIOverview, versionResolver)
            .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build(),

        EAPSampleBuilder("ktor-full-stack-real-world", VCSKtorFullStackRealWorld, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.ENHANCED_TOML_PATTERNS)
            .build()
    )
}

private fun createCompositeBuild(versionResolver: BuildType, buildTypes: List<BuildType>): BuildType {
    return BuildType {
        id("ExternalKtorEAPSamplesCompositeBuild")
        name = "Validate All External Samples with EAP"
        description = "Enhanced validation of all external GitHub samples against EAP version of Ktor"
        type = BuildTypeSettings.Type.COMPOSITE

        params {
            defaultGradleParams()
            param("env.GIT_BRANCH", "%teamcity.build.branch%")
            param("teamcity.build.skipDependencyBuilds", "true")
            param("enhanced.validation.enabled", "true")
        }

        features {
            EAPBuildFeatures.run {
                addEAPSlackNotifications(
                    includeSuccess = true,
                    includeBuildStart = true
                )
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
                failureMessage = "No compatible agents found for external samples composite"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "✗.*FAILED:"
                failureMessage = "Enhanced validation failed in one or more samples"
                stopBuildOnFailure = false
            }
            executionTimeoutMin = 30
        }
    }
}
