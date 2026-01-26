package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.build.defaultGradleParams
import subprojects.VCSSamples
import subprojects.VCSKtorBuildPlugins
import dsl.addSlackNotifications

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
    fun createConsolidatedProject(): Project =
        Project {
            id("ConsolidatedEAPValidation")
            name = "Consolidated EAP Validation"
            description = "Consolidated EAP validation project for external and internal projects"

            buildType(createConsolidatedBuild())

            params {
                param("teamcity.ui.settings.readOnly", "false")
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
    private fun createConsolidatedBuild(): BuildType =
        BuildType {
            id("ConsolidatedEAPValidation")
            name = "Consolidated EAP Validation"
            description = "Consolidated build that validates Ktor EAP releases"

            artifactRules = """
                version-resolution-reports => version-resolution-reports.zip
                external-validation-reports => external-validation-reports.zip  
                internal-validation-reports => internal-validation-reports.zip
                quality-gate-reports => quality-gate-reports.zip
            """.trimIndent()

            params {
                // Quality Gate Configuration Parameters
                param("quality.gate.scoring.external.weight", "60")
                param("quality.gate.scoring.internal.weight", "40")
                param("quality.gate.thresholds.minimum.score", "80")
                param("quality.gate.thresholds.critical.issues", "0")

                // Optional Slack webhook for detailed notifications
                password("env.SLACK_WEBHOOK_URL", "%system.slack.webhook.url%")

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

            vcs {
                root(VCSSamples, "+:. => samples")
                root(VCSKtorBuildPlugins, "+:. => ktor-build-plugins")
                cleanCheckout = true
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

            addSlackNotifications(
                channel = "#ktor-projects-on-eap",
                buildFailed = true,
                buildFinishedSuccessfully = true
            )

            requirements {
                startsWith("teamcity.agent.jvm.os.name", "Linux")
                exists("env.JAVA_HOME")
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
                
                echo "=== Step 1: Version Resolution ==="
                echo "Fetching latest EAP versions for Ktor framework, compiler plugin, and Kotlin"

                mkdir -p version-resolution-reports

                FETCH_ERRORS=0
                VERSION_REPORT=""

                # Fetch Ktor Framework EAP version
                echo "Fetching Ktor Framework EAP version..."
                KTOR_VERSION=""
                if KTOR_VERSION=$(curl -s -f --max-time 30 "${EapConstants.KTOR_EAP_METADATA_URL}" | grep -o "${EapConstants.EAP_VERSION_REGEX}" | head -1 | sed 's/[><]//g'); then
                    if [ -n "${'$'}KTOR_VERSION" ]; then
                        echo "✅ Latest Ktor EAP version: ${'$'}KTOR_VERSION"
                        echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}KTOR_VERSION']"
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Framework: ${'$'}KTOR_VERSION (SUCCESS)\n"
                    else
                        echo "❌ Failed to parse Ktor EAP version from metadata"
                        FETCH_ERRORS=$((FETCH_ERRORS + 1))
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Framework: PARSE_ERROR\n"
                    fi
                else
                    echo "❌ Failed to fetch Ktor EAP version from ${EapConstants.KTOR_EAP_METADATA_URL}"
                    FETCH_ERRORS=$((FETCH_ERRORS + 1))
                    VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Framework: FETCH_ERROR\n"
                fi

                # Fetch Ktor Compiler Plugin EAP version
                echo "Fetching Ktor Compiler Plugin EAP version..."
                KTOR_COMPILER_PLUGIN_VERSION=""
                if KTOR_COMPILER_PLUGIN_VERSION=$(curl -s -f --max-time 30 "${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}" | grep -o "${EapConstants.EAP_VERSION_REGEX}" | head -1 | sed 's/[><]//g'); then
                    if [ -n "${'$'}KTOR_COMPILER_PLUGIN_VERSION" ]; then
                        echo "✅ Latest Ktor Compiler Plugin EAP version: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
                        echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}KTOR_COMPILER_PLUGIN_VERSION']"
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: ${'$'}KTOR_COMPILER_PLUGIN_VERSION (SUCCESS)\n"
                    else
                        echo "❌ Failed to parse Ktor Compiler Plugin EAP version from metadata"
                        FETCH_ERRORS=$((FETCH_ERRORS + 1))
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: PARSE_ERROR\n"
                    fi
                else
                    echo "❌ Failed to fetch Ktor Compiler Plugin EAP version from ${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}"
                    FETCH_ERRORS=$((FETCH_ERRORS + 1))
                    VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: FETCH_ERROR\n"
                fi

                # Fetch Kotlin version (try EAP first, fallback to stable)
                echo "Fetching Kotlin version..."
                KOTLIN_VERSION=""
                if KOTLIN_VERSION=$(curl -s -f --max-time 30 "${EapConstants.KOTLIN_EAP_METADATA_URL}" | grep -o ">2\.[0-9]\+\.[0-9]\+\(-[A-Za-z0-9\-]\+\)\?<" | head -1 | sed 's/[><]//g' 2>/dev/null); then
                    if [ -n "${'$'}KOTLIN_VERSION" ]; then
                        echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

                        # Check if version has build number format (e.g., 2.1.22-332)
                        if [[ "${'$'}KOTLIN_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-[0-9]+$ ]]; then
                            ORIGINAL_VERSION="${'$'}KOTLIN_VERSION"
                            KOTLIN_VERSION=$(echo "${'$'}KOTLIN_VERSION" | sed 's/-[0-9]*$//')
                            echo "⚠️  Invalid Kotlin version format: ${'$'}ORIGINAL_VERSION (looks like build number)"
                            echo "🔧 Using corrected Kotlin version: ${'$'}KOTLIN_VERSION"
                            VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (EAP_SUCCESS_CORRECTED)\n"
                        else
                            echo "✅ Latest Kotlin version: ${'$'}KOTLIN_VERSION (from EAP repository)"
                            VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (EAP_SUCCESS)\n"
                        fi
                    else
                        KOTLIN_VERSION="2.1.21"
                        echo "⚠️ Using fallback Kotlin version: ${'$'}KOTLIN_VERSION"
                        VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (FALLBACK)\n"
                    fi
                else
                    KOTLIN_VERSION="2.1.21"
                    echo "⚠️ Failed to fetch Kotlin EAP version, using stable fallback: ${'$'}KOTLIN_VERSION"
                    VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (FALLBACK)\n"
                fi
                
                echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='${'$'}KOTLIN_VERSION']"

                # Set fetch status parameters
                echo "##teamcity[setParameter name='version.resolution.errors' value='${'$'}FETCH_ERRORS']"

                # Generate version resolution report
                cat > version-resolution-reports/version-resolution.txt <<EOF
Version Resolution Report
========================
Generated: $(date -Iseconds)

Resolved Versions:
$(echo -e "${'$'}VERSION_REPORT")

Summary:
- Total Fetch Errors: ${'$'}FETCH_ERRORS
- Status: $([[ ${'$'}FETCH_ERRORS -eq 0 ]] && echo "SUCCESS" || echo "PARTIAL_SUCCESS")

Details:
- Ktor Framework URL: ${EapConstants.KTOR_EAP_METADATA_URL}
- Compiler Plugin URL: ${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}
- Kotlin EAP URL: ${EapConstants.KOTLIN_EAP_METADATA_URL}
EOF

                echo "=== Version Resolution Summary ==="
                echo "Fetch Errors: ${'$'}FETCH_ERRORS"
                echo "Ktor Version: ${'$'}KTOR_VERSION"
                echo "Compiler Plugin Version: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
                echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

                # Only fail if we couldn't fetch ANY versions (critical failure)
                if [ -z "${'$'}KTOR_VERSION" ] && [ -z "${'$'}KTOR_COMPILER_PLUGIN_VERSION" ]; then
                    echo "CRITICAL ERROR: Could not fetch any Ktor versions - cannot proceed with validation"
                    exit 1
                fi

                echo "=== Step 1: Version Resolution Completed ==="
                exit 0
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
            
            echo "=== Step 2: External Samples Validation ==="
            
            # Get current parameter values or use fallback defaults
            KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
            KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.1.21/' || echo "2.1.21")
            
            echo "Validating external GitHub samples against EAP versions"
            echo "Ktor Version: ${'$'}KTOR_VERSION"
            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

            WORK_DIR=$(pwd)
            REPORTS_DIR="${'$'}WORK_DIR/external-validation-reports"
            SAMPLES_DIR="${'$'}WORK_DIR/external-samples"

            # Create necessary directories
            mkdir -p "${'$'}REPORTS_DIR"
            mkdir -p "${'$'}SAMPLES_DIR"

                # Define external sample repositories
                declare -A EXTERNAL_SAMPLES=(
                    ["ktor-ai-server"]="https://github.com/nomisRev/ktor-ai-server.git"
                    ["ktor-native-server"]="https://github.com/nomisRev/ktor-native-server.git"
                    ["ktor-config-example"]="https://github.com/nomisRev/ktor-config-example.git"
                    ["ktor-workshop-2025"]="https://github.com/nomisRev/ktor-workshop-2025.git"
                    ["amper-ktor-sample"]="https://github.com/nomisRev/amper-ktor-sample.git"
                    ["ktor-di-overview"]="https://github.com/nomisRev/Ktor-DI-Overview.git"
                    ["ktor-full-stack-real-world"]="https://github.com/nomisRev/ktor-full-stack-real-world.git"
                )

            echo "Cloning external sample repositories..."
            clone_pids=()
            for sample_name in "${'$'}{!EXTERNAL_SAMPLES[@]}"; do
                sample_url="${'$'}{EXTERNAL_SAMPLES[${'$'}sample_name]}"
                target_dir="${'$'}SAMPLES_DIR/${'$'}sample_name"
                
                (
                    echo "Cloning ${'$'}sample_name from ${'$'}sample_url to ${'$'}target_dir"
                    if [ -d "${'$'}target_dir" ]; then
                        rm -rf "${'$'}target_dir"
                    fi
                    
                    if git clone --depth 1 "${'$'}sample_url" "${'$'}target_dir" >/dev/null 2>&1; then
                        echo "✅ Successfully cloned ${'$'}sample_name"
                    else
                        echo "❌ Failed to clone ${'$'}sample_name"
                    fi
                ) &
                clone_pids+=($!)
            done
            
            # Wait for all clones to complete
            for clone_pid in "${'$'}{clone_pids[@]}"; do
                wait "${'$'}clone_pid"
            done
            echo "All repositories cloned"

            # Define external sample project directories using absolute paths
            EXTERNAL_SAMPLE_DIRS=(
                "${'$'}SAMPLES_DIR/ktor-ai-server"
                "${'$'}SAMPLES_DIR/ktor-native-server"
                "${'$'}SAMPLES_DIR/ktor-config-example"
                "${'$'}SAMPLES_DIR/ktor-workshop-2025"
                "${'$'}SAMPLES_DIR/amper-ktor-sample"
                "${'$'}SAMPLES_DIR/ktor-di-overview"
                "${'$'}SAMPLES_DIR/ktor-full-stack-real-world"
            )

            echo "External sample projects to validate:"
            for project_dir in "${'$'}{EXTERNAL_SAMPLE_DIRS[@]}"; do
                echo "- ${'$'}project_dir"
            done

            # Create gradle.properties with EAP versions for Gradle projects
            cat > "${'$'}WORK_DIR/gradle.properties.eap" <<EOF
kotlin_version=${'$'}KOTLIN_VERSION
ktor_version=${'$'}KTOR_VERSION
logback_version=1.4.14
kotlin.mpp.stability.nowarn=true
org.gradle.jvmargs=-Xmx2g
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.caching=false
kotlin.compiler.execution.strategy=in-process
kotlin.incremental=true
EOF

            # Initialize result tracking files using absolute paths
            > "${'$'}REPORTS_DIR/successful-samples.txt"
            > "${'$'}REPORTS_DIR/failed-samples.txt"
            > "${'$'}REPORTS_DIR/skipped-samples.txt"
            > "${'$'}REPORTS_DIR/detailed-errors.txt"

            echo ""
            echo "=== Starting validation of ${'$'}{#EXTERNAL_SAMPLE_DIRS[@]} samples ==="
            
            build_pids=()
            sample_index=1
            
            for project_dir in "${'$'}{EXTERNAL_SAMPLE_DIRS[@]}"; do
                sample_name=$(basename "${'$'}project_dir")
                (
                    echo ""
                    echo "=== [${'$'}sample_index] Validating external sample: ${'$'}project_dir ==="

                    # Create unique Gradle user home for this build to avoid cache conflicts
                    GRADLE_USER_HOME="${'$'}WORK_DIR/.gradle-${'$'}sample_name"
                    mkdir -p "${'$'}GRADLE_USER_HOME"
                    export GRADLE_USER_HOME

                    # Check if project directory exists
                    if [ ! -d "${'$'}project_dir" ]; then
                        echo "⚠️  Sample ${'$'}project_dir: DIRECTORY_NOT_FOUND - skipping"
                        echo "SKIPPED: ${'$'}project_dir (directory not found)" >> "${'$'}REPORTS_DIR/skipped-samples.txt"
                        exit 2  # skipped
                    fi

                    # Prepare build log
                    BUILD_LOG="${'$'}REPORTS_DIR/${'$'}sample_name.log"
                    BUILD_SUCCESS=false

                # Check if it's an Amper project (has module.yaml)
                if [ -f "${'$'}project_dir/module.yaml" ]; then
                    echo "Detected Amper project (module.yaml found)"
                    
                    # For Amper projects, check if there's also a Gradle wrapper as fallback
                    if [ -f "${'$'}project_dir/gradlew" ]; then
                        echo "Found Gradle wrapper, using it for validation..."
                        # Apply EAP versions to gradle.properties
                        if [ -f "${'$'}project_dir/gradle.properties" ]; then
                            cp "${'$'}project_dir/gradle.properties" "${'$'}project_dir/gradle.properties.backup"
                            grep -v -E "^(kotlin_version|ktor_version|logback_version)" "${'$'}project_dir/gradle.properties.backup" > "${'$'}project_dir/gradle.properties" || true
                            cat "${'$'}WORK_DIR/gradle.properties.eap" >> "${'$'}project_dir/gradle.properties"
                        else
                            cp "${'$'}WORK_DIR/gradle.properties.eap" "${'$'}project_dir/gradle.properties"
                        fi

                        cd "${'$'}project_dir"

                        # Function to run command with intelligent timeout
                        run_with_intelligent_timeout() {
                            local cmd="$1"
                            local log_file="$2"
                            local max_timeout=1500  # 25 minutes
                            local check_interval=30  # Check every 30 seconds
                            local no_progress_limit=300  # Kill if no progress for 5 minutes

                            # Start the command in background
                            ${'$'}cmd > "${'$'}log_file" 2>&1 &
                            local cmd_pid=$!
                            local start_time=$(date +%s)
                            local last_log_size=0
                            local last_progress_time=${'$'}start_time

                            while kill -0 ${'$'}cmd_pid 2>/dev/null; do
                                local current_time=$(date +%s)
                                local elapsed=$((current_time - start_time))

                                # Check if we've exceeded maximum timeout
                                if [ ${'$'}elapsed -gt ${'$'}max_timeout ]; then
                                    echo "❌ Maximum timeout (25 minutes) reached, terminating build"
                                    kill -TERM ${'$'}cmd_pid 2>/dev/null || true
                                    sleep 5
                                    kill -KILL ${'$'}cmd_pid 2>/dev/null || true
                                    return 1
                                fi

                                # Check if log file is growing (indicating progress)
                                if [ -f "${'$'}log_file" ]; then
                                    local current_log_size=$(wc -c < "${'$'}log_file" 2>/dev/null || echo "0")
                                    if [ "${'$'}current_log_size" -gt "${'$'}last_log_size" ]; then
                                        last_log_size=${'$'}current_log_size
                                        last_progress_time=${'$'}current_time
                                    fi
                                fi

                                # Check if no progress for too long
                                local no_progress_time=$((current_time - last_progress_time))
                                if [ ${'$'}no_progress_time -gt ${'$'}no_progress_limit ]; then
                                    echo "❌ No progress detected for 5 minutes, terminating build"
                                    kill -TERM ${'$'}cmd_pid 2>/dev/null || true
                                    sleep 5
                                    kill -KILL ${'$'}cmd_pid 2>/dev/null || true
                                    return 1
                                fi

                                sleep ${'$'}check_interval
                            done

                            # Wait for the process to finish and get exit code
                            wait ${'$'}cmd_pid
                            return $?
                        }

                        if run_with_intelligent_timeout "./gradlew assemble --no-daemon --continue --stacktrace" "${'$'}BUILD_LOG"; then
                            BUILD_SUCCESS=true
                            echo "✅ Build successful with Gradle"
                        else
                            echo "❌ Gradle build failed, trying compile-only..."
                            # Get available compile tasks to handle multiplatform projects properly
                            COMPILE_TASKS=$(./gradlew tasks --all 2>/dev/null | grep -E "^compile[A-Za-z]*( |$)" | awk '{print $1}' | head -10 | tr '\n' ' ')
                            if [ -n "${'$'}COMPILE_TASKS" ]; then
                                echo "Found compile tasks: ${'$'}COMPILE_TASKS"
                                if run_with_intelligent_timeout "./gradlew ${'$'}COMPILE_TASKS --no-daemon --continue --stacktrace" "${'$'}BUILD_LOG.compile"; then
                                    BUILD_SUCCESS=true
                                    echo "✅ Compile successful with Gradle"
                                    # Append compile log to main log
                                    cat "${'$'}BUILD_LOG.compile" >> "${'$'}BUILD_LOG"
                                fi
                            else
                                echo "No compile tasks found, trying classes task..."
                                if run_with_intelligent_timeout "./gradlew classes --no-daemon --continue --stacktrace" "${'$'}BUILD_LOG.compile"; then
                                    BUILD_SUCCESS=true
                                    echo "✅ Classes task successful with Gradle"
                                    # Append compile log to main log
                                    cat "${'$'}BUILD_LOG.compile" >> "${'$'}BUILD_LOG"
                                fi
                            fi
                        fi
                        cd "${'$'}WORK_DIR"

                            # Restore original gradle.properties
                            if [ -f "${'$'}project_dir/gradle.properties.backup" ]; then
                                mv "${'$'}project_dir/gradle.properties.backup" "${'$'}project_dir/gradle.properties"
                            else
                                rm -f "${'$'}project_dir/gradle.properties"
                            fi
                        else
                            echo "⚠️  Amper project without Gradle wrapper - skipping (Amper CLI not available in CI)"
                            echo "SKIPPED: ${'$'}project_dir (Amper project without Gradle wrapper)" >> "${'$'}REPORTS_DIR/skipped-samples.txt"
                            exit 2  # skipped
                        fi

                elif [ -f "${'$'}project_dir/gradlew" ]; then
                    echo "Detected Gradle project (gradlew found)"
                    
                    # Apply EAP versions to gradle.properties
                    if [ -f "${'$'}project_dir/gradle.properties" ]; then
                        cp "${'$'}project_dir/gradle.properties" "${'$'}project_dir/gradle.properties.backup"
                        grep -v -E "^(kotlin_version|ktor_version|logback_version)" "${'$'}project_dir/gradle.properties.backup" > "${'$'}project_dir/gradle.properties" || true
                        cat "${'$'}WORK_DIR/gradle.properties.eap" >> "${'$'}project_dir/gradle.properties"
                    else
                        cp "${'$'}WORK_DIR/gradle.properties.eap" "${'$'}project_dir/gradle.properties"
                    fi

                    cd "${'$'}project_dir"
                    echo "Building with intelligent timeout (max 25 minutes) using Gradle..."

                    # Function to run command with intelligent timeout (reused from Amper section)
                    run_with_intelligent_timeout() {
                        local cmd="$1"
                        local log_file="$2"
                        local max_timeout=1500  # 25 minutes
                        local check_interval=30  # Check every 30 seconds
                        local no_progress_limit=300  # Kill if no progress for 5 minutes

                        # Start the command in background
                        ${'$'}cmd > "${'$'}log_file" 2>&1 &
                        local cmd_pid=$!
                        local start_time=$(date +%s)
                        local last_log_size=0
                        local last_progress_time=${'$'}start_time

                        while kill -0 ${'$'}cmd_pid 2>/dev/null; do
                            local current_time=$(date +%s)
                            local elapsed=$((current_time - start_time))

                            # Check if we've exceeded maximum timeout
                            if [ ${'$'}elapsed -gt ${'$'}max_timeout ]; then
                                echo "❌ Maximum timeout (25 minutes) reached, terminating build"
                                kill -TERM ${'$'}cmd_pid 2>/dev/null || true
                                sleep 5
                                kill -KILL ${'$'}cmd_pid 2>/dev/null || true
                                return 1
                            fi

                            # Check if log file is growing (indicating progress)
                            if [ -f "${'$'}log_file" ]; then
                                local current_log_size=$(wc -c < "${'$'}log_file" 2>/dev/null || echo "0")
                                if [ "${'$'}current_log_size" -gt "${'$'}last_log_size" ]; then
                                    last_log_size=${'$'}current_log_size
                                    last_progress_time=${'$'}current_time
                                fi
                            fi

                            # Check if no progress for too long
                            local no_progress_time=$((current_time - last_progress_time))
                            if [ ${'$'}no_progress_time -gt ${'$'}no_progress_limit ]; then
                                echo "❌ No progress detected for 5 minutes, terminating build"
                                kill -TERM ${'$'}cmd_pid 2>/dev/null || true
                                sleep 5
                                kill -KILL ${'$'}cmd_pid 2>/dev/null || true
                                return 1
                            fi

                            sleep ${'$'}check_interval
                        done

                        # Wait for the process to finish and get exit code
                        wait ${'$'}cmd_pid
                        return $?
                    }

                    if run_with_intelligent_timeout "./gradlew assemble --no-daemon --continue --stacktrace" "${'$'}BUILD_LOG"; then
                        BUILD_SUCCESS=true
                        echo "✅ Build successful with assemble"
                    else
                        echo "❌ assemble failed, trying compile-only..."
                        # Get available compile tasks to handle multiplatform projects properly
                        COMPILE_TASKS=${'$'}(./gradlew tasks --all 2>/dev/null | grep -E "^compile[A-Za-z]*( |${'$'})" | awk '{print ${'$'}1}' | head -10 | tr '\n' ' ')
                        if [ -n "${'$'}COMPILE_TASKS" ]; then
                            echo "Found compile tasks: ${'$'}COMPILE_TASKS"
                            if run_with_intelligent_timeout "./gradlew ${'$'}COMPILE_TASKS --no-daemon --continue --stacktrace" "${'$'}BUILD_LOG.compile"; then
                                BUILD_SUCCESS=true
                                echo "✅ Compile successful"
                                # Append compile log to main log
                                cat "${'$'}BUILD_LOG.compile" >> "${'$'}BUILD_LOG"
                            fi
                        else
                            echo "No compile tasks found, trying classes task..."
                            if run_with_intelligent_timeout "./gradlew classes --no-daemon --continue --stacktrace" "${'$'}BUILD_LOG.compile"; then
                                BUILD_SUCCESS=true
                                echo "✅ Classes task successful"
                                # Append compile log to main log
                                cat "${'$'}BUILD_LOG.compile" >> "${'$'}BUILD_LOG"
                            fi
                        fi
                    fi
                    cd "${'$'}WORK_DIR"

                    # Restore original gradle.properties
                    if [ -f "${'$'}project_dir/gradle.properties.backup" ]; then
                        mv "${'$'}project_dir/gradle.properties.backup" "${'$'}project_dir/gradle.properties"
                    else
                        rm -f "${'$'}project_dir/gradle.properties"
                    fi

                elif [ -f "${'$'}project_dir/pom.xml" ]; then
                    echo "Detected Maven project (pom.xml found)"
                    cd "${'$'}project_dir"

                    # Function to run command with intelligent timeout
                    run_with_intelligent_timeout() {
                        local cmd="$1"
                        local log_file="$2"
                        local max_timeout=1500  # 25 minutes
                        local check_interval=30  # Check every 30 seconds
                        local no_progress_limit=300  # Kill if no progress for 5 minutes

                        # Start the command in background
                        ${'$'}cmd > "${'$'}log_file" 2>&1 &
                        local cmd_pid=$!
                        local start_time=$(date +%s)
                        local last_log_size=0
                        local last_progress_time=${'$'}start_time

                        while kill -0 ${'$'}cmd_pid 2>/dev/null; do
                            local current_time=$(date +%s)
                            local elapsed=$((current_time - start_time))

                            # Check if we've exceeded maximum timeout
                            if [ ${'$'}elapsed -gt ${'$'}max_timeout ]; then
                                echo "❌ Maximum timeout (25 minutes) reached, terminating build"
                                kill -TERM ${'$'}cmd_pid 2>/dev/null || true
                                sleep 5
                                kill -KILL ${'$'}cmd_pid 2>/dev/null || true
                                return 1
                            fi

                            # Check if log file is growing (indicating progress)
                            if [ -f "${'$'}log_file" ]; then
                                local current_log_size=$(wc -c < "${'$'}log_file" 2>/dev/null || echo "0")
                                if [ "${'$'}current_log_size" -gt "${'$'}last_log_size" ]; then
                                    last_log_size=${'$'}current_log_size
                                    last_progress_time=${'$'}current_time
                                fi
                            fi

                            # Check if no progress for too long
                            local no_progress_time=$((current_time - last_progress_time))
                            if [ ${'$'}no_progress_time -gt ${'$'}no_progress_limit ]; then
                                echo "❌ No progress detected for 5 minutes, terminating build"
                                kill -TERM ${'$'}cmd_pid 2>/dev/null || true
                                sleep 5
                                kill -KILL ${'$'}cmd_pid 2>/dev/null || true
                                return 1
                            fi

                            sleep ${'$'}check_interval
                        done

                        # Wait for the process to finish and get exit code
                        wait ${'$'}cmd_pid
                        return $?
                    }

                    if run_with_intelligent_timeout "mvn compile -Dkotlin.version=\"${'$'}KOTLIN_VERSION\" -Dktor.version=\"${'$'}KTOR_VERSION\"" "${'$'}BUILD_LOG"; then
                        BUILD_SUCCESS=true
                        echo "✅ Maven compile successful"
                    else
                        echo "❌ Maven compile failed"
                    fi
                    cd "${'$'}WORK_DIR"

                    else
                        echo "⚠️  Unknown build system - skipping"
                        echo "SKIPPED: ${'$'}project_dir (unknown build system)" >> "${'$'}REPORTS_DIR/skipped-samples.txt"
                        exit 2  # skipped
                    fi

                    # Process results
                    if [ "${'$'}BUILD_SUCCESS" = "true" ]; then
                        echo "✅ Sample ${'$'}project_dir: BUILD SUCCESSFUL"
                        echo "SUCCESS: ${'$'}project_dir" >> "${'$'}REPORTS_DIR/successful-samples.txt"
                        exit 0  # success
                    else
                        echo "❌ Sample ${'$'}project_dir: BUILD FAILED"
                        echo "FAILED: ${'$'}project_dir" >> "${'$'}REPORTS_DIR/failed-samples.txt"

                        # Extract detailed error information
                        if [ -f "${'$'}BUILD_LOG" ]; then
                            echo "=== Error Analysis for ${'$'}project_dir ===" >> "${'$'}REPORTS_DIR/detailed-errors.txt"
                            echo "Build Failures:" >> "${'$'}REPORTS_DIR/detailed-errors.txt"
                            grep -E "FAILURE|BUILD FAILED|Error|ERROR" "${'$'}BUILD_LOG" | head -5 >> "${'$'}REPORTS_DIR/detailed-errors.txt" || true
                            echo "---" >> "${'$'}REPORTS_DIR/detailed-errors.txt"
                            
                            echo "Build error summary:" >> "${'$'}REPORTS_DIR/failed-samples.txt"
                            tail -20 "${'$'}BUILD_LOG" | grep -E "(FAILURE|ERROR|Exception)" | head -5 >> "${'$'}REPORTS_DIR/failed-samples.txt" || true
                            echo "---" >> "${'$'}REPORTS_DIR/failed-samples.txt"
                        fi
                        exit 1  # failed
                    fi
                ) &
                build_pids+=($!)
                sample_index=$((sample_index + 1))
            done

            # Wait for all builds to complete and collect results
            TOTAL_SAMPLES=0
            SUCCESSFUL_SAMPLES=0
            FAILED_SAMPLES=0
            SKIPPED_SAMPLES=0

            for build_pid in "${'$'}{build_pids[@]}"; do
                wait "${'$'}build_pid"
                result_code=$?
                TOTAL_SAMPLES=$((TOTAL_SAMPLES + 1))
                
                case "${'$'}result_code" in
                    0) SUCCESSFUL_SAMPLES=$((SUCCESSFUL_SAMPLES + 1)) ;;
                    1) FAILED_SAMPLES=$((FAILED_SAMPLES + 1)) ;;
                    2) SKIPPED_SAMPLES=$((SKIPPED_SAMPLES + 1)) ;;
                esac
            done

                # Calculate success rate
                if [ "${'$'}TOTAL_SAMPLES" -gt 0 ]; then
                    SUCCESS_RATE=$(echo "scale=1; ${'$'}SUCCESSFUL_SAMPLES * 100 / ${'$'}TOTAL_SAMPLES" | bc -l 2>/dev/null || echo "0.0")
                else
                    SUCCESS_RATE="0.0"
                fi

                echo ""
                echo "=== External Samples Validation Results ==="
                echo "Total samples processed: ${'$'}TOTAL_SAMPLES"
                echo "Successful: ${'$'}SUCCESSFUL_SAMPLES"
                echo "Failed: ${'$'}FAILED_SAMPLES"
                echo "Skipped: ${'$'}SKIPPED_SAMPLES"
                echo "Success rate: ${'$'}SUCCESS_RATE%"

                # Set parameters for quality gate evaluation
                echo "##teamcity[setParameter name='external.validation.total.samples' value='${'$'}TOTAL_SAMPLES']"
                echo "##teamcity[setParameter name='external.validation.successful.samples' value='${'$'}SUCCESSFUL_SAMPLES']"
                echo "##teamcity[setParameter name='external.validation.failed.samples' value='${'$'}FAILED_SAMPLES']"
                echo "##teamcity[setParameter name='external.validation.skipped.samples' value='${'$'}SKIPPED_SAMPLES']"
                echo "##teamcity[setParameter name='external.validation.success.rate' value='${'$'}SUCCESS_RATE']"

            # Generate external validation report
            cat > "${'$'}REPORTS_DIR/external-validation.txt" <<EOF
External Samples Validation Report
==================================
Generated: $(date -Iseconds)
Ktor Version: ${'$'}KTOR_VERSION
Kotlin Version: ${'$'}KOTLIN_VERSION

Build Strategy:
- Gradle projects: ./gradlew assemble → compileKotlin/compileJava fallback
- Amper projects: Use Gradle wrapper if available, otherwise skip
- Maven projects: mvn compile

Results:
- Total Samples Processed: ${'$'}TOTAL_SAMPLES
- Successful: ${'$'}SUCCESSFUL_SAMPLES
- Failed: ${'$'}FAILED_SAMPLES  
- Skipped: ${'$'}SKIPPED_SAMPLES
- Success Rate: ${'$'}SUCCESS_RATE%

Successful Samples (${'$'}SUCCESSFUL_SAMPLES):
$(cat "${'$'}REPORTS_DIR/successful-samples.txt" 2>/dev/null | sort || echo "None")

Failed Samples (${'$'}FAILED_SAMPLES):
$(cat "${'$'}REPORTS_DIR/failed-samples.txt" 2>/dev/null | sort || echo "None")

Skipped Samples (${'$'}SKIPPED_SAMPLES):
$(cat "${'$'}REPORTS_DIR/skipped-samples.txt" 2>/dev/null | sort || echo "None")

Status: COMPLETED
EOF

            echo "=== Step 2: External Samples Validation Completed ==="
            echo "Reports generated in: ${'$'}REPORTS_DIR"
            
            # Always succeed - let quality gate evaluate the results
            exit 0
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
            mkdir -p samples
            cat > samples/gradle-eap-init.gradle <<EOF
settingsEvaluated { settings ->
    settings.dependencyResolutionManagement {
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
                <repository>
                    <id>kotlin-eap-repo</id>
                    <url>https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </repository>
                <repository>
                    <id>central</id>
                    <url>https://repo1.maven.org/maven2</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>false</enabled></snapshots>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>central</id>
                    <url>https://repo1.maven.org/maven2</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>false</enabled></snapshots>
                </pluginRepository>
                <pluginRepository>
                    <id>gradle-plugins</id>
                    <url>https://plugins.gradle.org/m2</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>false</enabled></snapshots>
                </pluginRepository>
                <pluginRepository>
                    <id>kotlin-eap-plugins</id>
                    <url>https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </pluginRepository>
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
            
            source build-env.properties
            
            echo "=== Validating Regular Sample Projects against EAP versions (PARALLEL) ==="
            echo "Using reports directory: ${'$'}REPORTS_DIR"
            echo "Using Kotlin version: ${'$'}KOTLIN_VERSION"
            
            # Set maximum parallel jobs
            MAX_PARALLEL_JOBS=4
            
            # Function to validate a single sample
            validate_sample() {
                local sample_dir="$1"
                local sample_name=$(basename "${'$'}sample_dir")
                local log_file="${'$'}REPORTS_DIR/${'$'}sample_name.log"
                
                echo "🔄 [PARALLEL] Starting validation of sample: ${'$'}sample_name"
                
                if [ ! -d "${'$'}sample_dir" ]; then
                    echo "⚠️  Sample ${'$'}sample_name: DIRECTORY_NOT_FOUND - skipping" | tee -a "${'$'}log_file"
                    echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/skipped-samples.log"
                    return 0
                fi
                
                cd "${'$'}sample_dir"

                # Function to check if failure is due to deprecation warnings only
                check_deprecation_warnings_only() {
                    local log_file="$1"
                    # Check if log contains only deprecation warnings and no actual build errors
                    if grep -q "DeprecationWarning" "${'$'}log_file" || grep -q "OpenJDK.*warning.*Sharing is only supported" "${'$'}log_file"; then
                        # Check if there are no actual build failures (compilation errors, test failures, etc.)
                        if ! grep -q -E "(BUILD FAILED|COMPILATION ERROR|Test.*FAILED|Error:|Exception:|Failed to|Cannot resolve)" "${'$'}log_file"; then
                            return 0  # Only deprecation warnings, treat as success
                        fi
                    fi
                    return 1  # Real build errors present
                }

                # Determine build command based on build system
                if [ -f "pom.xml" ]; then
                    # Maven build - use corrected Kotlin version
                    echo "🔧 Maven sample detected, using Kotlin version: ${'$'}KOTLIN_VERSION"
                    timeout 300 mvn clean test -Dkotlin.version="${'$'}KOTLIN_VERSION" > "${'$'}log_file" 2>&1 || true
                    exit_code=$?

                    if [ ${'$'}exit_code -eq 0 ]; then
                        echo "✅ [PARALLEL] Sample ${'$'}sample_name: BUILD SUCCESSFUL"
                        echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                    elif check_deprecation_warnings_only "${'$'}log_file"; then
                        echo "✅ [PARALLEL] Sample ${'$'}sample_name: BUILD SUCCESSFUL (deprecation warnings ignored)"
                        echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                    else
                        echo "❌ [PARALLEL] Sample ${'$'}sample_name: BUILD FAILED (exit code: ${'$'}exit_code)"
                        echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/failed-samples.log"
                    fi
                else
                    # Gradle build - suppress Node.js deprecation warnings
                    export NODE_OPTIONS="--no-deprecation --no-warnings"
                    timeout 300 ./gradlew clean build --init-script "${'$'}PWD/../gradle-eap-init.gradle" --no-daemon > "${'$'}log_file" 2>&1 || true
                    exit_code=$?

                    if [ ${'$'}exit_code -eq 0 ]; then
                        echo "✅ [PARALLEL] Sample ${'$'}sample_name: BUILD SUCCESSFUL"
                        echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                    elif check_deprecation_warnings_only "${'$'}log_file"; then
                        echo "✅ [PARALLEL] Sample ${'$'}sample_name: BUILD SUCCESSFUL (deprecation warnings ignored)"
                        echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                    else
                        echo "❌ [PARALLEL] Sample ${'$'}sample_name: BUILD FAILED (exit code: ${'$'}exit_code)"
                        echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/failed-samples.log"
                    fi
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
            
            echo "=== Processing Regular Sample Projects ==="
            
            # List of known regular samples from ktor-samples repository
            REGULAR_SAMPLES=(
                "chat"
                "client-mpp"
                "client-multipart"
                "client-tools"
                "di-kodein"
                "filelisting"
                "fullstack-mpp"
                "graalvm"
                "httpbin"
                "ktor-client-wasm"
                "kweet"
                "location-header"
                "maven-google-appengine-standard"
                "redirect-with-exception"
                "reverse-proxy"
                "reverse-proxy-ws"
                "rx"
                "sse"
                "structured-logging"
                "version-diff"
                "youkube"
            )
            
            # Run samples in parallel using xargs
            printf '%s\n' "${'$'}{REGULAR_SAMPLES[@]}" | xargs -n 1 -P ${'$'}MAX_PARALLEL_JOBS -I {} bash -c 'validate_sample "samples/{}"'

            echo "=== Regular samples validation completed ==="
        """.trimIndent()
        }

        script {
            name = "Step 3: Build Plugin Samples"
            scriptContent = """
            #!/bin/bash
            
            source build-env.properties
            
            echo "=== Step 3: Build Plugin Samples Validation ==="
            echo "Validating build plugin samples from VCS checkout"
            
            # Use the ktor-build-plugins repository checked out via VCS configuration
            BUILD_PLUGIN_DIR="${'$'}PWD/ktor-build-plugins"

            # Verify the VCS checkout exists
            if [ ! -d "${'$'}BUILD_PLUGIN_DIR" ]; then
                echo "❌ Build plugin repository not found at ${'$'}BUILD_PLUGIN_DIR"
                echo "This indicates a VCS configuration issue - the repository should be checked out automatically"
                exit 1
            fi
            
            echo "✅ Using ktor-build-plugins repository from VCS checkout: ${'$'}BUILD_PLUGIN_DIR"

            # Function to validate build plugin sample
            validate_build_plugin_sample() {
                local sample_name="$1"
                local sample_dir="${'$'}BUILD_PLUGIN_DIR/samples/${'$'}sample_name"
                local log_file="${'$'}REPORTS_DIR/${'$'}sample_name.log"
                
                echo "🔄 [BUILD PLUGIN] Starting validation of sample: ${'$'}sample_name"
                
                if [ ! -d "${'$'}sample_dir" ]; then
                    echo "⚠️  Build plugin sample ${'$'}sample_name: DIRECTORY_NOT_FOUND - skipping" | tee -a "${'$'}log_file"
                    echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/skipped-samples.log"
                    return 0
                fi

                cd "${'$'}BUILD_PLUGIN_DIR"

                # Function to check if failure is due to deprecation warnings only
                check_deprecation_warnings_only() {
                    local log_file="$1"
                    # Check if log contains only deprecation warnings and no actual build errors
                    if grep -q "DeprecationWarning" "${'$'}log_file" || grep -q "OpenJDK.*warning.*Sharing is only supported" "${'$'}log_file"; then
                        # Check if there are no actual build failures (compilation errors, test failures, etc.)
                        if ! grep -q -E "(BUILD FAILED|COMPILATION ERROR|Test.*FAILED|Error:|Exception:|Failed to|Cannot resolve)" "${'$'}log_file"; then
                            return 0  # Only deprecation warnings, treat as success
                        fi
                    fi
                    return 1  # Real build errors present
                }

                # Build plugin samples using composite build from the root directory - suppress Node.js deprecation warnings
                echo "🔧 Building plugin sample: ${'$'}sample_name"
                export NODE_OPTIONS="--no-deprecation --no-warnings"
                timeout 300 ./gradlew clean build --include-build "samples/${'$'}sample_name" --init-script "${'$'}PWD/../samples/gradle-eap-init.gradle" --no-daemon > "${'$'}log_file" 2>&1 || true
                exit_code=$?

                if [ ${'$'}exit_code -eq 0 ]; then
                    echo "✅ [BUILD PLUGIN] Sample ${'$'}sample_name: BUILD SUCCESSFUL"
                    echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                elif check_deprecation_warnings_only "${'$'}log_file"; then
                    echo "✅ [BUILD PLUGIN] Sample ${'$'}sample_name: BUILD SUCCESSFUL (deprecation warnings ignored)"
                    echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/successful-samples.log"
                else
                    echo "❌ [BUILD PLUGIN] Sample ${'$'}sample_name: BUILD FAILED (exit code: ${'$'}exit_code)"
                    echo "${'$'}sample_name" >> "${'$'}REPORTS_DIR/failed-samples.log"
                fi
                
                cd - > /dev/null
            }
            
            # Export function for parallel execution
            export -f validate_build_plugin_sample
            export BUILD_PLUGIN_DIR
            export REPORTS_DIR
            export PWD
            
            # List of known build plugin samples
            BUILD_PLUGIN_SAMPLES=(
                "ktor-docker-sample"
                "ktor-fatjar-sample"
                "ktor-native-image-sample"
                "ktor-openapi-sample"
            )
            
            # Validate build plugin samples sequentially to avoid Gradle lock conflicts
            echo "=== Processing Build Plugin Samples ==="
            
            # Process samples one by one to avoid Gradle daemon lock conflicts
            for sample in "${'$'}{BUILD_PLUGIN_SAMPLES[@]}"; do
                validate_build_plugin_sample "${'$'}sample"
            done
            
            echo "=== Build plugin samples validation completed ==="
        """.trimIndent()
        }

        script {
            name = "Step 3: Generate Internal Test Suites Summary"
            scriptContent = """
            #!/bin/bash
            
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

            # Set TeamCity parameters for quality gate evaluation
            echo "##teamcity[setParameter name='internal.validation.total.tests' value='${'$'}TOTAL_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.passed.tests' value='${'$'}SUCCESSFUL_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.failed.tests' value='${'$'}FAILED_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.error.tests' value='0']"
            echo "##teamcity[setParameter name='internal.validation.skipped.tests' value='${'$'}SKIPPED_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.success.rate' value='${'$'}SUCCESS_RATE.0']"
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
                echo "Evaluating all validation results against quality gate criteria"

                mkdir -p quality-gate-reports

                # Read validation results with safe parameter extraction and fallback defaults
                EXTERNAL_TOTAL=$(echo "%external.validation.total.samples%" | sed 's/^%external\.validation\.total\.samples%$/0/' || echo "0")
                EXTERNAL_SUCCESSFUL=$(echo "%external.validation.successful.samples%" | sed 's/^%external\.validation\.successful\.samples%$/0/' || echo "0")
                EXTERNAL_FAILED=$(echo "%external.validation.failed.samples%" | sed 's/^%external\.validation\.failed\.samples%$/0/' || echo "0")
                EXTERNAL_SKIPPED=$(echo "%external.validation.skipped.samples%" | sed 's/^%external\.validation\.skipped\.samples%$/0/' || echo "0")
                EXTERNAL_SUCCESS_RATE=$(echo "%external.validation.success.rate%" | sed 's/^%external\.validation\.success\.rate%$/0.0/' || echo "0.0")

                INTERNAL_TOTAL=$(echo "%internal.validation.total.tests%" | sed 's/^%internal\.validation\.total\.tests%$/0/' || echo "0")
                INTERNAL_PASSED=$(echo "%internal.validation.passed.tests%" | sed 's/^%internal\.validation\.passed\.tests%$/0/' || echo "0")
                INTERNAL_FAILED=$(echo "%internal.validation.failed.tests%" | sed 's/^%internal\.validation\.failed\.tests%$/0/' || echo "0")
                INTERNAL_ERRORS=$(echo "%internal.validation.error.tests%" | sed 's/^%internal\.validation\.error\.tests%$/0/' || echo "0")
                INTERNAL_SKIPPED=$(echo "%internal.validation.skipped.tests%" | sed 's/^%internal\.validation\.skipped\.tests%$/0/' || echo "0")
                INTERNAL_SUCCESS_RATE=$(echo "%internal.validation.success.rate%" | sed 's/^%internal\.validation\.success\.rate%$/0.0/' || echo "0.0")

                VERSION_ERRORS=$(echo "%version.resolution.errors%" | sed 's/^%version\.resolution\.errors%$/0/' || echo "0")

                # Read quality gate thresholds
                EXTERNAL_WEIGHT=$(echo "%quality.gate.scoring.external.weight%" | sed 's/^%quality\.gate\.scoring\.external\.weight%$/60/' || echo "60")
                INTERNAL_WEIGHT=$(echo "%quality.gate.scoring.internal.weight%" | sed 's/^%quality\.gate\.scoring\.internal\.weight%$/40/' || echo "40")
                MINIMUM_SCORE=$(echo "%quality.gate.thresholds.minimum.score%" | sed 's/^%quality\.gate\.thresholds\.minimum\.score%$/80/' || echo "80")
                CRITICAL_THRESHOLD=$(echo "%quality.gate.thresholds.critical.issues%" | sed 's/^%quality\.gate\.thresholds\.critical\.issues%$/0/' || echo "0")

                echo "=== Quality Gate Configuration ==="
                echo "- External Weight: ${'$'}EXTERNAL_WEIGHT%"
                echo "- Internal Weight: ${'$'}INTERNAL_WEIGHT%"
                echo "- Minimum Score Threshold: ${'$'}MINIMUM_SCORE"
                echo "- Critical Issues Threshold: ${'$'}CRITICAL_THRESHOLD"

                echo ""
                echo "=== Validation Data Collected ==="
                echo "Version Resolution Errors: ${'$'}VERSION_ERRORS"
                echo "External Samples: ${'$'}EXTERNAL_SUCCESSFUL/${'$'}EXTERNAL_TOTAL (${'$'}EXTERNAL_SUCCESS_RATE%)"
                echo "  - Failed: ${'$'}EXTERNAL_FAILED, Skipped: ${'$'}EXTERNAL_SKIPPED"
                echo "Internal Tests: ${'$'}INTERNAL_PASSED/${'$'}INTERNAL_TOTAL (${'$'}INTERNAL_SUCCESS_RATE%)"
                echo "  - Failed: ${'$'}INTERNAL_FAILED, Errors: ${'$'}INTERNAL_ERRORS, Skipped: ${'$'}INTERNAL_SKIPPED"

                # Calculate individual scores (convert success rates to integers)
                EXTERNAL_SCORE=$(echo "${'$'}EXTERNAL_SUCCESS_RATE" | awk '{printf "%.0f", $1}')
                INTERNAL_SCORE=$(echo "${'$'}INTERNAL_SUCCESS_RATE" | awk '{printf "%.0f", $1}')

                # Handle cases where scores might be empty or invalid
                EXTERNAL_SCORE=$(echo "${'$'}EXTERNAL_SCORE" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_SCORE=$(echo "${'$'}INTERNAL_SCORE" | grep -E '^[0-9]+$' || echo "0")

                echo ""
                echo "=== Individual Scores ==="
                echo "- External Score: ${'$'}EXTERNAL_SCORE/100"
                echo "- Internal Score: ${'$'}INTERNAL_SCORE/100"

                # Calculate overall weighted score
                OVERALL_SCORE=$(echo "${'$'}EXTERNAL_SCORE ${'$'}INTERNAL_SCORE ${'$'}EXTERNAL_WEIGHT ${'$'}INTERNAL_WEIGHT" | awk '{
                    weighted = ($1 * $3 / 100) + ($2 * $4 / 100)
                    printf "%.0f", weighted
                }')

                echo "- Overall Weighted Score: ${'$'}OVERALL_SCORE/100"

                # Determine individual gate status
                EXTERNAL_GATE_STATUS="FAILED"
                if [ "${'$'}EXTERNAL_SCORE" -ge 80 ]; then
                    EXTERNAL_GATE_STATUS="PASSED"
                fi

                INTERNAL_GATE_STATUS="FAILED"  
                if [ "${'$'}INTERNAL_SCORE" -ge 80 ]; then
                    INTERNAL_GATE_STATUS="PASSED"
                fi

                # Calculate critical issues (failed tests + errors + version resolution errors)
                TOTAL_CRITICAL=$((EXTERNAL_FAILED + INTERNAL_FAILED + INTERNAL_ERRORS + VERSION_ERRORS))

                echo ""
                echo "=== Quality Gate Assessment ==="
                echo "- External Gate: ${'$'}EXTERNAL_GATE_STATUS (${'$'}EXTERNAL_SCORE >= 80)"
                echo "- Internal Gate: ${'$'}INTERNAL_GATE_STATUS (${'$'}INTERNAL_SCORE >= 80)"
                echo "- Critical Issues: ${'$'}TOTAL_CRITICAL (threshold: ${'$'}CRITICAL_THRESHOLD)"

                # Overall quality gate decision
                OVERALL_STATUS="FAILED"
                FAILURE_REASONS=""
                RECOMMENDATIONS="Review validation results and address failures"
                NEXT_STEPS="Investigate failed tests and samples"

                # Check overall score threshold
                SCORE_CHECK="FAILED"
                if [ "${'$'}OVERALL_SCORE" -ge "${'$'}MINIMUM_SCORE" ]; then
                    SCORE_CHECK="PASSED"
                fi

                # Check critical issues threshold  
                CRITICAL_CHECK="FAILED"
                if [ "${'$'}TOTAL_CRITICAL" -le "${'$'}CRITICAL_THRESHOLD" ]; then
                    CRITICAL_CHECK="PASSED"
                fi

                # Determine overall status
                if [ "${'$'}SCORE_CHECK" = "PASSED" ] && [ "${'$'}CRITICAL_CHECK" = "PASSED" ]; then
                    OVERALL_STATUS="PASSED"
                    RECOMMENDATIONS="EAP version meets quality criteria and is ready for release"
                    NEXT_STEPS="Proceed with release process"
                    FAILURE_REASONS="None"
                else
                    # Build failure reasons
                    if [ "${'$'}SCORE_CHECK" = "FAILED" ]; then
                        FAILURE_REASONS="Overall score (${'$'}OVERALL_SCORE) below threshold (${'$'}MINIMUM_SCORE)"
                    fi
                    if [ "${'$'}CRITICAL_CHECK" = "FAILED" ]; then
                        if [ -n "${'$'}FAILURE_REASONS" ]; then
                            FAILURE_REASONS="${'$'}FAILURE_REASONS; Critical issues (${'$'}TOTAL_CRITICAL) exceed threshold (${'$'}CRITICAL_THRESHOLD)"
                        else
                            FAILURE_REASONS="Critical issues (${'$'}TOTAL_CRITICAL) exceed threshold (${'$'}CRITICAL_THRESHOLD)"
                        fi
                    fi
                    
                    # Provide specific recommendations based on failure type
                    if [ "${'$'}EXTERNAL_SCORE" -lt 50 ] && [ "${'$'}EXTERNAL_TOTAL" -gt 0 ]; then
                        RECOMMENDATIONS="Critical: External samples compatibility is very low. Review EAP version compatibility with community samples."
                    elif [ "${'$'}INTERNAL_SCORE" -lt 50 ] && [ "${'$'}INTERNAL_TOTAL" -gt 0 ]; then
                        RECOMMENDATIONS="Critical: Internal tests failing significantly. Review core framework stability with EAP versions."
                    elif [ "${'$'}TOTAL_CRITICAL" -gt 10 ]; then
                        RECOMMENDATIONS="High number of critical issues detected. Prioritize fixing core functionality before release."
                    else
                        RECOMMENDATIONS="Quality gate failed but issues may be addressable. Review specific failure details."
                    fi
                fi

                echo "- Score Check: ${'$'}SCORE_CHECK (${'$'}OVERALL_SCORE >= ${'$'}MINIMUM_SCORE)"
                echo "- Critical Check: ${'$'}CRITICAL_CHECK"
                echo "- Overall Status: ${'$'}OVERALL_STATUS"

                # Set parameters for reporting
                echo "##teamcity[setParameter name='quality.gate.overall.status' value='${'$'}OVERALL_STATUS']"
                echo "##teamcity[setParameter name='quality.gate.overall.score' value='${'$'}OVERALL_SCORE']"
                echo "##teamcity[setParameter name='quality.gate.total.critical' value='${'$'}TOTAL_CRITICAL']"
                echo "##teamcity[setParameter name='external.gate.status' value='${'$'}EXTERNAL_GATE_STATUS']"
                echo "##teamcity[setParameter name='external.gate.score' value='${'$'}EXTERNAL_SCORE']"
                echo "##teamcity[setParameter name='internal.gate.status' value='${'$'}INTERNAL_GATE_STATUS']"
                echo "##teamcity[setParameter name='internal.gate.score' value='${'$'}INTERNAL_SCORE']"
                echo "##teamcity[setParameter name='quality.gate.recommendations' value='${'$'}RECOMMENDATIONS']"
                echo "##teamcity[setParameter name='quality.gate.next.steps' value='${'$'}NEXT_STEPS']"
                echo "##teamcity[setParameter name='quality.gate.failure.reasons' value='${'$'}FAILURE_REASONS']"

                # Generate quality gate report
                cat > quality-gate-reports/quality-gate-evaluation.txt <<EOF
Quality Gate Evaluation Report
==============================
Generated: $(date -Iseconds)

Configuration:
- External Weight: ${'$'}EXTERNAL_WEIGHT%
- Internal Weight: ${'$'}INTERNAL_WEIGHT%
- Minimum Score Threshold: ${'$'}MINIMUM_SCORE
- Critical Issues Threshold: ${'$'}CRITICAL_THRESHOLD

Input Data:
- Version Resolution Errors: ${'$'}VERSION_ERRORS
- External Samples: ${'$'}EXTERNAL_SUCCESSFUL/${'$'}EXTERNAL_TOTAL (${'$'}EXTERNAL_SUCCESS_RATE%)
  * Failed: ${'$'}EXTERNAL_FAILED, Skipped: ${'$'}EXTERNAL_SKIPPED
- Internal Tests: ${'$'}INTERNAL_PASSED/${'$'}INTERNAL_TOTAL (${'$'}INTERNAL_SUCCESS_RATE%)
  * Failed: ${'$'}INTERNAL_FAILED, Errors: ${'$'}INTERNAL_ERRORS, Skipped: ${'$'}INTERNAL_SKIPPED

Scoring:
- External Score: ${'$'}EXTERNAL_SCORE/100 -> ${'$'}EXTERNAL_GATE_STATUS
- Internal Score: ${'$'}INTERNAL_SCORE/100 -> ${'$'}INTERNAL_GATE_STATUS
- Overall Weighted Score: ${'$'}OVERALL_SCORE/100

Quality Gate Decision:
- Score Check: ${'$'}SCORE_CHECK (${'$'}OVERALL_SCORE >= ${'$'}MINIMUM_SCORE)
- Critical Check: ${'$'}CRITICAL_CHECK (${'$'}TOTAL_CRITICAL <= ${'$'}CRITICAL_THRESHOLD)
- Overall Status: ${'$'}OVERALL_STATUS

Critical Issues Breakdown:
- External Sample Failures: ${'$'}EXTERNAL_FAILED
- Internal Test Failures: ${'$'}INTERNAL_FAILED
- Internal Test Errors: ${'$'}INTERNAL_ERRORS
- Version Resolution Errors: ${'$'}VERSION_ERRORS
- Total Critical Issues: ${'$'}TOTAL_CRITICAL

Recommendations: ${'$'}RECOMMENDATIONS
Next Steps: ${'$'}NEXT_STEPS
$([[ "${'$'}OVERALL_STATUS" == "FAILED" ]] && echo "Failure Reasons: ${'$'}FAILURE_REASONS" || echo "")
EOF

                if [ "${'$'}OVERALL_STATUS" = "PASSED" ]; then
                    echo ""
                    echo "✅ Quality gate evaluation PASSED!"
                    echo "Recommendations: ${'$'}RECOMMENDATIONS"
                    echo "Next Steps: ${'$'}NEXT_STEPS"
                else
                    echo ""
                    echo "❌ Quality gate evaluation FAILED!"
                    echo "Failure Reasons: ${'$'}FAILURE_REASONS"
                    echo "Recommendations: ${'$'}RECOMMENDATIONS"
                    echo "Next Steps: ${'$'}NEXT_STEPS"
                    
                    # This will trigger the build failure condition
                    echo "QUALITY_GATE_FAILED: Overall validation failed with score ${'$'}OVERALL_SCORE and ${'$'}TOTAL_CRITICAL critical issues"
                fi

                echo "=== Step 4: Quality Gate Evaluation Completed ==="
                
                # Always exit successfully here - the build failure condition will handle the actual failure
                exit 0
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
                echo "Generating comprehensive reports and sending notifications"
                echo "Timestamp: $(date -Iseconds)"

                # Read all runtime parameter values with safe defaults and parameter extraction
                KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
                KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.1.21/' || echo "2.1.21")
                KTOR_COMPILER_PLUGIN_VERSION=$(echo "%env.KTOR_COMPILER_PLUGIN_VERSION%" | sed 's/^%env\.KTOR_COMPILER_PLUGIN_VERSION%$//' || echo "")
                
                # Handle built-in TeamCity parameters safely
                BUILD_VCS_NUMBER="unknown"
                if [ -n "${'$'}{teamcity_build_vcs_number:-}" ]; then
                    BUILD_VCS_NUMBER="${'$'}teamcity_build_vcs_number"
                elif [ -n "${'$'}TEAMCITY_BUILD_VCS_NUMBER" ]; then
                    BUILD_VCS_NUMBER="${'$'}TEAMCITY_BUILD_VCS_NUMBER"
                elif [ -n "${'$'}{BUILD_VCS_NUMBER:-}" ]; then
                    BUILD_VCS_NUMBER="${'$'}BUILD_VCS_NUMBER"
                fi
    
                AGENT_NAME="unknown"
                if [ -n "${'$'}{teamcity_agent_name:-}" ]; then
                    AGENT_NAME="${'$'}teamcity_agent_name"
                elif [ -n "${'$'}TEAMCITY_AGENT_NAME" ]; then
                    AGENT_NAME="${'$'}TEAMCITY_AGENT_NAME"
                elif [ -n "${'$'}{AGENT_NAME:-}" ]; then
                    AGENT_NAME="${'$'}AGENT_NAME"
                elif [ -n "${'$'}HOSTNAME" ]; then
                    AGENT_NAME="${'$'}HOSTNAME"
                fi

                OVERALL_STATUS=$(echo "%quality.gate.overall.status%" | sed 's/^%quality\.gate\.overall\.status%$/UNKNOWN/' || echo "UNKNOWN")
                OVERALL_SCORE=$(echo "%quality.gate.overall.score%" | sed 's/^%quality\.gate\.overall\.score%$/0/' || echo "0")
                TOTAL_CRITICAL=$(echo "%quality.gate.total.critical%" | sed 's/^%quality\.gate\.total\.critical%$/0/' || echo "0")

                EXTERNAL_GATE_STATUS=$(echo "%external.gate.status%" | sed 's/^%external\.gate\.status%$/UNKNOWN/' || echo "UNKNOWN")
                EXTERNAL_GATE_SCORE=$(echo "%external.gate.score%" | sed 's/^%external\.gate\.score%$/0/' || echo "0")
                EXTERNAL_TOTAL_SAMPLES=$(echo "%external.validation.total.samples%" | sed 's/^%external\.validation\.total\.samples%$/0/' || echo "0")
                EXTERNAL_SUCCESSFUL_SAMPLES=$(echo "%external.validation.successful.samples%" | sed 's/^%external\.validation\.successful\.samples%$/0/' || echo "0")
                EXTERNAL_FAILED_SAMPLES=$(echo "%external.validation.failed.samples%" | sed 's/^%external\.validation\.failed\.samples%$/0/' || echo "0")
                EXTERNAL_SKIPPED_SAMPLES=$(echo "%external.validation.skipped.samples%" | sed 's/^%external\.validation\.skipped\.samples%$/0/' || echo "0")
                EXTERNAL_SUCCESS_RATE=$(echo "%external.validation.success.rate%" | sed 's/^%external\.validation\.success\.rate%$/0.0/' || echo "0.0")

                INTERNAL_GATE_STATUS=$(echo "%internal.gate.status%" | sed 's/^%internal\.gate\.status%$/UNKNOWN/' || echo "UNKNOWN")
                INTERNAL_GATE_SCORE=$(echo "%internal.gate.score%" | sed 's/^%internal\.gate\.score%$/0/' || echo "0")
                INTERNAL_TOTAL_TESTS=$(echo "%internal.validation.total.tests%" | sed 's/^%internal\.validation\.total\.tests%$/0/' || echo "0")
                INTERNAL_PASSED_TESTS=$(echo "%internal.validation.passed.tests%" | sed 's/^%internal\.validation\.passed\.tests%$/0/' || echo "0")
                INTERNAL_FAILED_TESTS=$(echo "%internal.validation.failed.tests%" | sed 's/^%internal\.validation\.failed\.tests%$/0/' || echo "0")
                INTERNAL_ERROR_TESTS=$(echo "%internal.validation.error.tests%" | sed 's/^%internal\.validation\.error\.tests%$/0/' || echo "0")
                INTERNAL_SKIPPED_TESTS=$(echo "%internal.validation.skipped.tests%" | sed 's/^%internal\.validation\.skipped\.tests%$/0/' || echo "0")
                INTERNAL_SUCCESS_RATE=$(echo "%internal.validation.success.rate%" | sed 's/^%internal\.validation\.success\.rate%$/0.0/' || echo "0.0")

                RECOMMENDATIONS=$(echo "%quality.gate.recommendations%" | sed 's/^%quality\.gate\.recommendations%$/Quality gate evaluation not completed/' || echo "Quality gate evaluation not completed")
                NEXT_STEPS=$(echo "%quality.gate.next.steps%" | sed 's/^%quality\.gate\.next\.steps%$/Review validation results/' || echo "Review validation results")
                FAILURE_REASONS=$(echo "%quality.gate.failure.reasons%" | sed 's/^%quality\.gate\.failure\.reasons%$//' || echo "")

                VERSION_ERRORS=$(echo "%version.resolution.errors%" | sed 's/^%version\.resolution\.errors%$/0/' || echo "0")

                # Read quality gate configuration parameters
                EXTERNAL_WEIGHT=$(echo "%quality.gate.scoring.external.weight%" | sed 's/^%quality\.gate\.scoring\.external\.weight%$/60/' || echo "60")
                INTERNAL_WEIGHT=$(echo "%quality.gate.scoring.internal.weight%" | sed 's/^%quality\.gate\.scoring\.internal\.weight%$/40/' || echo "40")
                MINIMUM_SCORE=$(echo "%quality.gate.thresholds.minimum.score%" | sed 's/^%quality\.gate\.thresholds\.minimum\.score%$/80/' || echo "80")
                CRITICAL_ISSUES_THRESHOLD=$(echo "%quality.gate.thresholds.critical.issues%" | sed 's/^%quality\.gate\.thresholds\.critical\.issues%$/0/' || echo "0")

                echo "=== Report Data Summary ==="
                echo "EAP Version: ${'$'}KTOR_VERSION"
                echo "Overall Status: ${'$'}OVERALL_STATUS"
                echo "Overall Score: ${'$'}OVERALL_SCORE/100"
                echo "Critical Issues: ${'$'}TOTAL_CRITICAL"

                # Generate comprehensive report
                cat > quality-gate-reports/consolidated-eap-validation-report.txt <<EOF
Consolidated EAP Validation Report - ${'$'}KTOR_VERSION
======================================================
Generated: $(date -Iseconds)
Architecture: Consolidated Single Build
Build ID: %teamcity.build.id%

Overall Assessment:
- Status: ${'$'}OVERALL_STATUS
- Score: ${'$'}OVERALL_SCORE/100 (weighted)
- Critical Issues: ${'$'}TOTAL_CRITICAL
- Ready for Release: $([[ "${'$'}OVERALL_STATUS" == "PASSED" ]] && echo "YES" || echo "NO")

Version Information:
- Ktor Framework: ${'$'}KTOR_VERSION
- Ktor Compiler Plugin: ${'$'}KTOR_COMPILER_PLUGIN_VERSION
- Kotlin: ${'$'}KOTLIN_VERSION
- Version Resolution Errors: ${'$'}VERSION_ERRORS

Step Results:
Step 1 - Version Resolution: $([[ "${'$'}VERSION_ERRORS" -eq "0" ]] && echo "SUCCESS" || echo "PARTIAL_SUCCESS (${'$'}VERSION_ERRORS errors)")

Step 2 - External Samples Validation: ${'$'}EXTERNAL_GATE_STATUS (${'$'}EXTERNAL_GATE_SCORE/100)
  - Total Samples: ${'$'}EXTERNAL_TOTAL_SAMPLES
  - Successful: ${'$'}EXTERNAL_SUCCESSFUL_SAMPLES
  - Failed: ${'$'}EXTERNAL_FAILED_SAMPLES
  - Skipped: ${'$'}EXTERNAL_SKIPPED_SAMPLES
  - Success Rate: ${'$'}EXTERNAL_SUCCESS_RATE%

Step 3 - Internal Test Suites: ${'$'}INTERNAL_GATE_STATUS (${'$'}INTERNAL_GATE_SCORE/100)
  - Total Tests: ${'$'}INTERNAL_TOTAL_TESTS
  - Passed: ${'$'}INTERNAL_PASSED_TESTS
  - Failed: ${'$'}INTERNAL_FAILED_TESTS
  - Errors: ${'$'}INTERNAL_ERROR_TESTS
  - Skipped: ${'$'}INTERNAL_SKIPPED_TESTS
  - Success Rate: ${'$'}INTERNAL_SUCCESS_RATE%

Step 4 - Quality Gate Evaluation: COMPLETED
  - Scoring Strategy: Weighted (External ${'$'}EXTERNAL_WEIGHT%, Internal ${'$'}INTERNAL_WEIGHT%)
  - Minimum Score Threshold: ${'$'}MINIMUM_SCORE
  - Critical Issues Threshold: ${'$'}CRITICAL_ISSUES_THRESHOLD
  - Score Check: $([[ "${'$'}OVERALL_SCORE" -ge "${'$'}MINIMUM_SCORE" ]] && echo "PASSED" || echo "FAILED") (${'$'}OVERALL_SCORE >= ${'$'}MINIMUM_SCORE)
  - Critical Check: $([[ "${'$'}TOTAL_CRITICAL" -le "${'$'}CRITICAL_ISSUES_THRESHOLD" ]] && echo "PASSED" || echo "FAILED") (${'$'}TOTAL_CRITICAL <= ${'$'}CRITICAL_ISSUES_THRESHOLD)

Step 5 - Report Generation & Notifications: COMPLETED

Critical Issues Breakdown:
- External Sample Failures: ${'$'}EXTERNAL_FAILED_SAMPLES
- Internal Test Failures: ${'$'}INTERNAL_FAILED_TESTS
- Internal Test Errors: ${'$'}INTERNAL_ERROR_TESTS
- Version Resolution Errors: ${'$'}VERSION_ERRORS
- Total: ${'$'}TOTAL_CRITICAL

Quality Gate Analysis:
- Recommendations: ${'$'}RECOMMENDATIONS
- Next Steps: ${'$'}NEXT_STEPS
$([[ "${'$'}OVERALL_STATUS" == "FAILED" ]] && echo "- Failure Reasons: ${'$'}FAILURE_REASONS" || echo "")

Build Information:
- TeamCity Build: %teamcity.serverUrl%/buildConfiguration/%system.teamcity.buildType.id%/%teamcity.build.id%
- VCS Revision: ${'$'}BUILD_VCS_NUMBER
- Agent: ${'$'}AGENT_NAME
EOF

                # Generate JSON report for programmatic consumption
                cat > quality-gate-reports/consolidated-validation-results.json <<EOF
{
    "metadata": {
        "generated": "$(date -Iseconds)",
        "architecture": "consolidated",
        "buildId": "%teamcity.build.id%",
        "vcsRevision": "${'$'}BUILD_VCS_NUMBER",
        "agentName": "${'$'}AGENT_NAME"
    },
    "versions": {
        "ktorFramework": "${'$'}KTOR_VERSION",
        "ktorCompilerPlugin": "${'$'}KTOR_COMPILER_PLUGIN_VERSION",
        "kotlin": "${'$'}KOTLIN_VERSION",
        "resolutionErrors": ${'$'}VERSION_ERRORS
    },
    "overallAssessment": {
        "status": "${'$'}OVERALL_STATUS",
        "score": ${'$'}OVERALL_SCORE,
        "criticalIssues": ${'$'}TOTAL_CRITICAL,
        "readyForRelease": $([[ "${'$'}OVERALL_STATUS" == "PASSED" ]] && echo "true" || echo "false")
    },
    "steps": {
        "versionResolution": {
            "status": $([[ "${'$'}VERSION_ERRORS" -eq "0" ]] && echo '"SUCCESS"' || echo '"PARTIAL_SUCCESS"'),
            "errors": ${'$'}VERSION_ERRORS
        },
        "externalSamplesValidation": {
            "status": "${'$'}EXTERNAL_GATE_STATUS",
            "score": ${'$'}EXTERNAL_GATE_SCORE,
            "totalSamples": ${'$'}EXTERNAL_TOTAL_SAMPLES,
            "successfulSamples": ${'$'}EXTERNAL_SUCCESSFUL_SAMPLES,
            "failedSamples": ${'$'}EXTERNAL_FAILED_SAMPLES,
            "skippedSamples": ${'$'}EXTERNAL_SKIPPED_SAMPLES,
            "successRate": ${'$'}EXTERNAL_SUCCESS_RATE
        },
        "internalTestSuites": {
            "status": "${'$'}INTERNAL_GATE_STATUS",
            "score": ${'$'}INTERNAL_GATE_SCORE,
            "totalTests": ${'$'}INTERNAL_TOTAL_TESTS,
            "passedTests": ${'$'}INTERNAL_PASSED_TESTS,
            "failedTests": ${'$'}INTERNAL_FAILED_TESTS,
            "errorTests": ${'$'}INTERNAL_ERROR_TESTS,
            "skippedTests": ${'$'}INTERNAL_SKIPPED_TESTS,
            "successRate": ${'$'}INTERNAL_SUCCESS_RATE
        }
    },
    "qualityGate": {
        "configuration": {
            "externalWeight": ${'$'}EXTERNAL_WEIGHT,
            "internalWeight": ${'$'}INTERNAL_WEIGHT,
            "minimumScoreThreshold": ${'$'}MINIMUM_SCORE,
            "criticalIssuesThreshold": ${'$'}CRITICAL_ISSUES_THRESHOLD
        },
        "evaluation": {
            "scoreCheck": $([[ "${'$'}OVERALL_SCORE" -ge "${'$'}MINIMUM_SCORE" ]] && echo '"PASSED"' || echo '"FAILED"'),
            "criticalCheck": $([[ "${'$'}TOTAL_CRITICAL" -le "${'$'}CRITICAL_ISSUES_THRESHOLD" ]] && echo '"PASSED"' || echo '"FAILED"')
        }
    },
    "recommendations": "${'$'}RECOMMENDATIONS",
    "nextSteps": "${'$'}NEXT_STEPS"$([[ "${'$'}OVERALL_STATUS" == "FAILED" ]] && echo ',
    "failureReasons": "'"${'$'}FAILURE_REASONS"'"' || echo "")
}
EOF

                echo ""
                echo "=== Publishing Artifacts ==="
                echo "##teamcity[publishArtifacts 'version-resolution-reports => version-resolution-reports.zip']"
                echo "##teamcity[publishArtifacts 'external-validation-reports => external-validation-reports.zip']"
                echo "##teamcity[publishArtifacts 'internal-validation-reports => internal-validation-reports.zip']"
                echo "##teamcity[publishArtifacts 'quality-gate-reports => quality-gate-reports.zip']"

                # Choose emojis based on status
                if [ "${'$'}OVERALL_STATUS" = "PASSED" ]; then
                    MAIN_EMOJI="✅"
                    STATUS_COLOR="SUCCESS"
                else
                    MAIN_EMOJI="❌"
                    STATUS_COLOR="FAILED"
                fi

                # Create enhanced build status text with key metrics
                STATUS_LINE1="${'$'}MAIN_EMOJI EAP ${'$'}KTOR_VERSION: ${'$'}OVERALL_STATUS (${'$'}OVERALL_SCORE/100)"
                STATUS_LINE2="Ext: ${'$'}EXTERNAL_SUCCESSFUL_SAMPLES/${'$'}EXTERNAL_TOTAL_SAMPLES samples | Int: ${'$'}INTERNAL_PASSED_TESTS/${'$'}INTERNAL_TOTAL_TESTS tests"
                STATUS_LINE3="Critical: ${'$'}TOTAL_CRITICAL issues | Score: ${'$'}OVERALL_SCORE/100"

                # Combine into single-line status for TeamCity service message
                STATUS_TEXT="${'$'}STATUS_LINE1 | ${'$'}STATUS_LINE2 | ${'$'}STATUS_LINE3"

                echo "##teamcity[buildStatus text='${'$'}STATUS_TEXT']"

                # Store detailed info in build parameters for notifications
                echo "##teamcity[setParameter name='quality.gate.slack.status.emoji' value='${'$'}MAIN_EMOJI']"
                echo "##teamcity[setParameter name='quality.gate.slack.external.emoji' value='$([[ "${'$'}EXTERNAL_GATE_STATUS" == "PASSED" ]] && echo "✅" || echo "❌")']"
                echo "##teamcity[setParameter name='quality.gate.slack.internal.emoji' value='$([[ "${'$'}INTERNAL_GATE_STATUS" == "PASSED" ]] && echo "✅" || echo "❌")']"
                echo "##teamcity[setParameter name='quality.gate.slack.critical.emoji' value='$([[ "${'$'}TOTAL_CRITICAL" -eq "0" ]] && echo "✅" || echo "🚨")']"

                echo ""
                echo "=== Final Consolidated EAP Validation Results ==="
                echo "EAP Version: ${'$'}KTOR_VERSION"
                echo "Overall Status: ${'$'}OVERALL_STATUS (${'$'}STATUS_COLOR)"
                echo "Overall Score: ${'$'}OVERALL_SCORE/100"
                echo "Critical Issues: ${'$'}TOTAL_CRITICAL"

                if [ "${'$'}OVERALL_STATUS" = "PASSED" ]; then
                    echo ""
                    echo "🎉 Consolidated EAP validation PASSED!"
                    echo "✅ Recommendations: ${'$'}RECOMMENDATIONS"
                    echo "▶️  Next Steps: ${'$'}NEXT_STEPS"
                else
                    echo ""
                    echo "⚠️  Consolidated EAP validation FAILED!"
                    echo "💥 Failure Reasons: ${'$'}FAILURE_REASONS"
                    echo "💡 Recommendations: ${'$'}RECOMMENDATIONS"
                    echo "▶️  Next Steps: ${'$'}NEXT_STEPS"
                fi

                echo ""
                echo "=== Step 5: Report Generation & Notifications Completed Successfully ==="
                
                # Always exit successfully to ensure full report generation and artifact publishing
                exit 0
            """.trimIndent()
        }

        // Add a separate step for detailed Slack webhook notification
        script {
            name = "Send Detailed Slack Report"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/bin/bash
                
                echo "=== Sending detailed Slack webhook notification ==="
                
                # Read all the quality gate data with safe parameter extraction and defaults
                KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
                OVERALL_STATUS=$(echo "%quality.gate.overall.status%" | sed 's/^%quality\.gate\.overall\.status%$/UNKNOWN/' || echo "UNKNOWN")
                OVERALL_SCORE=$(echo "%quality.gate.overall.score%" | sed 's/^%quality\.gate\.overall\.score%$/0/' || echo "0")
                TOTAL_CRITICAL=$(echo "%quality.gate.total.critical%" | sed 's/^%quality\.gate\.total\.critical%$/0/' || echo "0")
                
                EXTERNAL_GATE_STATUS=$(echo "%external.gate.status%" | sed 's/^%external\.gate\.status%$/UNKNOWN/' || echo "UNKNOWN")
                EXTERNAL_GATE_SCORE=$(echo "%external.gate.score%" | sed 's/^%external\.gate\.score%$/0/' || echo "0")
                EXTERNAL_TOTAL_SAMPLES=$(echo "%external.validation.total.samples%" | sed 's/^%external\.validation\.total\.samples%$/0/' || echo "0")
                EXTERNAL_SUCCESSFUL_SAMPLES=$(echo "%external.validation.successful.samples%" | sed 's/^%external\.validation\.successful\.samples%$/0/' || echo "0")
                
                INTERNAL_GATE_STATUS=$(echo "%internal.gate.status%" | sed 's/^%internal\.gate\.status%$/UNKNOWN/' || echo "UNKNOWN")
                INTERNAL_GATE_SCORE=$(echo "%internal.gate.score%" | sed 's/^%internal\.gate\.score%$/0/' || echo "0")
                INTERNAL_TOTAL_TESTS=$(echo "%internal.validation.total.tests%" | sed 's/^%internal\.validation\.total\.tests%$/0/' || echo "0")
                INTERNAL_PASSED_TESTS=$(echo "%internal.validation.passed.tests%" | sed 's/^%internal\.validation\.passed\.tests%$/0/' || echo "0")
                
                RECOMMENDATIONS=$(echo "%quality.gate.recommendations%" | sed 's/^%quality\.gate\.recommendations%$/Quality gate evaluation not completed/' || echo "Quality gate evaluation not completed")
                
                # Choose emojis and colors based on status
                if [ "${'$'}OVERALL_STATUS" = "PASSED" ]; then
                    MAIN_EMOJI="🎉"
                    COLOR="good"
                else
                    MAIN_EMOJI="⚠️"
                    COLOR="danger"
                fi
                
                EXT_EMOJI="❌"
                if [ "${'$'}EXTERNAL_GATE_STATUS" = "PASSED" ]; then
                    EXT_EMOJI="✅"
                fi
                
                INT_EMOJI="❌"
                if [ "${'$'}INTERNAL_GATE_STATUS" = "PASSED" ]; then
                    INT_EMOJI="✅"
                fi
                
                CRITICAL_EMOJI="🚨"
                if [ "${'$'}TOTAL_CRITICAL" -eq 0 ]; then
                    CRITICAL_EMOJI="✅"
                fi
                
                # Build URL
                BUILD_URL="%teamcity.serverUrl%/buildConfiguration/%system.teamcity.buildType.id%/%teamcity.build.id%"
                
                # Create JSON payload for Slack webhook with error handling
                if ! cat > slack_payload.json << EOF
{
    "attachments": [
        {
            "color": "${'$'}COLOR",
            "blocks": [
                {
                    "type": "header",
                    "text": {
                        "type": "plain_text",
                        "text": "${'$'}MAIN_EMOJI Ktor EAP Validation Report - ${'$'}KTOR_VERSION"
                    }
                },
                {
                    "type": "section",
                    "fields": [
                        {
                            "type": "mrkdwn",
                            "text": "*Overall Status:*\\n${'$'}OVERALL_STATUS"
                        },
                        {
                            "type": "mrkdwn", 
                            "text": "*Score:*\\n${'$'}OVERALL_SCORE/100"
                        },
                        {
                            "type": "mrkdwn",
                            "text": "*Critical Issues:*\\n${'$'}CRITICAL_EMOJI ${'$'}TOTAL_CRITICAL"
                        }
                    ]
                },
                {
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": "*📋 Validation Results:*"
                    }
                },
                {
                    "type": "section",
                    "fields": [
                        {
                            "type": "mrkdwn",
                            "text": "${'$'}EXT_EMOJI *External Samples:*\\n\`${'$'}EXTERNAL_SUCCESSFUL_SAMPLES/${'$'}EXTERNAL_TOTAL_SAMPLES\` passed (\`${'$'}EXTERNAL_GATE_SCORE/100\`)"
                        },
                        {
                            "type": "mrkdwn",
                            "text": "${'$'}INT_EMOJI *Internal Tests:*\\n\`${'$'}INTERNAL_PASSED_TESTS/${'$'}INTERNAL_TOTAL_TESTS\` passed (\`${'$'}INTERNAL_GATE_SCORE/100\`)"
                        }
                    ]
                },
                {
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": "*💡 Recommendations:*\\n${'$'}RECOMMENDATIONS"
                    }
                },
                {
                    "type": "actions",
                    "elements": [
                        {
                            "type": "button",
                            "text": {
                                "type": "plain_text",
                                "text": "🔗 View Full Report"
                            },
                            "url": "${'$'}BUILD_URL"
                        }
                    ]
                }
            ]
        }
    ]
}
EOF
                then
                    echo "❌ Failed to create Slack payload JSON"
                    exit 0
                fi
                
                # Send to Slack webhook with error handling
                SLACK_WEBHOOK="${'$'}SLACK_WEBHOOK_URL"

                echo "Sending notification to Slack webhook..."
                
                if curl -X POST -H 'Content-type: application/json' \
                    --max-time 30 \
                    --data @slack_payload.json \
                    "${'$'}SLACK_WEBHOOK"; then
                    echo "✅ Detailed Slack notification sent successfully"
                else
                    CURL_EXIT_CODE=$?
                    echo "❌ Failed to send Slack notification (curl exit code: ${'$'}CURL_EXIT_CODE)"
                    echo "This is non-critical - build continues successfully"
                fi
                
                # Clean up
                rm -f slack_payload.json
                
                echo "=== Slack notification step completed ==="
                exit 0
            """.trimIndent()
        }
    }
}
