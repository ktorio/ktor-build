package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.Agents.ANY
import subprojects.build.defaultBuildFeatures
import subprojects.build.defaultGradleParams

object EAPConfig {
    object Repositories {
        const val KTOR_EAP = "https://maven.pkg.jetbrains.space/public/p/ktor/eap"
        const val COMPOSE_DEV = "https://maven.pkg.jetbrains.space/public/p/compose/dev"
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
                sendTo = "#ktor-external-samples-eap"
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

object EAPScriptTemplates {
    fun repositoryConfiguration() = """
        maven("${EAPConfig.Repositories.KTOR_EAP}")
        maven("${EAPConfig.Repositories.COMPOSE_DEV}")
        mavenCentral()
        gradlePluginPortal()
    """.trimIndent()

    fun mavenRepositoryXml() = """
        <!-- EAP Repositories -->
        <repository>
            <id>ktor-eap</id>
            <url>${EAPConfig.Repositories.KTOR_EAP}</url>
        </repository>
        <repository>
            <id>compose-dev</id>
            <url>${EAPConfig.Repositories.COMPOSE_DEV}</url>
        </repository>
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
    }

    fun BuildSteps.gradleEAPBuild(specialHandling: List<SpecialHandling> = emptyList()) {
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
                name = "Handle Amper-Gradle Hybrid"
                scriptContent = ExternalSampleScripts.handleAmperGradleHybrid()
            }
        }

        script {
            name = "Setup Composite Builds Support"
            scriptContent = ExternalSampleScripts.setupCompositeBuildsSupport()
        }
        script {
            name = "Build Gradle Project"
            scriptContent = ExternalSampleScripts.buildGradleProjectEnhanced(specialHandling)
        }
    }

    fun BuildSteps.mavenEAPBuild() {
        script {
            name = "Setup Enhanced Maven Repositories"
            scriptContent = ExternalSampleScripts.setupMavenRepositoriesEnhanced()
        }
        script {
            name = "Build Maven Project"
            scriptContent = ExternalSampleScripts.buildMavenProjectEnhanced()
        }
    }

    fun BuildSteps.amperEAPBuild() {
        script {
            name = "Setup Amper Repositories"
            scriptContent = ExternalSampleScripts.setupAmperRepositories()
        }
        script {
            name = "Update Amper Versions"
            scriptContent = ExternalSampleScripts.updateAmperVersionsEnhanced()
        }
        script {
            name = "Build Amper Project"
            scriptContent = ExternalSampleScripts.buildAmperProjectEnhanced()
        }
    }
}

object ExternalSampleScripts {
    fun backupConfigFiles() = generateScript("backup_config_files") {
        """
        echo "=== Backing up configuration files ==="
        find . -name "settings.gradle.kts" -exec cp {} {}.backup \;
        find . -name "build.gradle.kts" -exec cp {} {}.backup \;
        find . -name "gradle.properties" -exec cp {} {}.backup \;
        find . -name "libs.versions.toml" -exec cp {} {}.backup \;
        find . -name "pom.xml" -exec cp {} {}.backup \;
        find . -name "module.yaml" -exec cp {} {}.backup \;
        echo "✓ Configuration files backed up"
        """
    }

    fun analyzeProjectStructure(specialHandling: List<SpecialHandling> = emptyList()) =
        generateScript("analyze_project_structure") {
            """
        echo "=== Analyzing project structure ==="
        echo "Root directory contents:"
        ls -la
        echo ""
        
        if [ -f "settings.gradle.kts" ] || [ -f "build.gradle.kts" ]; then
            echo "✓ Gradle project detected"
            PROJECT_TYPE="gradle"
        elif [ -f "pom.xml" ]; then
            echo "✓ Maven project detected"
            PROJECT_TYPE="maven"
        elif [ -f "module.yaml" ]; then
            echo "✓ Amper project detected"
            PROJECT_TYPE="amper"
        else
            echo "⚠ Unknown project type"
            PROJECT_TYPE="unknown"
        fi
        
        ${
                if (specialHandling.contains(SpecialHandling.KOTLIN_MULTIPLATFORM)) {
                    """
            echo "🔍 Checking for Kotlin Multiplatform..."
            if grep -r "kotlin.*multiplatform" . --include="*.gradle.kts" --include="*.gradle"; then
                echo "✓ Kotlin Multiplatform detected"
            fi
            """
                } else ""
            }
        
