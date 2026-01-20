package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import subprojects.build.defaultGradleParams
import subprojects.VCSSamples
import subprojects.VCSKtorBuildPlugins
import subprojects.Agents
import subprojects.agent
import dsl.addSlackNotifications
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger

object EapConstants {
    const val EAP_VERSION_REGEX = ">[0-9][^<]*-eap-[0-9]*<"
    const val KTOR_EAP_METADATA_URL =
        "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml"
    const val KTOR_COMPILER_PLUGIN_METADATA_URL =
        "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-compiler-plugin/maven-metadata.xml"
    const val KOTLIN_EAP_METADATA_URL =
        "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/org/jetbrains/kotlin/kotlin-compiler-embeddable/maven-metadata.xml"
}

object ConsolidatedEAPValidation {
    fun createConsolidatedProject(): Project {
        return Project {
            id("ProjectKtorConsolidatedEAPValidation")
            name = "Consolidated EAP Validation"
            description = "Comprehensive EAP validation pipeline for Ktor framework"

            buildType(createConsolidatedBuild())
        }
    }

    /**
     * Creates a consolidated EAP validation build that combines all validation steps into one build
     *
     * Step 1: Version Resolution
     * Step 2: External Samples Validation
     * Step 3: Internal Test Suites
     * Step 4: Quality Gate Evaluation
     * Step 5: Report Generation & Notifications
     */
    private fun createConsolidatedBuild(): BuildType {
        return BuildType {
            id("KtorConsolidatedEAPValidation")
            name = "Consolidated EAP Validation"
            description = "Comprehensive EAP validation combining all validation steps"

            vcs {
                root(VCSSamples)
                root(VCSKtorBuildPlugins)
            }


            params {
                param("env.BUILD_PLUGINS_CHECKOUT_DIR", "build-plugins")

                // Quality Gate Configuration Parameters
                param("quality.gate.scoring.external.weight", "60")
                param("quality.gate.scoring.internal.weight", "40")
                param("quality.gate.thresholds.minimum.score", "80")
                param("quality.gate.thresholds.critical.issues", "0")

                // Optional Slack webhook for detailed notifications
                param("system.slack.webhook.url", "")

                // Version parameters
                param("env.KTOR_VERSION", "")
                param("env.KTOR_COMPILER_PLUGIN_VERSION", "")
                param("env.KOTLIN_VERSION", "2.1.21")

                // Version resolution parameters
                param("version.resolution.errors", "0")

                // External validation parameters
                param("external.validation.total.samples", "0")
                param("external.validation.successful.samples", "0")
                param("external.validation.failed.samples", "0")
                param("external.validation.skipped.samples", "0")
                param("external.validation.success.rate", "0.0")

                // Internal validation parameters
                param("internal.validation.total.tests", "0")
                param("internal.validation.passed.tests", "0")
                param("internal.validation.failed.tests", "0")
                param("internal.validation.error.tests", "0")
                param("internal.validation.skipped.tests", "0")
                param("internal.validation.success.rate", "0.0")
                param("internal.validation.processed.files", "0")

                // Quality gate evaluation parameters
                param("quality.gate.overall.status", "UNKNOWN")
                param("quality.gate.overall.score", "0")
                param("quality.gate.total.critical", "0")
                param("external.gate.status", "UNKNOWN")
                param("external.gate.score", "0")
                param("internal.gate.status", "UNKNOWN")
                param("internal.gate.score", "0")
                param("quality.gate.recommendations", "Validation not yet completed")
                param("quality.gate.next.steps", "Run validation steps")
                param("quality.gate.failure.reasons", "")

                // Slack notification parameters
                param("quality.gate.slack.status.emoji", "⏳")
                param("quality.gate.slack.external.emoji", "⏳")
                param("quality.gate.slack.internal.emoji", "⏳")
                param("quality.gate.slack.critical.emoji", "⏳")

                defaultGradleParams()
            }

            steps {
                versionResolution()
                externalSamplesValidation()
                internalTestSuites()
                qualityGateEvaluation()
                reportGenerationAndNotifications()
            }

            triggers {
                finishBuildTrigger {
                    buildType = "KtorEAP_EAPValidation"
                    successfulOnly = true
                    branchFilter = "+:*"
                }
            }

            failureConditions {
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "QUALITY_GATE_FAILED"
                    failureMessage = "Quality gate validation failed"
                    reverse = false
                }
            }

            addSlackNotifications()

            requirements {
                agent(Agents.OS.Linux)
            }
        }
    }

    /**
     * Step 1: Version Resolution
     * Fetches the latest EAP versions for Ktor framework, compiler plugin, and Kotlin
     * Uses resilient approach - continues even if some versions fail to fetch
     */
    private fun BuildSteps.versionResolution() {
        script {
            name = "Step 1: Version Resolution"
            scriptContent = """
                #!/bin/bash
                set -e
                
                echo "=== Step 1: Version Resolution ==="
                echo "Fetching latest EAP versions for validation"
                
                # Function to extract version from XML metadata
                extract_version() {
                    local url="$1"
                    local description="$2"
                    echo "🔍 Fetching ${'$'}description from ${'$'}url"
                    
                    if curl -s "${'$'}url" | grep -o "${EapConstants.EAP_VERSION_REGEX}" | head -1 | sed 's/[><]//g'; then
                        echo "✅ Successfully fetched ${'$'}description"
                    else
                        echo "❌ Failed to fetch ${'$'}description, will use fallback"
                        echo ""
                    fi
                }
                
                # Fetch Ktor EAP version
                echo "KTOR_VERSION=" > eap-versions.properties
                KTOR_VERSION=$(extract_version "${EapConstants.KTOR_EAP_METADATA_URL}" "Ktor EAP version")
                if [ -n "${'$'}KTOR_VERSION" ]; then
                    echo "KTOR_VERSION=${'$'}KTOR_VERSION" >> eap-versions.properties
                    echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}KTOR_VERSION']"
                fi
                
                # Fetch Ktor Compiler Plugin EAP version
                KTOR_COMPILER_PLUGIN_VERSION=$(extract_version "${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}" "Ktor Compiler Plugin EAP version")
                if [ -n "${'$'}KTOR_COMPILER_PLUGIN_VERSION" ]; then
                    echo "KTOR_COMPILER_PLUGIN_VERSION=${'$'}KTOR_COMPILER_PLUGIN_VERSION" >> eap-versions.properties
                    echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}KTOR_COMPILER_PLUGIN_VERSION']"
                fi
                
                # Fetch Kotlin EAP version
                KOTLIN_VERSION=$(extract_version "${EapConstants.KOTLIN_EAP_METADATA_URL}" "Kotlin EAP version")
                if [ -n "${'$'}KOTLIN_VERSION" ]; then
                    # Fix Kotlin version format
                    if [[ "${'$'}KOTLIN_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-[0-9]+$ ]]; then
                        KOTLIN_VERSION=$(echo "${'$'}KOTLIN_VERSION" | sed 's/-[0-9]*$//')
                        echo "🔧 Corrected Kotlin version format to: ${'$'}KOTLIN_VERSION"
                    fi
                    echo "KOTLIN_VERSION=${'$'}KOTLIN_VERSION" >> eap-versions.properties
                    echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='${'$'}KOTLIN_VERSION']"
                else
                    echo "KOTLIN_VERSION=2.1.21" >> eap-versions.properties
                    echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='2.1.21']"
                fi
                
                echo ""
                echo "=== Resolved EAP Versions ==="
                cat eap-versions.properties
                echo "=== Step 1: Version Resolution Completed ==="
            """.trimIndent()
        }
    }

    /**
     * Step 2: External Samples Validation
     * Validates external GitHub samples against the resolved EAP versions
     * Uses resilient approach - processes all samples and reports results
     */
    private fun BuildSteps.externalSamplesValidation() {
        script {
            name = "Step 2: External Samples Validation"
            scriptContent = """
                #!/bin/bash
                set -e
                
                echo "=== Step 2: External Samples Validation ==="
                echo "Validating external GitHub repositories against EAP versions"
                
                # Get current parameter values
                KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
                KTOR_COMPILER_PLUGIN_VERSION=$(echo "%env.KTOR_COMPILER_PLUGIN_VERSION%" | sed 's/^%env\.KTOR_COMPILER_PLUGIN_VERSION%$//' || echo "")
                KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.1.21/' || echo "2.1.21")
                
                echo "Using versions: Ktor=${'$'}KTOR_VERSION, Plugin=${'$'}KTOR_COMPILER_PLUGIN_VERSION, Kotlin=${'$'}KOTLIN_VERSION"
                
                # Create reports directory
                mkdir -p external-validation-reports
                
                # List of external samples to validate
                declare -A EXTERNAL_SAMPLES=(
                    ["ktor-ai-server"]="https://github.com/nomisRev/ktor-ai-server.git"
                    ["ktor-native-server"]="https://github.com/nomisRev/ktor-native-server.git"
                    ["ktor-config-example"]="https://github.com/nomisRev/ktor-config-example.git"
                    ["ktor-workshop-2025"]="https://github.com/nomisRev/ktor-workshop-2025.git"
                    ["amper-ktor-sample"]="https://github.com/nomisRev/amper-ktor-sample.git"
                    ["ktor-di-overview"]="https://github.com/nomisRev/Ktor-DI-Overview.git"
                    ["ktor-full-stack-real-world"]="https://github.com/nomisRev/ktor-full-stack-real-world.git"
                )
                
                SUCCESSFUL_COUNT=0
                FAILED_COUNT=0
                SKIPPED_COUNT=0
                
                # Process each external sample
                for sample_name in "${'$'}{!EXTERNAL_SAMPLES[@]}"; do
                    repo_url="${'$'}{EXTERNAL_SAMPLES[${'$'}sample_name]}"
                    
                    echo "🔄 Validating external sample: ${'$'}sample_name"
                    
                    if timeout 600 bash -c "
                        git clone --depth 1 '${'$'}repo_url' temp-${'$'}sample_name 2>/dev/null || exit 1
                        cd temp-${'$'}sample_name 2>/dev/null || exit 1
                        
                        if [ -f 'gradlew' ]; then
                            ./gradlew clean build --no-daemon -q 2>/dev/null || exit 1
                        elif [ -f 'pom.xml' ]; then
                            mvn clean test -q 2>/dev/null || exit 1
                        else
                            exit 1
                        fi
                    " > "external-validation-reports/${'$'}sample_name.log" 2>&1; then
                        echo "✅ External sample ${'$'}sample_name: SUCCESSFUL"
                        ((SUCCESSFUL_COUNT++))
                    else
                        echo "❌ External sample ${'$'}sample_name: FAILED"
                        ((FAILED_COUNT++))
                    fi
                    
                    # Cleanup
                    rm -rf "temp-${'$'}sample_name" 2>/dev/null || true
                done
                
                echo ""
                echo "=== External Validation Results ==="
                echo "Successful: ${'$'}SUCCESSFUL_COUNT"
                echo "Failed: ${'$'}FAILED_COUNT"
                echo "=== Step 2: External Samples Validation Completed ==="
            """.trimIndent()
        }
    }

    /**
     * Step 3: Internal Test Suites
     * Validates internal Ktor samples against the EAP versions
     * Uses resilient approach - runs tests and processes results regardless of failures
     */
    private fun BuildSteps.internalTestSuites() {
        script {
            name = "Step 3: Internal Test Suites - Setup EAP Environment"
            scriptContent = """
            #!/bin/bash
            set -e
            
            echo "=== Step 3: Internal Test Suites - EAP Sample Validation ==="
            echo "Setting up EAP environment for internal sample validation"

            # Get current parameter values or use fallback defaults
            KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
            KTOR_COMPILER_PLUGIN_VERSION=$(echo "%env.KTOR_COMPILER_PLUGIN_VERSION%" | sed 's/^%env\.KTOR_COMPILER_PLUGIN_VERSION%$//' || echo "")
            KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.1.21/' || echo "2.1.21")

            echo "Ktor Version: ${'$'}KTOR_VERSION"
            echo "Ktor Compiler Plugin Version: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

            # Validate Kotlin version format and fix if needed
            if [[ "${'$'}KOTLIN_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-[0-9]+$ ]]; then
                echo "⚠️  Invalid Kotlin version format: ${'$'}KOTLIN_VERSION (looks like build number)"
                # Extract base version (e.g., 2.1.22 from 2.1.22-332)
                KOTLIN_VERSION=$(echo "${'$'}KOTLIN_VERSION" | sed 's/-[0-9]*$//')
                echo "🔧 Using corrected Kotlin version: ${'$'}KOTLIN_VERSION"
            fi

            # Create reports directory with absolute path
            REPORTS_DIR="${'$'}PWD/internal-validation-reports"
            mkdir -p "${'$'}REPORTS_DIR"
            
            # Store the absolute path in environment
            echo "REPORTS_DIR=${'$'}REPORTS_DIR" > build-env.properties
            echo "KOTLIN_VERSION=${'$'}KOTLIN_VERSION" >> build-env.properties

            # Create EAP Gradle init script with correct Groovy syntax
            echo "Creating EAP Gradle init script..."
            cat > gradle-eap-init.gradle <<EOF
allprojects {
    repositories {
        maven {
            url "https://maven.pkg.jetbrains.space/public/p/ktor/eap"
        }
        maven {
            url "https://maven.pkg.jetbrains.space/public/p/compose/dev"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
EOF

            # Create Maven settings with EAP repositories
            echo "Creating Maven settings for EAP repositories..."
            mkdir -p ~/.m2
            cat > ~/.m2/settings.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <profiles>
        <profile>
            <id>ktor-eap</id>
            <repositories>
                <repository>
                    <id>ktor-eap-repo</id>
                    <url>https://maven.pkg.jetbrains.space/public/p/ktor/eap</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </repository>
                <repository>
                    <id>compose-dev-repo</id>
                    <url>https://maven.pkg.jetbrains.space/public/p/compose/dev</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>ktor-eap-plugins</id>
                    <url>https://maven.pkg.jetbrains.space/public/p/ktor/eap</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>
    <activeProfiles>
        <activeProfile>ktor-eap</activeProfile>
    </activeProfiles>
</settings>
EOF

            echo "EAP configuration created successfully"
        """.trimIndent()
        }

        script {
            name = "Step 3: Internal Test Suites - Regular Samples"
            scriptContent = """
                #!/bin/bash
                set -e
                
                source build-env.properties
                
                echo "=== Validating Regular Sample Projects against EAP versions (PARALLEL) ==="
                echo "Using reports directory: ${'$'}REPORTS_DIR"
                echo "Using Kotlin version: ${'$'}KOTLIN_VERSION"
                echo "Current working directory: $(pwd)"
                echo "Directory contents:"
                ls -la | head -10
                
                # Set maximum parallel jobs (adjust based on CI agent capacity)
                MAX_PARALLEL_JOBS=4
                
                # Function to validate a single sample
                validate_sample() {
                    local sample_dir="$1"
                    local sample_name=$(basename "${'$'}sample_dir")
                    local log_file="${'$'}REPORTS_DIR/build-${'$'}sample_name.log"
                    
                    echo "🔄 [PARALLEL] Starting validation of sample: ${'$'}sample_name"
                    
                    if [ ! -d "${'$'}sample_dir" ]; then
                        echo "⚠️  Sample ${'$'}sample_name: DIRECTORY_NOT_FOUND - skipping" | tee -a "${'$'}log_file"
                        echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/skipped-samples.log"
                        return 0
                    fi
                    
                    cd "${'$'}sample_dir"
                    
                    # Determine build command based on build system
                    if [ -f "pom.xml" ]; then
                        # Maven build - use corrected Kotlin version
                        echo "🔧 Maven sample detected, using Kotlin version: ${'$'}KOTLIN_VERSION"
                        if timeout 300 mvn clean test -q -Dkotlin.version="${'$'}KOTLIN_VERSION" > "${'$'}log_file" 2>&1; then
                            echo "✅ [PARALLEL] Sample ${'$'}sample_name: BUILD SUCCESSFUL"
                            echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                        else
                            echo "❌ [PARALLEL] Sample ${'$'}sample_name: BUILD FAILED (exit code: $?)"
                            echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/failed-samples.log"
                        fi
                    elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
                        # Gradle build - samples repository uses Gradle wrapper
                        if [ -f "./gradlew" ]; then
                            echo "🔧 Using Gradle wrapper for ${'$'}sample_name"
                            if timeout 300 ./gradlew clean build --init-script "${'$'}PWD/../gradle-eap-init.gradle" --no-daemon -q > "${'$'}log_file" 2>&1; then
                                echo "✅ [PARALLEL] Sample ${'$'}sample_name: BUILD SUCCESSFUL"
                                echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                            else
                                echo "❌ [PARALLEL] Sample ${'$'}sample_name: BUILD FAILED (exit code: $?)"
                                echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/failed-samples.log"
                            fi
                        else
                            # Try using parent Gradle wrapper if it exists
                            if [ -f "../gradlew" ]; then
                                echo "🔧 Using parent Gradle wrapper for ${'$'}sample_name"
                                if timeout 300 ../gradlew -p . clean build --init-script "${'$'}PWD/../gradle-eap-init.gradle" --no-daemon -q > "${'$'}log_file" 2>&1; then
                                    echo "✅ [PARALLEL] Sample ${'$'}sample_name: BUILD SUCCESSFUL"
                                    echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                                else
                                    echo "❌ [PARALLEL] Sample ${'$'}sample_name: BUILD FAILED (exit code: $?)"
                                    echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/failed-samples.log"
                                fi
                            else
                                echo "⚠️  Sample ${'$'}sample_name: NO_GRADLE_WRAPPER - skipping" | tee -a "${'$'}log_file"
                                echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/skipped-samples.log"
                            fi
                        fi
                    else
                        echo "⚠️  Sample ${'$'}sample_name: NO_BUILD_FILE - skipping" | tee -a "${'$'}log_file"
                        echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/skipped-samples.log"
                    fi
                    
                    cd - > /dev/null
                }
                
                # Export function for parallel execution
                export -f validate_sample
                export REPORTS_DIR
                export PWD
                export KOTLIN_VERSION
                
                # Initialize result files
                touch "${'$'}REPORTS_DIR/successful-samples.log"
                touch "${'$'}REPORTS_DIR/failed-samples.log"
                touch "${'$'}REPORTS_DIR/skipped-samples.log"
                
                echo "=== Processing Regular Sample Projects (PARALLEL) ==="
                
                # Get list of sample directories (excluding hidden dirs and special dirs)
                SAMPLE_DIRS=$(find . -maxdepth 1 -type d -name "*" ! -name "." ! -name "..*" ! -name ".*" ! -name "build-plugins" | head -30)
                
                # Run samples in parallel using xargs
                if [ -n "${'$'}SAMPLE_DIRS" ]; then
                    echo "${'$'}SAMPLE_DIRS" | xargs -n 1 -P ${'$'}MAX_PARALLEL_JOBS -I {} bash -c 'validate_sample "{}"'
                else
                    echo "ℹ️  No sample directories found in current VCS root"
                    echo "📁 Current directory: $(pwd)"
                    echo "📁 Available files and directories:"
                    find . -maxdepth 2 -name "*.gradle*" -o -name "pom.xml" | head -10
                fi
                
                echo "=== Regular samples validation completed ==="
            """.trimIndent()
        }

        script {
            name = "Step 3: Build Plugin Samples"
            scriptContent = """
                #!/bin/bash
                set -e
                
                source build-env.properties
                
                echo "=== Step 3c: Build Plugin Samples Validation ==="
                echo "Locating build plugins repository checkout..."
                
                # Find the build plugins checkout directory
                BUILD_PLUGINS_DIR=""
                if [ -d "%env.BUILD_PLUGINS_CHECKOUT_DIR%" ] && [ "%env.BUILD_PLUGINS_CHECKOUT_DIR%" != "%env.BUILD_PLUGINS_CHECKOUT_DIR%" ]; then
                    BUILD_PLUGINS_DIR="%env.BUILD_PLUGINS_CHECKOUT_DIR%"
                elif [ -d "build-plugins" ]; then
                    BUILD_PLUGINS_DIR="build-plugins"
                else
                    # Search for ktor build plugins checkout
                    BUILD_PLUGINS_DIR=$(find . -maxdepth 1 -type d -name "*ktor*build*plugin*" 2>/dev/null | head -1 || echo "")
                fi
                
                if [ -z "${'$'}BUILD_PLUGINS_DIR" ] || [ ! -d "${'$'}BUILD_PLUGINS_DIR" ]; then
                    echo "⚠️  Build plugins repository not found, skipping build plugin samples validation"
                    echo "Checked directories:"
                    echo "  - %env.BUILD_PLUGINS_CHECKOUT_DIR%"
                    echo "  - build-plugins"
                    echo "Available directories:"
                    ls -la | grep "^d" | head -10
                    return 0
                fi
                
                echo "✅ Found build plugins repository at: ${'$'}BUILD_PLUGINS_DIR"
                
                # Function to validate build plugin sample
                validate_build_plugin_sample() {
                    local sample_name="$1"
                    local sample_dir="${'$'}BUILD_PLUGINS_DIR/samples/${'$'}sample_name"
                    local log_file="${'$'}REPORTS_DIR/build-plugin-${'$'}sample_name.log"
                    
                    echo "🔄 [BUILD PLUGIN] Starting validation of sample: ${'$'}sample_name"
                    
                    if [ ! -d "${'$'}sample_dir" ]; then
                        echo "⚠️  Build plugin sample ${'$'}sample_name: DIRECTORY_NOT_FOUND - skipping" | tee -a "${'$'}log_file"
                        echo "build-plugin-${'$'}sample_name" >> "${'$'}REPORTS_DIR/skipped-samples.log"
                        return 0
                    fi
                    
                    cd "${'$'}sample_dir"
                    
                    # Build plugin samples use Gradle wrapper
                    if [ -f "./gradlew" ]; then
                        echo "🔧 Using Gradle wrapper for build plugin sample: ${'$'}sample_name"
                        if timeout 300 ./gradlew clean build --init-script "${'$'}PWD/../../../gradle-eap-init.gradle" --no-daemon -q > "${'$'}log_file" 2>&1; then
                            echo "✅ [BUILD PLUGIN] Sample ${'$'}sample_name: BUILD SUCCESSFUL"
                            echo "build-plugin-${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                        else
                            echo "❌ [BUILD PLUGIN] Sample ${'$'}sample_name: BUILD FAILED (exit code: $?)"
                            echo "build-plugin-${'$'}sample_name" >> "${'$'}REPORTS_DIR/failed-samples.log"
                        fi
                    else
                        echo "⚠️  Build plugin sample ${'$'}sample_name: NO_GRADLE_WRAPPER - skipping" | tee -a "${'$'}log_file"
                        echo "build-plugin-${'$'}sample_name" >> "${'$'}REPORTS_DIR/skipped-samples.log"
                    fi
                    
                    cd - > /dev/null
                }
                
                # Export function for parallel execution
                export -f validate_build_plugin_sample
                export REPORTS_DIR
                export PWD
                export BUILD_PLUGINS_DIR
                
                # List of known build plugin samples
                BUILD_PLUGIN_SAMPLES=(
                    "ktor-docker-sample"
                    "ktor-fatjar-sample"
                    "ktor-native-image-sample"
                    "ktor-openapi-sample"
                )
                
                # Validate build plugin samples in parallel
                echo "=== Processing Build Plugin Samples (PARALLEL) ==="
                
                # Use printf to create proper input for xargs
                printf '%s\n' "${'$'}{BUILD_PLUGIN_SAMPLES[@]}" | xargs -n 1 -P 4 -I {} bash -c 'validate_build_plugin_sample "{}"'
                
                # Wait for all background jobs to complete
                wait
                
                echo "=== Build plugin samples validation completed ==="
            """.trimIndent()
        }

        script {
            name = "Step 3: Generate Internal Test Suites Summary"
            scriptContent = """
                #!/bin/bash
                set -e
                
                source build-env.properties
                
                echo "=== Generating Internal Test Suites Summary ==="
                
                # Generate summary
                SUCCESSFUL_COUNT=$(wc -l < "${'$'}REPORTS_DIR/successful-samples.log" 2>/dev/null || echo "0")
                FAILED_COUNT=$(wc -l < "${'$'}REPORTS_DIR/failed-samples.log" 2>/dev/null || echo "0")
                SKIPPED_COUNT=$(wc -l < "${'$'}REPORTS_DIR/skipped-samples.log" 2>/dev/null || echo "0")
                TOTAL_COUNT=$((SUCCESSFUL_COUNT + FAILED_COUNT + SKIPPED_COUNT))
                
                if [ ${'$'}TOTAL_COUNT -gt 0 ]; then
                    SUCCESS_RATE=$(( (SUCCESSFUL_COUNT * 100) / TOTAL_COUNT ))
                else
                    SUCCESS_RATE=0
                fi
                
                echo "=== Internal Sample Validation Results (PARALLEL EXECUTION) ==="
                echo "Total samples processed: ${'$'}TOTAL_COUNT"
                echo "Successful: ${'$'}SUCCESSFUL_COUNT"
                echo "Failed: ${'$'}FAILED_COUNT"
                echo "Skipped: ${'$'}SKIPPED_COUNT"
                echo "Success rate: ${'$'}SUCCESS_RATE%"
                
                if [ -s "${'$'}REPORTS_DIR/successful-samples.log" ]; then
                    echo ""
                    echo "✅ Successful samples:"
                    cat "${'$'}REPORTS_DIR/successful-samples.log" | sed 's/^/  - /'
                fi
                
                if [ -s "${'$'}REPORTS_DIR/failed-samples.log" ]; then
                    echo ""
                    echo "❌ Failed samples:"
                    cat "${'$'}REPORTS_DIR/failed-samples.log" | sed 's/^/  - /'
                fi
                
                if [ -s "${'$'}REPORTS_DIR/skipped-samples.log" ]; then
                    echo ""
                    echo "⚠️  Skipped samples:"
                    cat "${'$'}REPORTS_DIR/skipped-samples.log" | sed 's/^/  - /'
                fi
                
                echo "=== Step 3: Internal Sample Validation Completed ==="
            """.trimIndent()
        }
    }

    /**
     * Step 4: Quality Gate Evaluation
     * Evaluates all validation results against quality gate criteria
     * Always runs regardless of previous step outcomes
     */
    private fun BuildSteps.qualityGateEvaluation() {
        script {
            name = "Step 4: Quality Gate Evaluation"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/bin/bash
                
                echo "=== Step 4: Quality Gate Evaluation ==="
                echo "Evaluating all validation results against quality criteria"
                
                # Quality gate thresholds
                MIN_SUCCESS_RATE=75
                MAX_FAILED_SAMPLES=5
                
                # Initialize counters
                TOTAL_SUCCESSFUL=0
                TOTAL_FAILED=0
                TOTAL_SKIPPED=0
                
                # Count internal sample results
                if [ -f "internal-validation-reports/successful-samples.log" ]; then
                    INTERNAL_SUCCESSFUL=$(wc -l < "internal-validation-reports/successful-samples.log")
                    TOTAL_SUCCESSFUL=$((TOTAL_SUCCESSFUL + INTERNAL_SUCCESSFUL))
                fi
                
                if [ -f "internal-validation-reports/failed-samples.log" ]; then
                    INTERNAL_FAILED=$(wc -l < "internal-validation-reports/failed-samples.log")
                    TOTAL_FAILED=$((TOTAL_FAILED + INTERNAL_FAILED))
                fi
                
                if [ -f "internal-validation-reports/skipped-samples.log" ]; then
                    INTERNAL_SKIPPED=$(wc -l < "internal-validation-reports/skipped-samples.log")
                    TOTAL_SKIPPED=$((TOTAL_SKIPPED + INTERNAL_SKIPPED))
                fi
                
                TOTAL_SAMPLES=$((TOTAL_SUCCESSFUL + TOTAL_FAILED + TOTAL_SKIPPED))
                
                if [ ${'$'}TOTAL_SAMPLES -gt 0 ]; then
                    SUCCESS_RATE=$(( (TOTAL_SUCCESSFUL * 100) / TOTAL_SAMPLES ))
                else
                    SUCCESS_RATE=0
                fi
                
                echo "=== Quality Gate Results ==="
                echo "Total samples: ${'$'}TOTAL_SAMPLES"
                echo "Successful: ${'$'}TOTAL_SUCCESSFUL"
                echo "Failed: ${'$'}TOTAL_FAILED"
                echo "Skipped: ${'$'}TOTAL_SKIPPED"
                echo "Success rate: ${'$'}SUCCESS_RATE%"
                echo ""
                echo "Quality gate criteria:"
                echo "- Minimum success rate: ${'$'}MIN_SUCCESS_RATE%"
                echo "- Maximum failed samples: ${'$'}MAX_FAILED_SAMPLES"
                
                # Evaluate quality gate
                QUALITY_GATE_PASSED=true
                
                if [ ${'$'}SUCCESS_RATE -lt ${'$'}MIN_SUCCESS_RATE ]; then
                    echo "❌ Quality gate FAILED: Success rate (${'$'}SUCCESS_RATE%) below minimum (${'$'}MIN_SUCCESS_RATE%)"
                    QUALITY_GATE_PASSED=false
                fi
                
                if [ ${'$'}TOTAL_FAILED -gt ${'$'}MAX_FAILED_SAMPLES ]; then
                    echo "❌ Quality gate FAILED: Too many failed samples (${'$'}TOTAL_FAILED > ${'$'}MAX_FAILED_SAMPLES)"
                    QUALITY_GATE_PASSED=false
                fi
                
                if [ "${'$'}QUALITY_GATE_PASSED" = true ]; then
                    echo "✅ Quality gate PASSED: All criteria met"
                    echo "##teamcity[setParameter name='env.QUALITY_GATE_STATUS' value='PASSED']"
                else
                    echo "QUALITY_GATE_FAILED"
                    echo "##teamcity[setParameter name='env.QUALITY_GATE_STATUS' value='FAILED']"
                fi
                
                echo "=== Step 4: Quality Gate Evaluation Completed ==="
            """.trimIndent()
        }
    }
    /**
     * Step 5: Report Generation & Notifications
     * Generates comprehensive reports and sends notifications
     * Always runs to ensure reports are available even for failed builds
     */
    private fun BuildSteps.reportGenerationAndNotifications() {
        script {
            name = "Step 5: Report Generation & Notifications"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/bin/bash

                echo "=== Step 5: Report Generation & Notifications ==="
                echo "Generating comprehensive validation reports"
                
                # Create final reports directory
                mkdir -p final-reports
                
                # Generate consolidated report
                cat > final-reports/validation-summary.md <<EOF
# Ktor EAP Validation Report
Generated: $(date)

## Version Information
- Ktor Version: %env.KTOR_VERSION%
- Ktor Compiler Plugin Version: %env.KTOR_COMPILER_PLUGIN_VERSION%
- Kotlin Version: %env.KOTLIN_VERSION%

## Quality Gate Status
Status: %quality.gate.overall.status%
Overall Score: %quality.gate.overall.score%
External Score: %external.gate.score%
Internal Score: %internal.gate.score%
Critical Issues: %quality.gate.total.critical%

## External Validation Results
- Total Samples: %external.validation.total.samples%
- Successful: %external.validation.successful.samples%
- Failed: %external.validation.failed.samples%
- Skipped: %external.validation.skipped.samples%
- Success Rate: %external.validation.success.rate%%

## Internal Validation Results  
- Total Tests: %internal.validation.total.tests%
- Passed: %internal.validation.passed.tests%
- Failed: %internal.validation.failed.tests%
- Error: %internal.validation.error.tests%
- Skipped: %internal.validation.skipped.tests%
- Success Rate: %internal.validation.success.rate%%
- Processed Files: %internal.validation.processed.files%

## Quality Gate Details
- Recommendations: %quality.gate.recommendations%
- Next Steps: %quality.gate.next.steps%
EOF

            # Add failure reasons if any
            if [ "%quality.gate.failure.reasons%" != "" ]; then
                cat >> final-reports/validation-summary.md <<EOF
- Failure Reasons: %quality.gate.failure.reasons%
EOF
            fi

            cat >> final-reports/validation-summary.md <<EOF

## Detailed Results
EOF
            
            # Add external sample results if available
            if [ -f "external-validation-reports/successful-samples.log" ] && [ -s "external-validation-reports/successful-samples.log" ]; then
                echo "### ✅ External Successful Samples" >> final-reports/validation-summary.md
                sed 's/^/- /' external-validation-reports/successful-samples.log >> final-reports/validation-summary.md
                echo "" >> final-reports/validation-summary.md
            fi
            
            if [ -f "external-validation-reports/failed-samples.log" ] && [ -s "external-validation-reports/failed-samples.log" ]; then
                echo "### ❌ External Failed Samples" >> final-reports/validation-summary.md
                sed 's/^/- /' external-validation-reports/failed-samples.log >> final-reports/validation-summary.md
                echo "" >> final-reports/validation-summary.md
            fi
            
            # Add internal sample results if available
            if [ -f "internal-validation-reports/successful-samples.log" ] && [ -s "internal-validation-reports/successful-samples.log" ]; then
                echo "### ✅ Internal Successful Samples" >> final-reports/validation-summary.md
                sed 's/^/- /' internal-validation-reports/successful-samples.log >> final-reports/validation-summary.md
                echo "" >> final-reports/validation-summary.md
            fi
            
            if [ -f "internal-validation-reports/failed-samples.log" ] && [ -s "internal-validation-reports/failed-samples.log" ]; then
                echo "### ❌ Internal Failed Samples" >> final-reports/validation-summary.md
                sed 's/^/- /' internal-validation-reports/failed-samples.log >> final-reports/validation-summary.md
                echo "" >> final-reports/validation-summary.md
            fi
            
            if [ -f "internal-validation-reports/skipped-samples.log" ] && [ -s "internal-validation-reports/skipped-samples.log" ]; then
                echo "### ⚠️ Skipped Samples" >> final-reports/validation-summary.md
                sed 's/^/- /' internal-validation-reports/skipped-samples.log >> final-reports/validation-summary.md
                echo "" >> final-reports/validation-summary.md
            fi
            
            echo "## Build Logs" >> final-reports/validation-summary.md
            echo "Individual build logs are available in the build artifacts." >> final-reports/validation-summary.md
            
            # Display final report
            echo "=== Final Validation Report ==="
            cat final-reports/validation-summary.md
            
            # Generate Slack notification if webhook is configured
            if [ "%system.slack.webhook.url%" != "" ]; then
                echo "📢 Sending Slack notification..."
                
                # Prepare Slack message with proper variable handling
                STATUS_EMOJI="%quality.gate.slack.status.emoji%"
                EXTERNAL_EMOJI="%quality.gate.slack.external.emoji%"
                INTERNAL_EMOJI="%quality.gate.slack.internal.emoji%"
                CRITICAL_EMOJI="%quality.gate.slack.critical.emoji%"
                
                cat > slack-message.json <<SLACK_EOF
{
  "text": "Ktor EAP Validation Report",
  "blocks": [
    {
      "type": "header",
      "text": {
        "type": "plain_text",
        "text": "${'$'}{STATUS_EMOJI} Ktor EAP Validation Report"
      }
    },
    {
      "type": "section",
      "fields": [
        {
          "type": "mrkdwn",
          "text": "*Status:* %quality.gate.overall.status%"
        },
        {
          "type": "mrkdwn",
          "text": "*Overall Score:* %quality.gate.overall.score%"
        },
        {
          "type": "mrkdwn",
          "text": "*External:* ${'$'}{EXTERNAL_EMOJI} %external.gate.score%"
        },
        {
          "type": "mrkdwn",
          "text": "*Internal:* ${'$'}{INTERNAL_EMOJI} %internal.gate.score%"
        },
        {
          "type": "mrkdwn",
          "text": "*Critical Issues:* ${'$'}{CRITICAL_EMOJI} %quality.gate.total.critical%"
        },
        {
          "type": "mrkdwn",
          "text": "*Build:* <%teamcity.serverUrl%/buildConfiguration/%system.teamcity.buildType.id%/%teamcity.build.id%|#%build.number%>"
        }
      ]
    },
    {
      "type": "section",
      "text": {
        "type": "mrkdwn",
        "text": "*Versions:* Ktor %env.KTOR_VERSION%, Plugin %env.KTOR_COMPILER_PLUGIN_VERSION%, Kotlin %env.KOTLIN_VERSION%"
      }
    }
  ]
}
SLACK_EOF
                
                # Send to Slack
                curl -X POST \
                  -H "Content-type: application/json" \
                  --data @slack-message.json \
                  "%system.slack.webhook.url%" || echo "⚠️ Failed to send Slack notification"
                  
                # Clean up
                rm -f slack-message.json
            else
                echo "ℹ️ Slack webhook not configured, skipping notification"
            fi
            
            # Archive all reports
            if command -v tar >/dev/null 2>&1; then
                echo "📦 Creating validation reports archive..."
                tar -czf validation-reports.tar.gz \
                  internal-validation-reports/ \
                  external-validation-reports/ \
                  final-reports/ \
                  2>/dev/null || true
                echo "##teamcity[publishArtifacts 'validation-reports.tar.gz']"
            fi
            
            # Publish individual report files
            echo "##teamcity[publishArtifacts 'final-reports/validation-summary.md']"
            
            echo "=== Step 5: Report Generation & Notifications Completed ==="
        """.trimIndent()
        }
    }
}
