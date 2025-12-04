package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.build.*

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

enum class SpecialHandling {
    KOTLIN_MULTIPLATFORM,
    AMPER_GRADLE_HYBRID,
    ENHANCED_TOML_PATTERNS
}

object ExternalSampleScripts {

    fun backupConfigFiles() = """
        echo "=== Backing up configuration files ==="
        [ -f "gradle.properties" ] && cp gradle.properties gradle.properties.backup
        [ -f "gradle/libs.versions.toml" ] && cp gradle/libs.versions.toml gradle/libs.versions.toml.backup
        [ -f "settings.gradle.kts" ] && cp settings.gradle.kts settings.gradle.kts.backup
        [ -f "settings.gradle" ] && cp settings.gradle settings.gradle.backup
        [ -f "pom.xml" ] && cp pom.xml pom.xml.backup
        [ -f "module.yaml" ] && cp module.yaml module.yaml.backup
        [ -f "settings.yaml" ] && cp settings.yaml settings.yaml.backup
        echo "✓ Configuration files backed up"
    """.trimIndent()

    fun analyzeProjectStructure() = """
        analyze_project_structure() {
            echo "=== Analyzing Project Structure ==="
            
            touch project_analysis.env
            
            if [ -f "build.gradle.kts" ] || [ -f "build.gradle" ]; then
                echo "BUILD_SYSTEM=gradle" >> project_analysis.env
                echo "✓ Detected Gradle build system"
            elif [ -f "pom.xml" ]; then
                echo "BUILD_SYSTEM=maven" >> project_analysis.env
                echo "✓ Detected Maven build system"
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
                    if grep -q "^\s*${'$'}{toml_pattern}\s*=" gradle/libs.versions.toml; then
                        echo "TOML_PATTERN_${'$'}{toml_pattern}=true" >> project_analysis.env
                        echo "✓ Found TOML pattern: ${'$'}{toml_pattern}"
                    fi
                done
            fi
            
            echo "✓ Project analysis completed"
            echo "=== Analysis Results ==="
            cat project_analysis.env
        }
        analyze_project_structure
    """.trimIndent()