        ${
                if (specialHandling.contains(SpecialHandling.AMPER_GRADLE_HYBRID)) {
                    """
            echo "🔍 Checking for Amper-Gradle hybrid..."
            if [ -f "module.yaml" ] && [ -f "build.gradle.kts" ]; then
                echo "✓ Amper-Gradle hybrid detected"
            fi
            """
                } else ""
            }
        
        echo "Project type: ${'$'}PROJECT_TYPE"
        """
        }

    fun updateGradlePropertiesEnhanced() = generateScript("update_gradle_properties_enhanced") {
        """
        echo "=== Updating gradle.properties ==="
        ${EAPScriptTemplates.buildCommonSetup()}
        
        if [ -f "gradle.properties" ]; then
            echo "Found existing gradle.properties, preserving existing configuration"
            
            if grep -q "ktor.version" gradle.properties; then
                echo "Updating existing ktor.version"
                sed -i.bak "s/ktor.version=.*/ktor.version=${'$'}{KTOR_VERSION}/" gradle.properties
            else
                echo "Adding ktor.version to existing file"
                echo "" >> gradle.properties
                echo "# EAP Version Configuration" >> gradle.properties
                echo "ktor.version=${'$'}{KTOR_VERSION}" >> gradle.properties
            fi
            
            if grep -q "ktor_compiler_plugin.version" gradle.properties; then
                echo "Updating existing ktor_compiler_plugin.version"
                sed -i.bak "s/ktor_compiler_plugin.version=.*/ktor_compiler_plugin.version=${'$'}{KTOR_COMPILER_PLUGIN_VERSION}/" gradle.properties
            else
                echo "Adding ktor_compiler_plugin.version to existing file"
                echo "ktor_compiler_plugin.version=${'$'}{KTOR_COMPILER_PLUGIN_VERSION}" >> gradle.properties
            fi
        else
            echo "Creating new gradle.properties with EAP configuration"
            cat > gradle.properties << EOF
ktor.version=${'$'}{KTOR_VERSION}
ktor_compiler_plugin.version=${'$'}{KTOR_COMPILER_PLUGIN_VERSION}
EOF
        fi
        
        echo "✓ gradle.properties updated"
        """
    }

    fun updateVersionCatalogComprehensive(specialHandling: List<SpecialHandling> = emptyList()) =
        generateScript("update_version_catalog_comprehensive") {
            """
        echo "=== Updating Version Catalog (Comprehensive) ==="
        
        CATALOG_FILES=$(find . -name "libs.versions.toml" -o -name "gradle/libs.versions.toml")
        
        if [ -n "${'$'}CATALOG_FILES" ]; then
            for CATALOG in ${'$'}CATALOG_FILES; do
                echo "Processing version catalog: ${'$'}CATALOG"
                
                if [ -f "${'$'}CATALOG" ]; then
                    echo "✓ Found version catalog, preserving existing structure"
                    
                    cp "${'$'}CATALOG" "${'$'}CATALOG.backup"
                    
                    if grep -q "ktor.*=" "${'$'}CATALOG"; then
                        echo "Updating existing Ktor version entries"
                        sed -i.tmp -E 's/ktor[[:space:]]*=[[:space:]]*"[^"]*"/ktor = "'${'$'}{KTOR_VERSION}'"/' "${'$'}CATALOG"
                    else
                        echo "Adding Ktor version to existing catalog"
                        if grep -q "\[versions\]" "${'$'}CATALOG"; then
                            sed -i.tmp '/\[versions\]/a\
ktor = "'${'$'}{KTOR_VERSION}'"' "${'$'}CATALOG"
                        else
                            echo "" >> "${'$'}CATALOG"
                            echo "[versions]" >> "${'$'}CATALOG"
                            echo 'ktor = "'${'$'}{KTOR_VERSION}'"' >> "${'$'}CATALOG"
                        fi
                    fi
                    
                    ${
                if (specialHandling.contains(SpecialHandling.ENHANCED_TOML_PATTERNS)) {
                    """
                        echo "Applying enhanced TOML patterns"
                        sed -i.tmp -E 's/ktor-[a-zA-Z-]*[[:space:]]*=[[:space:]]*{[^}]*version\.ref[[:space:]]*=[[:space:]]*"[^"]*"/ktor-core = { version.ref = "ktor" }/' "${'$'}CATALOG"
                        """
                } else ""
            }
                    
                    rm -f "${'$'}CATALOG.tmp"
                    echo "✓ Version catalog updated: ${'$'}CATALOG"
                fi
            done
        else
            echo "ℹ No version catalogs found - this is normal for projects not using version catalogs"
        fi
        """
        }

    fun setupEnhancedGradleRepositories() = generateScript("setup_enhanced_gradle_repositories") {
        """
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
                    /repositories.*{/ && pm==1 {repos=1; print; print "        maven(\"${EAPConfig.Repositories.KTOR_EAP}\")"; print "        maven(\"${EAPConfig.Repositories.COMPOSE_DEV}\")"; next}
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
        """
    }

    fun setupCompositeBuildsSupport() = generateScript("setup_composite_builds_support") {
        """
        echo "=== Setting up Composite Builds Support ==="
        
        if [ -f "settings.gradle.kts" ]; then
            if grep -q "includeBuild" settings.gradle.kts; then
                echo "✓ Composite builds detected, configuration preserved"
                
                for BUILD_DIR in $(grep -oP '(?<=includeBuild\(")[^"]*' settings.gradle.kts 2>/dev/null || true); do
                    if [ -d "${'$'}BUILD_DIR" ] && [ -f "${'$'}BUILD_DIR/settings.gradle.kts" ]; then
                        echo "Configuring EAP repositories for included build: ${'$'}BUILD_DIR"
                        if ! grep -q "${EAPConfig.Repositories.KTOR_EAP}" "${'$'}BUILD_DIR/settings.gradle.kts"; then
                            cp "${'$'}BUILD_DIR/settings.gradle.kts" "${'$'}BUILD_DIR/settings.gradle.kts.backup"
                            cat > "${'$'}BUILD_DIR/settings.gradle.kts.tmp" << 'EOF'
pluginManagement {
    repositories {
        ${EAPScriptTemplates.repositoryConfiguration()}
    }
}

EOF
                            cat "${'$'}BUILD_DIR/settings.gradle.kts" >> "${'$'}BUILD_DIR/settings.gradle.kts.tmp"
                            mv "${'$'}BUILD_DIR/settings.gradle.kts.tmp" "${'$'}BUILD_DIR/settings.gradle.kts"
                        fi
                    fi
                done
            else
                echo "ℹ No composite builds detected"
            fi
        fi
        
        echo "✓ Composite builds support configured"
        """
    }

    fun configureKotlinMultiplatform() = generateScript("configure_kotlin_multiplatform") {
        """
        echo "=== Configuring Kotlin Multiplatform ==="
        
        find . -name "build.gradle.kts" -exec grep -l "kotlin.*multiplatform" {} \; | while read -r BUILD_FILE; do
            echo "Configuring multiplatform build file: ${'$'}BUILD_FILE"
            
            if ! grep -q "${EAPConfig.Repositories.KTOR_EAP}" "${'$'}BUILD_FILE"; then
                echo "Adding EAP repositories to ${'$'}BUILD_FILE"
                
                if ! grep -q "repositories.*{" "${'$'}BUILD_FILE"; then
                    sed -i.tmp '1i\
repositories {\
    ${EAPScriptTemplates.repositoryConfiguration()}\
}' "${'$'}BUILD_FILE"
                fi
            fi
        done
        
        echo "✓ Kotlin Multiplatform configuration completed"
        """
    }


    fun handleAmperGradleHybrid() = generateScript("handle_amper_gradle_hybrid") {
        """
    echo "=== Handling Amper-Gradle Hybrid Projects ==="
    
    if [ -f "module.yaml" ] && [ -f "build.gradle.kts" ]; then
        echo "✓ Amper-Gradle hybrid detected"
        
        if [ -f "module.yaml" ]; then
            echo "Backing up module.yaml"
            cp module.yaml module.yaml.backup
            
            if ! grep -q "ktor-eap" module.yaml; then
                echo "Adding EAP repositories to module.yaml"
                
                if grep -q "^repositories:" module.yaml; then
                    echo "Found existing repositories section, adding EAP repos"
                    sed -i.tmp '/^repositories:/a\\
  - url: "${EAPConfig.Repositories.KTOR_EAP}"\\
  - url: "${EAPConfig.Repositories.COMPOSE_DEV}"' module.yaml
                else
                    echo "No repositories section found, creating new one"
                    cat >> module.yaml << 'EOF'

repositories:
  - url: "${EAPConfig.Repositories.KTOR_EAP}"
  - url: "${EAPConfig.Repositories.COMPOSE_DEV}"
EOF
                fi
                
                if grep -q "^dependencies:" module.yaml; then
                    echo "Updating Ktor version in dependencies"
                    sed -i.tmp 's/io\.ktor:\([^:]*\):[^"'\'']*\)/io.ktor:\1:%ktor.eap.version%/g' module.yaml
                fi
                