    fun updateGradlePropertiesEnhanced() = """
        update_gradle_properties_enhanced() {
            echo "=== Enhanced Gradle Properties Update ==="
            
            if [ -f "gradle.properties" ]; then
                echo "Updating existing gradle.properties..."
                grep -v "^ktor.*Version\s*=" gradle.properties > gradle.properties.tmp || touch gradle.properties.tmp
                grep -v "^ktor\..*\.version\s*=" gradle.properties.tmp > gradle.properties.tmp2 || cp gradle.properties.tmp gradle.properties.tmp2
                
                echo "ktorVersion=%env.KTOR_VERSION%" >> gradle.properties.tmp2
                echo "ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%" >> gradle.properties.tmp2
                
                if grep -q "KMP_PROJECT=true" project_analysis.env 2>/dev/null; then
                    echo "ktor.client.version=%env.KTOR_VERSION%" >> gradle.properties.tmp2
                    echo "ktor.server.version=%env.KTOR_VERSION%" >> gradle.properties.tmp2
                    echo "✓ Added KMP-specific version properties"
                fi
                
                mv gradle.properties.tmp2 gradle.properties
                rm -f gradle.properties.tmp
            else
                echo "Creating new gradle.properties..."
                cat > gradle.properties << EOF
ktorVersion=%env.KTOR_VERSION%
ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%
org.gradle.daemon=false
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m
EOF
            fi
            echo "✓ Enhanced gradle.properties update completed"
        }
        update_gradle_properties_enhanced
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
                    if grep -q "^\s*${'$'}{version_pattern}\s*=" gradle/libs.versions.toml; then
                        sed -i "s/^\s*${'$'}{version_pattern}\s*=.*/${'$'}{version_pattern} = \"%env.KTOR_VERSION%\"/" gradle/libs.versions.toml
                        echo "✓ Updated ${'$'}{version_pattern} version reference"
                    fi
                done

                if grep -q "ktor-bom" gradle/libs.versions.toml; then
                    sed -i 's/ktor-bom = { module = "io.ktor:ktor-bom", version.ref = "[^"]*" }/ktor-bom = { module = "io.ktor:ktor-bom", version.ref = "ktor" }/' gradle/libs.versions.toml
                    echo "✓ Updated ktor-bom reference"
                fi

                if ! grep -q "^\s*ktor\s*=" gradle/libs.versions.toml; then
                    if grep -q "^\[versions\]" gradle/libs.versions.toml; then
                        sed -i '/^\[versions\]/a ktor = "%env.KTOR_VERSION%"' gradle/libs.versions.toml
                    else
                        echo -e "\n[versions]\nktor = \"%env.KTOR_VERSION%\"" >> gradle/libs.versions.toml
                    fi
                    echo "✓ Added ktor version to [versions] section"
                fi

                if grep -q "^\[plugins\]" gradle/libs.versions.toml; then
                    sed -i 's/ktor = { id = "io.ktor.plugin", version.ref = "[^"]*" }/ktor = { id = "io.ktor.plugin", version.ref = "ktor" }/' gradle/libs.versions.toml
                    echo "✓ Updated ktor plugin version reference"
                fi

                echo "✓ Comprehensive version catalog update completed"
            fi
        }
        update_version_catalog_comprehensive
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

    fun setupCompositeBuildsSupport() = """
        setup_composite_build_support() {
            echo "=== Setting up Composite Build Support ==="
            
            if grep -q "COMPOSITE_BUILD=true" project_analysis.env 2>/dev/null; then
                echo "Configuring composite build support..."
                
                for settings_file in "settings.gradle.kts" "settings.gradle"; do
                    if [ -f "${'$'}settings_file" ]; then
                        if grep -q "includeBuild\|include.*Build" "${'$'}settings_file"; then
                            echo "Detected composite build in ${'$'}settings_file"
                            
                            find . -name "gradle.properties" -not -path "./gradle.properties" | while read prop_file; do
                                dir=$(dirname "${'$'}prop_file")
                                cp gradle-eap-init.gradle "${'$'}dir/" 2>/dev/null || true
                                echo "✓ Applied init script to: ${'$'}dir"
                            done
                        fi
                    fi
                done
            else
                echo "No composite build detected, skipping"
            fi
        }
        setup_composite_build_support
    """.trimIndent()

    fun buildGradleProjectEnhanced() = """
        build_gradle_project_enhanced() {
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
            
            ${'$'}GRADLE_CMD clean build \
                --init-script gradle-eap-init.gradle \
                --no-daemon --stacktrace --refresh-dependencies \
                --continue || {
                echo "Build failed, analyzing..."
                analyze_build_failure
                exit 1
            }
            
            echo "✓ Enhanced Gradle build completed successfully"
        }
        build_gradle_project_enhanced
    """.trimIndent()

    fun updateAmperVersionsEnhanced() = """
        update_amper_versions_enhanced() {
            echo "=== Enhanced Amper Version Update ==="
            
            if [ -f "module.yaml" ]; then
                echo "Updating module.yaml..."
                
                sed -i 's/io\.ktor:[^:]*:[0-9][^[:space:]]*/io.ktor:\1:%env.KTOR_VERSION%/g' module.yaml
                
                for artifact in "ktor-server-core" "ktor-client-core" "ktor-server-netty" "ktor-client-cio" "ktor-server-auth" "ktor-serialization"; do
                    if grep -q "${'$'}artifact" module.yaml; then
                        sed -i "s/${'$'}{artifact}:[0-9][^[:space:]]*/${'$'}{artifact}:%env.KTOR_VERSION%/g" module.yaml
                        echo "✓ Updated ${'$'}artifact version"
                    fi
                done
            fi
            
            if [ -f "settings.yaml" ]; then
                echo "Updating settings.yaml..."
                sed -i 's/ktor-version: [0-9][^[:space:]]*/ktor-version: %env.KTOR_VERSION%/g' settings.yaml
                echo "✓ Updated settings.yaml"
            fi
            
            echo "✓ Enhanced Amper version update completed"
        }
        update_amper_versions_enhanced
    """.trimIndent()

    fun validateVersionApplication() = """
        validate_version_application() {
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
                validate_toml_changes
            fi
            
            echo "✓ Using Ktor version: %env.KTOR_VERSION%"
            echo "✓ Using compiler plugin version: %env.KTOR_COMPILER_PLUGIN_VERSION%"
            echo "✓ Version application validation completed"
        }
        validate_version_application
    """.trimIndent()

    fun logVersionChanges() = """
        log_version_changes() {
            echo "=== Version Change Log ==="
            echo "Original files:"
            [ -f "gradle.properties.backup" ] && echo "gradle.properties:" && cat gradle.properties.backup
            [ -f "gradle/libs.versions.toml.backup" ] && echo "libs.versions.toml:" && cat gradle/libs.versions.toml.backup
            
            echo "Modified files:"
            [ -f "gradle.properties" ] && echo "gradle.properties:" && cat gradle.properties
            [ -f "gradle/libs.versions.toml" ] && echo "libs.versions.toml:" && cat gradle/libs.versions.toml
            
            if [ -f "gradle/libs.versions.toml.backup" ] && [ -f "gradle/libs.versions.toml" ]; then
                echo "=== TOML Changes ==="
                diff gradle/libs.versions.toml.backup gradle/libs.versions.toml || true
            fi
            echo "========================"
        }
        log_version_changes
    """.trimIndent()

    fun setupMavenRepositoriesEnhanced() = """
        setup_maven_repositories_enhanced() {
            echo "=== Enhanced Maven Repository Setup ==="
            
            if ! [ -f "pom.xml" ]; then
                echo "ERROR: No pom.xml found"
                exit 1
            fi

            if grep -q "<repositories>" pom.xml; then
                sed -i '/<\/repositories>/i\
        <repository><id>ktor-eap</id><url>${EapRepositoryConfig.KTOR_EAP_URL}</url></repository>\
        <repository><id>compose-dev</id><url>${EapRepositoryConfig.COMPOSE_DEV_URL}</url></repository>' pom.xml
            else
                sed -i '/<modelVersion>.*<\/modelVersion>/a\
    <repositories>\
        <repository><id>ktor-eap</id><url>${EapRepositoryConfig.KTOR_EAP_URL}</url></repository>\
        <repository><id>compose-dev</id><url>${EapRepositoryConfig.COMPOSE_DEV_URL}</url></repository>\
    </repositories>' pom.xml
            fi
            echo "✓ Enhanced Maven repositories configured"
        }
        setup_maven_repositories_enhanced
    """.trimIndent()

    fun buildMavenProjectEnhanced() = """
        build_maven_project_enhanced() {
            echo "=== Enhanced Maven Build ==="
            echo "Using Ktor version: %env.KTOR_VERSION%"
            echo "Using compiler plugin version: %env.KTOR_COMPILER_PLUGIN_VERSION%"
            
            mvn clean compile test \
                -Dktor.version=%env.KTOR_VERSION% \
                -Dktor-compiler-plugin.version=%env.KTOR_COMPILER_PLUGIN_VERSION% \
                -U || {
                echo "Maven build failed, analyzing..."
                analyze_build_failure
                exit 1
            }
            echo "✓ Enhanced Maven build completed successfully"
        }
        build_maven_project_enhanced
    """.trimIndent()

    fun cleanupBackups() = """
        cleanup_backups() {
            echo "=== Cleaning up backup files ==="
            rm -f *.backup gradle/libs.versions.toml.backup gradle-eap-init.gradle gradle-amper-eap-init.gradle project_analysis.env
            echo "✓ Cleanup completed"
        }
        cleanup_backups
    """.trimIndent()
}

fun BuildSteps.buildEnhancedEAPExternalGradleSample() {
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
            ${ExternalSampleScripts.updateGradlePropertiesEnhanced()}
            ${ExternalSampleScripts.updateVersionCatalogComprehensive()}
            ${ExternalSampleScripts.setupEnhancedGradleRepositories()}
            ${ExternalSampleScripts.setupCompositeBuildsSupport()}
            ${ExternalSampleScripts.logVersionChanges()}
        """.trimIndent()
    }

    script {
        name = "Validate Configuration"
        scriptContent = """
            #!/bin/bash
            set -e
            ${ExternalSampleScripts.validateVersionApplication()}
        """.trimIndent()
    }

    script {
        name = "Build Enhanced Gradle Sample"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Building Enhanced External Gradle Sample with Ktor EAP %env.KTOR_VERSION% ==="
            ${ExternalSampleScripts.buildGradleProjectEnhanced()}
            echo "✓ Enhanced build completed successfully"
        """.trimIndent()
    }
}

fun BuildSteps.buildEnhancedEAPExternalMavenSample() {
    script {
        name = "Analyze Project Structure"
        scriptContent = """
            #!/bin/bash
            set -e
            ${ExternalSampleScripts.analyzeProjectStructure()}
        """.trimIndent()
    }

    script {
        name = "Setup Enhanced Maven EAP Configuration"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Setting up Enhanced Maven EAP Configuration ==="
            ${ExternalSampleScripts.backupConfigFiles()}
            ${ExternalSampleScripts.setupMavenRepositoriesEnhanced()}
            ${ExternalSampleScripts.logVersionChanges()}
        """.trimIndent()
    }

    script {
        name = "Validate Configuration"
        scriptContent = """
            #!/bin/bash
            set -e
            ${ExternalSampleScripts.validateVersionApplication()}
        """.trimIndent()
    }

    script {
        name = "Build Enhanced Maven Sample"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Building Enhanced External Maven Sample with Ktor EAP %env.KTOR_VERSION% ==="
            ${ExternalSampleScripts.buildMavenProjectEnhanced()}
            echo "✓ Enhanced Maven build completed successfully"
        """.trimIndent()
    }
}

fun BuildSteps.buildEnhancedEAPExternalAmperSample() {
    script {
        name = "Analyze Project Structure"
        scriptContent = """
            #!/bin/bash
            set -e
            ${ExternalSampleScripts.analyzeProjectStructure()}
        """.trimIndent()
    }

    script {
        name = "Setup Enhanced Amper EAP Configuration"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Setting up Enhanced Amper EAP Configuration ==="
            ${ExternalSampleScripts.backupConfigFiles()}
            ${ExternalSampleScripts.updateAmperVersionsEnhanced()}
            ${ExternalSampleScripts.updateGradlePropertiesEnhanced()}
            ${ExternalSampleScripts.logVersionChanges()}
        """.trimIndent()
    }

    script {
        name = "Validate Configuration"
        scriptContent = """
            #!/bin/bash
            set -e
            ${ExternalSampleScripts.validateVersionApplication()}
        """.trimIndent()
    }

    script {
        name = "Build Enhanced Amper Sample"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Building Enhanced External Amper Sample with Ktor EAP %env.KTOR_VERSION% ==="
            ${ExternalSampleScripts.setupEnhancedGradleRepositories()}
            ${ExternalSampleScripts.buildGradleProjectEnhanced()}
            echo "✓ Enhanced Amper build completed successfully"
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

data class ExternalSampleConfig(
    override val projectName: String,
    val vcsRoot: VcsRoot,
    val buildType: ExternalSampleBuildType = ExternalSampleBuildType.GRADLE,
    val versionResolver: BuildType,
    val specialHandling: List<SpecialHandling> = emptyList()
) : EAPSampleConfig {

    override fun createEAPBuildType(): BuildType {
        return BuildType {
            id("ExternalEAP_${projectName.replace("-", "_").replace(" ", "_")}")
            name = "EAP: $projectName"
            description = "Enhanced validation of $projectName against EAP version of Ktor with comprehensive TOML handling"

            configureEAPSampleBuild(projectName, vcsRoot, versionResolver)

            steps {
                debugEnvironmentVariables()
                createEAPGradleInitScript()

                when (buildType) {
                    ExternalSampleBuildType.GRADLE -> buildEnhancedEAPExternalGradleSample()
                    ExternalSampleBuildType.MAVEN -> buildEnhancedEAPExternalMavenSample()
                    ExternalSampleBuildType.AMPER -> buildEnhancedEAPExternalAmperSample()
                }

                addEnhancedCleanupStep()
            }

            failureConditions {
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "BUILD FAILED"
                    failureMessage = "Build failed for $projectName"
                    stopBuildOnFailure = true
                }
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "CRITICAL ERROR:"
                    failureMessage = "Critical error in $projectName build"
                    stopBuildOnFailure = true
                }
                executionTimeoutMin = 30
            }
        }
    }
}