                echo "✓ EAP configuration injected into module.yaml"
            else
                echo "✓ EAP repositories already configured"
            fi
        fi
        
        echo "✓ Amper-Gradle hybrid configuration completed"
    else
        echo "ℹ Not an Amper-Gradle hybrid project"
    fi
    """
    }

    fun buildGradleProjectEnhanced(specialHandling: List<SpecialHandling> = emptyList()) =
        generateScript("build_gradle_project_enhanced") {
            """
        echo "=== Building Gradle Project (Enhanced) ==="
        ${EAPScriptTemplates.buildCommonSetup()}
        
        GRADLE_OPTS="--init-script gradle-eap-init.gradle"
        
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
        
        ${
                if (specialHandling.contains(SpecialHandling.KOTLIN_MULTIPLATFORM)) {
                    """
            echo "Running multiplatform-specific tasks..."
            ./gradlew allTests ${'$'}GRADLE_OPTS || echo "⚠ Some multiplatform tests failed"
            """
                } else ""
            }
        
        echo "✓ Gradle build enhanced completed"
        """
        }


    fun setupAmperRepositories() = generateScript("setup_amper_repositories") {
        """
        echo "=== Setting up Amper Repositories ==="
        
        if [ -f "module.yaml" ]; then
            echo "✓ Amper project detected, configuring repositories"
            cp module.yaml module.yaml.backup
            cat > amper-settings.yaml << 'EOF'
repositories:
  - url: ${EAPConfig.Repositories.KTOR_EAP}
    name: ktor-eap
  - url: ${EAPConfig.Repositories.COMPOSE_DEV}
    name: compose-dev
  - url: https://repo1.maven.org/maven2/
    name: central
  - url: https://plugins.gradle.org/m2/
    name: gradle-plugins
EOF
            
            echo "✓ Amper repositories configured in amper-settings.yaml"
            if ! grep -q "repositories:" module.yaml; then
                cat >> module.yaml << 'EOF'

repositories:
  - url: ${EAPConfig.Repositories.KTOR_EAP}
  - url: ${EAPConfig.Repositories.COMPOSE_DEV}
EOF
                echo "✓ EAP repositories added to module.yaml"
            fi
        else
            echo "ℹ No module.yaml found, not an Amper project"
        fi
        """
    }

    fun updateAmperVersionsEnhanced() = generateScript("update_amper_versions_enhanced") {
        """
        echo "=== Updating Amper Versions (Enhanced) ==="
        
        if [ -f "module.yaml" ]; then
            echo "Updating Ktor versions in module.yaml while preserving configuration"
            cp module.yaml module.yaml.backup
            
            if grep -q "ktor.*version" module.yaml; then
                echo "Updating existing Ktor version reference"
                sed -i.bak "s/ktor.*version.*:.*/  ktor-version: ${'$'}{KTOR_VERSION}/" module.yaml
            else
                if grep -q "dependencies:" module.yaml; then
                    sed -i.bak '/dependencies:/i\
# EAP versions\
settings:\
  ktor-version: '"${'$'}{KTOR_VERSION}"'\
  ktor-compiler-plugin-version: '"${'$'}{KTOR_COMPILER_PLUGIN_VERSION}"'\
' module.yaml
                else
                    cat >> module.yaml << EOF

settings:
  ktor-version: ${'$'}{KTOR_VERSION}
  ktor-compiler-plugin-version: ${'$'}{KTOR_COMPILER_PLUGIN_VERSION}
EOF
                fi
            fi
            
            sed -i.bak "s/io.ktor:\([^:]*\):[0-9][^'\"]*['\"]$/io.ktor:\1:${'$'}{KTOR_VERSION}\"/" module.yaml
            
            echo "✓ Amper versions updated"
            echo "Updated module.yaml content:"
            cat module.yaml
        else
            echo "ℹ No module.yaml found, skipping Amper version updates"
        fi
        """
    }

    fun buildAmperProjectEnhanced() = generateScript("build_amper_project_enhanced") {
        """
        echo "=== Building Amper Project (Enhanced) ==="
        ${EAPScriptTemplates.buildCommonSetup()}
        
        if [ -f "module.yaml" ]; then
            echo "Building Amper project with EAP versions..."
            
            if ! command -v amper &> /dev/null; then
                echo "Amper command not found, attempting to install or use alternative build method"
                
                
                if [ -f "./gradlew" ]; then
                    echo "Found Gradle wrapper, using as fallback for Amper build"
                    
                    ./gradlew clean --init-script gradle-eap-init.gradle -Pktor.version=${'$'}{KTOR_VERSION}
                    if [ $? -ne 0 ]; then
                        echo "❌ Amper project clean failed"
                        exit 1
                    fi
                    
                    ./gradlew build --init-script gradle-eap-init.gradle -Pktor.version=${'$'}{KTOR_VERSION} --stacktrace
                    if [ $? -ne 0 ]; then
                        echo "❌ Amper project build failed"
                        exit 1
                    fi
                    
                    ./gradlew test --init-script gradle-eap-init.gradle -Pktor.version=${'$'}{KTOR_VERSION}
                    if [ $? -eq 0 ]; then
                        echo "✓ Amper project tests passed"
                    else
                        echo "⚠ Some Amper project tests failed"
                    fi
                    
                else
                    echo "❌ No build method available for Amper project"
                    echo "Neither 'amper' command nor './gradlew' found"
                    exit 1
                fi
            else
                echo "Using native amper command for build"
                
                export KTOR_VERSION=${'$'}{KTOR_VERSION}
                export KTOR_COMPILER_PLUGIN_VERSION=${'$'}{KTOR_COMPILER_PLUGIN_VERSION}
                
                amper clean
                if [ $? -ne 0 ]; then
                    echo "❌ Amper clean failed"
                    exit 1
                fi
                
                amper build
                if [ $? -ne 0 ]; then
                    echo "❌ Amper build failed"
                    exit 1
                fi
                
                amper test
                if [ $? -eq 0 ]; then
                    echo "✓ Amper tests passed"
                else
                    echo "⚠ Some Amper tests failed, continuing"
                fi
            fi
            
            echo "✓ Amper build completed"
        else
            echo "ℹ No module.yaml found, skipping Amper build"
        fi
        """
    }

    fun setupMavenRepositoriesEnhanced() = generateScript("setup_maven_repositories_enhanced") {
        """
        echo "=== Setting up Maven Repositories (Enhanced) ==="
        
        if [ -f "pom.xml" ]; then
            echo "Found pom.xml, preserving existing configuration"
            
            if grep -q "ktor-eap" pom.xml; then
                echo "✓ EAP repositories already configured"
                exit 0
            fi
            
            cp pom.xml pom.xml.backup
            
            if grep -q "<repositories>" pom.xml; then
                echo "Adding EAP repositories to existing repositories section"
                sed -i '/<\/repositories>/i\
        ${EAPScriptTemplates.mavenRepositoryXml()}' pom.xml
            else
                echo "Adding new repositories section"
                sed -i '/<project[^>]*>/a\
    <repositories>\
        ${EAPScriptTemplates.mavenRepositoryXml()}\
    </repositories>' pom.xml
            fi
            
            echo "✓ Maven repositories enhanced configuration completed"
        else
            echo "ℹ No pom.xml found, not a Maven project"
        fi
        """
    }

    fun buildMavenProjectEnhanced() = generateScript("build_maven_project_enhanced") {
        """
        echo "=== Building Maven Project (Enhanced) ==="
        ${EAPScriptTemplates.buildCommonSetup()}
        
        if [ -f "pom.xml" ]; then
            echo "Building Maven project with EAP repositories..."
            
            if mvn clean compile -B; then
                echo "✓ Maven compile completed successfully"
            else
                echo "❌ Maven compile failed"
                exit 1
            fi
            
            if mvn test -B; then
                echo "✓ Maven test completed successfully"
            else
                echo "⚠ Some Maven tests failed, continuing"
            fi
            
            echo "✓ Maven build enhanced completed"
        else
            echo "ℹ No pom.xml found, skipping Maven build"
        fi
        """
    }

    private fun generateScript(functionName: String, content: () -> String): String = """
        #!/bin/bash
        set -e
        