enum class ExternalSampleBuildType {
    GRADLE, MAVEN, AMPER
}

object ExternalSamplesEAPValidation : Project({
    id("ExternalSamplesEAPValidation")
    name = "External Samples EAP Validation"
    description = "Enhanced validation of external GitHub samples against EAP versions of Ktor with comprehensive TOML handling and improved monitoring"

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

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
        param("toml.comprehensive.handling", "true")
    }

    val versionResolver = EAPVersionResolver.createVersionResolver(
        id = "ExternalKtorEAPVersionResolver",
        name = "EAP Version Resolver for External Samples",
        description = "Enhanced version resolver with comprehensive validation for external sample validation"
    )

    buildType(versionResolver)

    val externalSamples = listOf(
        ExternalSampleConfig(
            "ktor-arrow-example",
            VCSKtorArrowExample,
            ExternalSampleBuildType.GRADLE,
            versionResolver,
            listOf(SpecialHandling.ENHANCED_TOML_PATTERNS)
        ),
        ExternalSampleConfig(
            "ktor-ai-server",
            VCSKtorAiServer,
            ExternalSampleBuildType.GRADLE,
            versionResolver,
            listOf(SpecialHandling.ENHANCED_TOML_PATTERNS)
        ),
        ExternalSampleConfig(
            "ktor-native-server",
            VCSKtorNativeServer,
            ExternalSampleBuildType.GRADLE,
            versionResolver,
            listOf(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.ENHANCED_TOML_PATTERNS)
        ),
        ExternalSampleConfig(
            "ktor-koog-example",
            VCSKtorKoogExample,
            ExternalSampleBuildType.GRADLE,
            versionResolver,
            listOf(SpecialHandling.ENHANCED_TOML_PATTERNS)
        ),
        ExternalSampleConfig(
            "full-stack-ktor-talk",
            VCSFullStackKtorTalk,
            ExternalSampleBuildType.GRADLE,
            versionResolver,
            listOf(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.ENHANCED_TOML_PATTERNS)
        ),
        ExternalSampleConfig(
            "ktor-config-example",
            VCSKtorConfigExample,
            ExternalSampleBuildType.GRADLE,
            versionResolver,
            listOf(SpecialHandling.ENHANCED_TOML_PATTERNS)
        ),
        ExternalSampleConfig(
            "ktor-workshop-2025",
            VCSKtorWorkshop2025,
            ExternalSampleBuildType.GRADLE,
            versionResolver,
            listOf(SpecialHandling.ENHANCED_TOML_PATTERNS)
        ),
        ExternalSampleConfig(
            "amper-ktor-sample",
            VCSAmperKtorSample,
            ExternalSampleBuildType.AMPER,
            versionResolver,
            listOf(SpecialHandling.AMPER_GRADLE_HYBRID, SpecialHandling.ENHANCED_TOML_PATTERNS)
        ),
        ExternalSampleConfig(
            "Ktor-DI-Overview",
            VCSKtorDIOverview,
            ExternalSampleBuildType.GRADLE,
            versionResolver,
            listOf(SpecialHandling.ENHANCED_TOML_PATTERNS)
        ),
        ExternalSampleConfig(
            "ktor-full-stack-real-world",
            VCSKtorFullStackRealWorld,
            ExternalSampleBuildType.GRADLE,
            versionResolver,
            listOf(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.ENHANCED_TOML_PATTERNS)
        )
    )

    val allExternalSampleBuilds = externalSamples.map { it.createEAPBuildType() }

    allExternalSampleBuilds.forEach { buildType(it) }

    buildType {
        id("ExternalKtorEAPSamplesCompositeBuild")
        name = "Validate All External Samples with EAP"
        description = "Enhanced validation of all external GitHub samples against EAP version of Ktor with comprehensive TOML handling"
        type = BuildTypeSettings.Type.COMPOSITE

        params {
            defaultGradleParams()
            param("env.GIT_BRANCH", "%teamcity.build.branch%")
            param("teamcity.build.skipDependencyBuilds", "true")
            param("enhanced.validation.enabled", "true")
        }

        features {
            notifications {
                notifierSettings = slackNotifier {
                    connection = "PROJECT_EXT_5"
                    sendTo = "#ktor-external-samples-eap"
                    messageFormat = verboseMessageFormat {
                        addStatusText = true
                        addBranch = true
                        addChanges = true
                    }
                }
                buildFailedToStart = true
                buildFailed = true
                buildFinishedSuccessfully = true
                firstSuccessAfterFailure = true
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

            allExternalSampleBuilds.forEach { sampleBuild ->
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
            executionTimeoutMin = 45
        }
    }
})