        # Script: $functionName
        # Generated for EAP validation with configuration preservation
        
        ${content().trimIndent()}
    """.trimIndent()
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
        id("KtorExternalEAPSample_${projectName.replace('-', '_').replace(' ', '_')}")
        name = "EAP Validate External $projectName"

        configureEAPSampleBuild(projectName, vcsRoot, versionResolver)

        steps {
            with(EAPBuildSteps) {
                standardEAPSetup(specialHandling)

                when (buildType) {
                    ExternalSampleBuildType.GRADLE -> gradleEAPBuild(specialHandling)
                    ExternalSampleBuildType.MAVEN -> mavenEAPBuild()
                    ExternalSampleBuildType.AMPER -> amperEAPBuild()
                }
            }
        }

        features {
            with(EAPBuildFeatures) {
                addEAPSlackNotifications()
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
    name = "External Samples EAP Version Resolver"

    vcs {
        coreEap()
    }

    requirements {
        agent(Agents.OS.Linux, Agents.Arch.X64, hardwareCapacity = ANY)
    }

    params {
        defaultGradleParams()
        param("teamcity.build.skipDependencyBuilds", "true")
        param("teamcity.runAsFirstBuild", "true")
        param("env.KTOR_VERSION", "")
        param("env.KTOR_COMPILER_PLUGIN_VERSION", "")
    }

    steps {
        script {
            name = "Resolve EAP Versions"
            scriptContent = """
                #!/bin/bash
                set -e
                
                echo "=== Resolving EAP Versions ==="
                
                KTOR_EAP_VERSION=$(curl -s "${EapConstants.KTOR_EAP_METADATA_URL}" | grep -o "${EapConstants.EAP_VERSION_REGEX}" | sed 's/[<>]//g' | head -1)
                KTOR_COMPILER_PLUGIN_VERSION=$(curl -s "${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}" | grep -o "${EapConstants.EAP_VERSION_REGEX}" | sed 's/[<>]//g' | head -1)
                
                echo "Resolved Ktor EAP version: ${'$'}KTOR_EAP_VERSION"
                echo "Resolved Ktor Compiler Plugin EAP version: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
                
                echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}KTOR_EAP_VERSION']"
                echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}KTOR_COMPILER_PLUGIN_VERSION']"
                
                echo "✓ EAP version resolution completed"
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
        executionTimeoutMin = 10
    }

    defaultBuildFeatures()
}

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalSampleConfig> = listOf(
    EAPSampleBuilder("Ktor Arrow Example", VCSKtorArrowExample, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
        .build(),

    EAPSampleBuilder("Ktor AI Server", VCSKtorAiServer, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .build(),

    EAPSampleBuilder("Ktor Native Server", VCSKtorNativeServer, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
        .build(),

    EAPSampleBuilder("Ktor Koog Example", VCSKtorKoogExample, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .build(),

    EAPSampleBuilder("Full Stack Ktor Talk", VCSFullStackKtorTalk, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .build(),

    EAPSampleBuilder("Ktor Config Example", VCSKtorConfigExample, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .build(),

    EAPSampleBuilder("Ktor Workshop 2025", VCSKtorWorkshop2025, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.ENHANCED_TOML_PATTERNS)
        .build(),

    EAPSampleBuilder("Amper Ktor Sample", VCSAmperKtorSample, versionResolver)
        .withBuildType(ExternalSampleBuildType.AMPER)
        .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID)
        .build(),

    EAPSampleBuilder("Ktor DI Overview", VCSKtorDIOverview, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .build(),

    EAPSampleBuilder("Ktor Full Stack Real World", VCSKtorFullStackRealWorld, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
        .build()
)

private fun createCompositeBuild(versionResolver: BuildType, buildTypes: List<BuildType>): BuildType = BuildType {
    id("KtorExternalEAPSamplesComposite")
    name = "Validate All External Samples with EAP"
    description = "Comprehensive validation of external samples against EAP versions with configuration preservation"
    type = BuildTypeSettings.Type.COMPOSITE

    triggers {
        finishBuildTrigger {
            buildType = EapConstants.PUBLISH_EAP_BUILD_TYPE_ID
            successfulOnly = true
        }
    }

    features {
        with(EAPBuildFeatures) {
            addEAPSlackNotifications(includeSuccess = true)
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
        executionTimeoutMin = 60
    }
}
