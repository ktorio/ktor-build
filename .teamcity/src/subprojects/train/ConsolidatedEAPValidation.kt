package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.build.defaultGradleParams
import subprojects.VCSCore
import dsl.addSlackNotifications

object EapConstants {
    const val EAP_VERSION_REGEX = ">[0-9][^<]*-eap-[0-9]*<"
    const val KTOR_EAP_METADATA_URL =
        "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml"
    const val KTOR_COMPILER_PLUGIN_METADATA_URL =
        "https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-compiler-plugin/maven-metadata.xml"
}

object ConsolidatedEAPValidation {
    fun createConsolidatedProject(): Project = Project {
        id("ConsolidatedEAPValidation")
        name = "Consolidated EAP Validation"
        description =
            "Consolidated EAP validation that runs all validation steps in sequence: Version Resolution → External Samples → Internal Tests → Quality Gate → Reports"

        val consolidatedBuild = createConsolidatedBuild()
        buildType(consolidatedBuild)
    }

    /**
     * Creates a consolidated EAP validation build that combines all validation steps into one build
     *
     * Step 1: Version Resolution
     * Step 2: External Samples Validation (Parallel)
     * Step 3: Internal Test Suites
     * Step 4: Quality Gate Evaluation
     * Step 5: Report Generation & Notifications
     */
    private fun createConsolidatedBuild(): BuildType = BuildType {
        id("ConsolidatedEAPValidation")
        name = "Consolidated EAP Validation Build"
        description = "Consolidated build that runs all EAP validation steps in sequence"

        vcs {
            root(VCSCore)
        }

        requirements {
            exists("teamcity.agent.jvm.os.name")
        }


        params {
            defaultGradleParams()

            // EAP Version parameters
            param("env.KTOR_VERSION", "")
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "")
            param("env.KOTLIN_VERSION", "")

            // External validation parameters
            param("ktor.eap.version", "KTOR_VERSION")
            param("enhanced.validation.enabled", "true")
            param("toml.comprehensive.handling", "true")
            param("configuration.preservation.enabled", "true")
            param("special.handling.enabled", "true")
            param("compose.multiplatform.support", "true")
            param("testcontainers.cloud.enabled", "true")
            password("testcontainers-cloud-token", "credentialsJSON:your-testcontainers-cloud-token-id")

            // Quality Gate Configuration
            param("quality.gate.enabled", "true")
            param("quality.gate.thresholds.minimum.score", "80")
            param("quality.gate.thresholds.critical.issues", "0")
            param("quality.gate.thresholds.warning.issues", "5")
            param("quality.gate.thresholds.success.rate", "95.0")
            param("quality.gate.execution.timeout.minutes", "120")

            // Initialize runtime parameters with default values
            param("quality.gate.overall.status", "PENDING")
            param("quality.gate.overall.score", "0")
            param("quality.gate.total.critical", "0")
            param("external.gate.status", "PENDING")
            param("external.gate.score", "0")
            param("internal.gate.status", "PENDING")
            param("internal.gate.score", "0")
            param("quality.gate.recommendations", "Quality gate evaluation not yet started")
            param("quality.gate.next.steps", "Waiting for validation steps to complete")
            param("quality.gate.failure.reasons", "Quality gate evaluation failed for unknown reasons")

            // Scoring Configuration
            param("quality.gate.scoring.base.score", "100")
            param("quality.gate.scoring.external.weight", "60")
            param("quality.gate.scoring.internal.weight", "40")
            param("quality.gate.scoring.failure.penalty", "50")
            param("quality.gate.scoring.critical.penalty", "20")
            param("quality.gate.scoring.warning.penalty", "5")

            // Notification Configuration
            param("quality.gate.notification.channel.main", "#ktor-projects-on-eap")
            param("quality.gate.notification.channel.alerts", "#ktor-projects-on-eap")
            param("quality.gate.notification.connection", "PROJECT_EXT_5")
            param("quality.gate.notification.enhanced", "true")

            // Sample Counts for Validation
            param("quality.gate.external.samples.expected", "7")
            param("quality.gate.internal.samples.expected", "15")
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
                buildType = "KtorPublish_AllEAP"
                successfulOnly = true
                branchFilter = "+:refs/heads/*"
            }
        }

        addSlackNotifications()

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "ERROR:"
                failureMessage = "Error detected in EAP validation"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "CRITICAL ERROR:"
                failureMessage = "Critical error in EAP validation"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "Quality gates failed"
                failureMessage = "EAP quality gates did not meet release criteria"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 120
        }

        artifactRules = """
            external-validation-reports => external-validation-reports.zip
            internal-validation-reports => internal-validation-reports.zip
            quality-gate-reports => quality-gate-reports.zip
        """.trimIndent()
    }

    /**
     * Step 1: Version Resolution
     * Fetches the latest EAP versions for Ktor framework, compiler plugin, and Kotlin
     */
    private fun BuildSteps.versionResolution() {
        script {
            name = "Step 1: Version Resolution"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== Step 1: Version Resolution ==="
                echo "Fetching latest EAP versions for Ktor framework, compiler plugin, and Kotlin"
                echo "Timestamp: $(date -Iseconds)"

                # Create reports directory
                mkdir -p version-resolution-reports

                # Fetch Latest EAP Ktor Framework Version
                echo "=== Fetching Latest Ktor EAP Framework Version ==="
                METADATA_URL="${EapConstants.KTOR_EAP_METADATA_URL}"
                TEMP_METADATA=$(mktemp)

                echo "Fetching framework metadata from: ${'$'}METADATA_URL"

                if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}METADATA_URL" -o "${'$'}TEMP_METADATA" 2>/dev/null; then
                    echo "Successfully fetched framework metadata"

                    LATEST_EAP_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_METADATA" | sed 's/<latest>//;s/<\/latest>//')

                    if [ -z "${'$'}LATEST_EAP_VERSION" ]; then
                        LATEST_EAP_VERSION=$(grep -o "${EapConstants.EAP_VERSION_REGEX}" "${'$'}TEMP_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
                    fi

                    if [ -n "${'$'}LATEST_EAP_VERSION" ]; then
                        echo "Found latest EAP framework version: ${'$'}LATEST_EAP_VERSION"
                        echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}LATEST_EAP_VERSION']"
                    else
                        echo "ERROR: No EAP version found in metadata"
                        exit 1
                    fi

                    rm -f "${'$'}TEMP_METADATA"
                else
                    echo "ERROR: Failed to fetch framework metadata from ${'$'}METADATA_URL"
                    exit 1
                fi

                # Fetch Latest EAP Ktor Compiler Plugin Version
                echo "=== Fetching Latest Ktor Compiler Plugin Version ==="
                COMPILER_METADATA_URL="${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}"
                TEMP_COMPILER_METADATA=$(mktemp)

                echo "Fetching compiler plugin metadata from: ${'$'}COMPILER_METADATA_URL"

                if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}COMPILER_METADATA_URL" -o "${'$'}TEMP_COMPILER_METADATA" 2>/dev/null; then
                    echo "Successfully fetched compiler plugin metadata"

                    LATEST_COMPILER_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_COMPILER_METADATA" | sed 's/<latest>//;s/<\/latest>//')

                    if [ -z "${'$'}LATEST_COMPILER_VERSION" ]; then
                        LATEST_COMPILER_VERSION=$(grep -o "${EapConstants.EAP_VERSION_REGEX}" "${'$'}TEMP_COMPILER_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
                    fi

                    if [ -n "${'$'}LATEST_COMPILER_VERSION" ]; then
                        echo "Found latest compiler plugin version: ${'$'}LATEST_COMPILER_VERSION"
                        echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}LATEST_COMPILER_VERSION']"
                    else
                        echo "ERROR: No compiler plugin version found in metadata"
                        exit 1
                    fi

                    rm -f "${'$'}TEMP_COMPILER_METADATA"
                else
                    echo "ERROR: Failed to fetch compiler plugin metadata from ${'$'}COMPILER_METADATA_URL"
                    exit 1
                fi

                # Fetch latest Kotlin version from GitHub API
                echo "=== Fetching Latest Kotlin Version ==="
                KOTLIN_API_URL="https://api.github.com/repos/JetBrains/kotlin/releases/latest"
                TEMP_KOTLIN_METADATA=$(mktemp)

                echo "Fetching Kotlin version from: ${'$'}KOTLIN_API_URL"

                if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}KOTLIN_API_URL" -o "${'$'}TEMP_KOTLIN_METADATA" 2>/dev/null; then
                    echo "Successfully fetched Kotlin metadata"

                    LATEST_KOTLIN_VERSION=$(grep -o '"tag_name": *"[^"]*"' "${'$'}TEMP_KOTLIN_METADATA" | sed 's/"tag_name": *"//;s/"//' | sed 's/^v//')

                    if [ -n "${'$'}LATEST_KOTLIN_VERSION" ]; then
                        echo "Found latest Kotlin version: ${'$'}LATEST_KOTLIN_VERSION"
                        echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='${'$'}LATEST_KOTLIN_VERSION']"
                    else
                        echo "WARNING: No Kotlin version found in GitHub API, using fallback"
                        LATEST_KOTLIN_VERSION="2.3.0"  # Fallback version
                        echo "Using fallback Kotlin version: ${'$'}LATEST_KOTLIN_VERSION"
                        echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='${'$'}LATEST_KOTLIN_VERSION']"
                    fi

                    rm -f "${'$'}TEMP_KOTLIN_METADATA"
                else
                    echo "WARNING: Failed to fetch Kotlin version from GitHub API, using fallback"
                    LATEST_KOTLIN_VERSION="2.3.0"  # Fallback version
                    echo "Using fallback Kotlin version: ${'$'}LATEST_KOTLIN_VERSION"
                    echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='${'$'}LATEST_KOTLIN_VERSION']"
                fi

                # Generate version resolution report
                cat > version-resolution-reports/version-summary.txt <<EOF
Version Resolution Report
========================
Generated: $(date -Iseconds)

Resolved Versions:
- Ktor Framework: ${'$'}LATEST_EAP_VERSION
- Ktor Compiler Plugin: ${'$'}LATEST_COMPILER_VERSION
- Kotlin: ${'$'}LATEST_KOTLIN_VERSION

Status: SUCCESS
EOF

                echo "=== Step 1: Version Resolution Completed Successfully ==="
                echo "Ktor Version: ${'$'}LATEST_EAP_VERSION"
                echo "Compiler Plugin Version: ${'$'}LATEST_COMPILER_VERSION"
                echo "Kotlin Version: ${'$'}LATEST_KOTLIN_VERSION"
            """.trimIndent()
        }
    }

    /**
     * Step 2: External Samples Validation (Parallel)
     * Validates external GitHub samples against the resolved EAP versions
     */
    private fun BuildSteps.externalSamplesValidation() {
        script {
            name = "Step 2: External Samples Validation (Parallel)"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== Step 2: External Samples Validation (Parallel) ==="
                echo "Validating external GitHub samples against EAP versions"
                echo "EAP Version: %env.KTOR_VERSION%"
                echo "Timestamp: $(date -Iseconds)"

                # Create reports directory
                mkdir -p external-validation-reports

                # List of external samples to validate
                declare -a SAMPLES=(
                    "ktor-ai-server"
                    "ktor-native-server"
                    "ktor-config-example"
                    "ktor-workshop-2025"
                    "amper-ktor-sample"
                    "ktor-di-overview"
                    "ktor-full-stack-real-world"
                )

                echo "Validating ${'$'}{#SAMPLES[@]} external samples in parallel..."

                # Function to validate a single sample
                validate_sample() {
                    local sample_name="$1"
                    local sample_dir="samples/${'$'}sample_name"
                    local report_file="$(pwd)/external-validation-reports/${'$'}sample_name-validation.txt"

                    echo "=== Validating ${'$'}sample_name ===" > "${'$'}report_file"
                    echo "Started: $(date -Iseconds)" >> "${'$'}report_file"
                    echo "EAP Version: %env.KTOR_VERSION%" >> "${'$'}report_file"
                    echo "" >> "${'$'}report_file"

                    # Clone the sample repository
                    echo "Cloning sample repository..." >> "${'$'}report_file"
                    if git clone "https://github.com/ktorio/${'$'}sample_name.git" "${'$'}sample_dir" 2>> "${'$'}report_file"; then
                        echo "Successfully cloned ${'$'}sample_name" >> "${'$'}report_file"
                    else
                        echo "Status: FAILED" >> "${'$'}report_file"
                        echo "Error: Failed to clone repository" >> "${'$'}report_file"
                        return 1
                    fi

                    cd "${'$'}sample_dir" || {
                        echo "Status: FAILED" >> "${'$'}report_file"
                        echo "Error: Failed to enter sample directory" >> "${'$'}report_file"
                        return 1
                    }

                    # Update Ktor version in build files
                    echo "Updating Ktor version to %env.KTOR_VERSION%..." >> "${'$'}report_file"
                    if [ -f "build.gradle.kts" ]; then
                        # Update Kotlin DSL build file
                        sed -i.bak "s/ktor_version = \"[^\"]*\"/ktor_version = \"%env.KTOR_VERSION%\"/g" build.gradle.kts
                        sed -i.bak "s/val ktor_version: String by project/val ktor_version = \"%env.KTOR_VERSION%\"/g" build.gradle.kts
                    elif [ -f "build.gradle" ]; then
                        # Update Groovy build file
                        sed -i.bak "s/ktor_version = '[^']*'/ktor_version = '%env.KTOR_VERSION%'/g" build.gradle
                        sed -i.bak "s/ktor_version = \"[^\"]*\"/ktor_version = \"%env.KTOR_VERSION%\"/g" build.gradle
                    fi

                    # Update gradle.properties if it exists
                    if [ -f "gradle.properties" ]; then
                        sed -i.bak "s/ktor_version=.*/ktor_version=%env.KTOR_VERSION%/g" gradle.properties
                    fi

                    # Build the sample
                    echo "Building sample..." >> "${'$'}report_file"
                    if ./gradlew build --no-daemon --stacktrace 2>> "${'$'}report_file"; then
                        echo "Build successful" >> "${'$'}report_file"

                        # Run tests if available
                        echo "Running tests..." >> "${'$'}report_file"
                        if ./gradlew test --no-daemon --stacktrace 2>> "${'$'}report_file"; then
                            echo "Status: SUCCESS" >> "${'$'}report_file"
                            echo "All tests passed" >> "${'$'}report_file"
                            cd - > /dev/null
                            return 0
                        else
                            echo "Status: FAILED" >> "${'$'}report_file"
                            echo "Error: Tests failed" >> "${'$'}report_file"
                            cd - > /dev/null
                            return 1
                        fi
                    else
                        echo "Status: FAILED" >> "${'$'}report_file"
                        echo "Error: Build failed" >> "${'$'}report_file"
                        cd - > /dev/null
                        return 1
                    fi
                }

                # Run validations in parallel
                declare -a PIDS=()
                declare -a RESULTS=()

                for sample in "${'$'}{SAMPLES[@]}"; do
                    validate_sample "${'$'}sample" &
                    PIDS+=($!)
                done

                # Wait for all validations to complete and collect results
                TOTAL_SAMPLES=${'$'}{#SAMPLES[@]}
                SUCCESSFUL_SAMPLES=0
                FAILED_SAMPLES=0

                # Wait for all background processes and collect results
                for pid in "${'$'}{PIDS[@]}"; do
                    wait ${'$'}pid
                    if [ $? -eq 0 ]; then
                        SUCCESSFUL_SAMPLES=$((SUCCESSFUL_SAMPLES + 1))
                        echo "✅ Sample validation succeeded"
                    else
                        FAILED_SAMPLES=$((FAILED_SAMPLES + 1))
                        echo "❌ Sample validation failed"
                    fi
                done

                # Calculate success rate
                SUCCESS_RATE=$(echo "scale=2; ${'$'}SUCCESSFUL_SAMPLES * 100 / ${'$'}TOTAL_SAMPLES" | bc -l)

                # Generate external validation summary
                cat > external-validation-reports/external-validation-summary.txt <<EOF
External Samples Validation Report
==================================
Generated: $(date -Iseconds)
EAP Version: %env.KTOR_VERSION%

Results:
- Total Samples: ${'$'}TOTAL_SAMPLES
- Successful: ${'$'}SUCCESSFUL_SAMPLES
- Failed: ${'$'}FAILED_SAMPLES
- Success Rate: ${'$'}SUCCESS_RATE%

Status: $([[ ${'$'}FAILED_SAMPLES -eq 0 ]] && echo "SUCCESS" || echo "FAILED")
EOF

                # Generate JSON report for programmatic access
                cat > external-validation-reports/external-validation-results.json <<EOF
{
    "eapVersion": "%env.KTOR_VERSION%",
    "timestamp": "$(date -Iseconds)",
    "totalSamples": ${'$'}TOTAL_SAMPLES,
    "successfulSamples": ${'$'}SUCCESSFUL_SAMPLES,
    "failedSamples": ${'$'}FAILED_SAMPLES,
    "successRate": ${'$'}SUCCESS_RATE,
    "overallStatus": "$([[ ${'$'}FAILED_SAMPLES -eq 0 ]] && echo "SUCCESS" || echo "FAILED")",
    "criticalIssues": ${'$'}FAILED_SAMPLES
}
EOF

                # Set TeamCity parameters for quality gate evaluation
                echo "##teamcity[setParameter name='external.validation.total.samples' value='${'$'}TOTAL_SAMPLES']"
                echo "##teamcity[setParameter name='external.validation.successful.samples' value='${'$'}SUCCESSFUL_SAMPLES']"
                echo "##teamcity[setParameter name='external.validation.failed.samples' value='${'$'}FAILED_SAMPLES']"
                echo "##teamcity[setParameter name='external.validation.success.rate' value='${'$'}SUCCESS_RATE']"
                echo "##teamcity[setParameter name='external.validation.status' value='$([[ ${'$'}FAILED_SAMPLES -eq 0 ]] && echo "SUCCESS" || echo "FAILED")']"

                echo "=== Step 2: External Samples Validation Completed ==="
                echo "Total Samples: ${'$'}TOTAL_SAMPLES"
                echo "Successful: ${'$'}SUCCESSFUL_SAMPLES"
                echo "Failed: ${'$'}FAILED_SAMPLES"
                echo "Success Rate: ${'$'}SUCCESS_RATE%"

                # External validation is required - track failures for quality gate evaluation
                # This ensures external validation is not optional and failures are properly evaluated
                if [ ${'$'}FAILED_SAMPLES -gt 0 ]; then
                    echo "❌ External validation failed: ${'$'}FAILED_SAMPLES out of ${'$'}TOTAL_SAMPLES samples failed"
                    echo "External validation is required and failures will be evaluated by quality gate"
                    echo "FAILURE|External validation status could not be determined - required dependency failed"
                else
                    echo "✅ External validation passed: All ${'$'}SUCCESSFUL_SAMPLES samples succeeded"
                fi

                # Continue to quality gate evaluation to make final determination
                exit 0
            """.trimIndent()
        }
    }

    /**
     * Step 3: Internal Test Suites
     * Runs internal test suites against the EAP versions
     */
    private fun BuildSteps.internalTestSuites() {
        script {
            name = "Step 3: Internal Test Suites"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== Step 3: Internal Test Suites ==="
                echo "Running internal test suites against EAP versions"
                echo "EAP Version: %env.KTOR_VERSION%"
                echo "Expected Internal Samples: %quality.gate.internal.samples.expected%"
                echo "Timestamp: $(date -Iseconds)"

                # Create reports directory for artifact collection
                mkdir -p internal-validation-reports

                # Run internal test suites
                echo "Running internal test suites..."

                # Initialize counters
                TOTAL_TESTS=0
                PASSED_TESTS=0
                FAILED_TESTS=0
                CRITICAL_ISSUES=0
                WARNING_ISSUES=0

                # Run different test suites and collect results
                echo "Running unit tests..." >> internal-validation-reports/test-execution.log
                if ./gradlew test --no-daemon --stacktrace 2>> internal-validation-reports/test-execution.log; then
                    echo "Unit tests: PASSED" >> internal-validation-reports/test-execution.log
                    UNIT_TESTS_PASSED=1
                else
                    echo "Unit tests: FAILED" >> internal-validation-reports/test-execution.log
                    UNIT_TESTS_PASSED=0
                    CRITICAL_ISSUES=$((CRITICAL_ISSUES + 1))
                fi

                echo "Running integration tests..." >> internal-validation-reports/test-execution.log
                if ./gradlew integrationTest --no-daemon --stacktrace 2>> internal-validation-reports/test-execution.log; then
                    echo "Integration tests: PASSED" >> internal-validation-reports/test-execution.log
                    INTEGRATION_TESTS_PASSED=1
                else
                    echo "Integration tests: FAILED" >> internal-validation-reports/test-execution.log
                    INTEGRATION_TESTS_PASSED=0
                    FAILED_TESTS=$((FAILED_TESTS + 1))
                fi

                echo "Running API compatibility tests..." >> internal-validation-reports/test-execution.log
                if ./gradlew apiCheck --no-daemon --stacktrace 2>> internal-validation-reports/test-execution.log; then
                    echo "API compatibility tests: PASSED" >> internal-validation-reports/test-execution.log
                    API_TESTS_PASSED=1
                else
                    echo "API compatibility tests: FAILED" >> internal-validation-reports/test-execution.log
                    API_TESTS_PASSED=0
                    WARNING_ISSUES=$((WARNING_ISSUES + 1))
                fi

                echo "Running performance tests..." >> internal-validation-reports/test-execution.log
                if ./gradlew performanceTest --no-daemon --stacktrace 2>> internal-validation-reports/test-execution.log; then
                    echo "Performance tests: PASSED" >> internal-validation-reports/test-execution.log
                    PERF_TESTS_PASSED=1
                else
                    echo "Performance tests: FAILED" >> internal-validation-reports/test-execution.log
                    PERF_TESTS_PASSED=0
                    WARNING_ISSUES=$((WARNING_ISSUES + 1))
                fi

                echo "Running documentation tests..." >> internal-validation-reports/test-execution.log
                if ./gradlew dokkaHtml --no-daemon --stacktrace 2>> internal-validation-reports/test-execution.log; then
                    echo "Documentation tests: PASSED" >> internal-validation-reports/test-execution.log
                    DOC_TESTS_PASSED=1
                else
                    echo "Documentation tests: FAILED" >> internal-validation-reports/test-execution.log
                    DOC_TESTS_PASSED=0
                    WARNING_ISSUES=$((WARNING_ISSUES + 1))
                fi

                # Calculate totals
                TOTAL_TESTS=5
                PASSED_TESTS=$((UNIT_TESTS_PASSED + INTEGRATION_TESTS_PASSED + API_TESTS_PASSED + PERF_TESTS_PASSED + DOC_TESTS_PASSED))
                FAILED_TESTS=$((TOTAL_TESTS - PASSED_TESTS))

                echo "Internal Test Results:"
                echo "- Total Tests: ${'$'}TOTAL_TESTS"
                echo "- Passed: ${'$'}PASSED_TESTS"
                echo "- Failed: ${'$'}FAILED_TESTS"
                echo "- Critical Issues: ${'$'}CRITICAL_ISSUES"
                echo "- Warning Issues: ${'$'}WARNING_ISSUES"

                # Calculate success rate
                SUCCESS_RATE=$(echo "scale=2; ${'$'}PASSED_TESTS * 100 / ${'$'}TOTAL_TESTS" | bc -l)
                echo "- Success Rate: ${'$'}SUCCESS_RATE%"

                # Set TeamCity parameters for quality gate evaluation
                echo "##teamcity[setParameter name='internal.validation.total.tests' value='${'$'}TOTAL_TESTS']"
                echo "##teamcity[setParameter name='internal.validation.passed.tests' value='${'$'}PASSED_TESTS']"
                echo "##teamcity[setParameter name='internal.validation.failed.tests' value='${'$'}FAILED_TESTS']"
                echo "##teamcity[setParameter name='internal.validation.critical.issues' value='${'$'}CRITICAL_ISSUES']"
                echo "##teamcity[setParameter name='internal.validation.warning.issues' value='${'$'}WARNING_ISSUES']"
                echo "##teamcity[setParameter name='internal.validation.success.rate' value='${'$'}SUCCESS_RATE']"

                # Generate internal validation reports
                cat > internal-validation-reports/internal-validation-summary.txt <<EOF
Internal Validation Report - %env.KTOR_VERSION%
===============================================
Generated: $(date -Iseconds)

Test Results:
- Total Tests: ${'$'}TOTAL_TESTS
- Passed Tests: ${'$'}PASSED_TESTS
- Failed Tests: ${'$'}FAILED_TESTS
- Success Rate: ${'$'}SUCCESS_RATE%

Issues:
- Critical Issues: ${'$'}CRITICAL_ISSUES
- Warning Issues: ${'$'}WARNING_ISSUES

Overall Status: $([[ ${'$'}FAILED_TESTS -eq 0 && ${'$'}CRITICAL_ISSUES -eq 0 ]] && echo "SUCCESS" || echo "FAILED")
EOF

                # Generate JSON report for programmatic access
                cat > internal-validation-reports/internal-validation-results.json <<EOF
{
    "eapVersion": "%env.KTOR_VERSION%",
    "timestamp": "$(date -Iseconds)",
    "testResults": {
        "totalTests": ${'$'}TOTAL_TESTS,
        "passedTests": ${'$'}PASSED_TESTS,
        "failedTests": ${'$'}FAILED_TESTS,
        "successRate": ${'$'}SUCCESS_RATE
    },
    "issues": {
        "criticalIssues": ${'$'}CRITICAL_ISSUES,
        "warningIssues": ${'$'}WARNING_ISSUES
    },
    "overallStatus": "$([[ ${'$'}FAILED_TESTS -eq 0 && ${'$'}CRITICAL_ISSUES -eq 0 ]] && echo "SUCCESS" || echo "FAILED")"
}
EOF

                echo "Internal validation reports generated in internal-validation-reports/"

                # Determine overall status
                if [ ${'$'}FAILED_TESTS -eq 0 ] && [ ${'$'}CRITICAL_ISSUES -eq 0 ]; then
                    echo "✅ Internal validation passed"
                    echo "##teamcity[setParameter name='internal.validation.status' value='SUCCESS']"
                else
                    echo "❌ Internal validation failed: ${'$'}FAILED_TESTS failed tests, ${'$'}CRITICAL_ISSUES critical issues"
                    echo "Internal validation is required and failures will be evaluated by quality gate"
                    echo "FAILURE|Internal validation status could not be determined - required dependency failed"
                    echo "##teamcity[setParameter name='internal.validation.status' value='FAILED']"
                fi

                echo "=== Step 3: Internal Test Suites Completed ==="
                # Continue to quality gate evaluation to make final determination
                exit 0
            """.trimIndent()
        }
    }

    /**
     * Step 4: Quality Gate Evaluation
     * Evaluates all validation results against quality gate criteria
     */
    private fun BuildSteps.qualityGateEvaluation() {
        script {
            name = "Step 4: Quality Gate Evaluation"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== Step 4: Quality Gate Evaluation ==="
                echo "Evaluating all validation results against quality gate criteria"
                echo "EAP Version: %env.KTOR_VERSION%"
                echo "Timestamp: $(date -Iseconds)"

                # Create reports directory
                mkdir -p quality-gate-reports

                # First, update status to indicate evaluation has started
                echo "##teamcity[setParameter name='quality.gate.overall.status' value='EVALUATING']"
                echo "##teamcity[setParameter name='quality.gate.recommendations' value='Quality gate evaluation in progress']"
                echo "##teamcity[setParameter name='quality.gate.next.steps' value='Awaiting evaluation results']"

                # Get validation results from previous steps
                EXTERNAL_STATUS="%external.validation.status%"
                EXTERNAL_TOTAL_SAMPLES="%external.validation.total.samples%"
                EXTERNAL_SUCCESSFUL_SAMPLES="%external.validation.successful.samples%"
                EXTERNAL_FAILED_SAMPLES="%external.validation.failed.samples%"
                EXTERNAL_SUCCESS_RATE="%external.validation.success.rate%"

                INTERNAL_STATUS="%internal.validation.status%"
                INTERNAL_TOTAL_TESTS="%internal.validation.total.tests%"
                INTERNAL_PASSED_TESTS="%internal.validation.passed.tests%"
                INTERNAL_FAILED_TESTS="%internal.validation.failed.tests%"
                INTERNAL_CRITICAL_ISSUES="%internal.validation.critical.issues%"
                INTERNAL_SUCCESS_RATE="%internal.validation.success.rate%"

                # Quality gate thresholds
                MIN_SCORE="%quality.gate.thresholds.minimum.score%"
                MAX_CRITICAL="%quality.gate.thresholds.critical.issues%"
                BASE_SCORE="%quality.gate.scoring.base.score%"
                EXTERNAL_WEIGHT="%quality.gate.scoring.external.weight%"
                INTERNAL_WEIGHT="%quality.gate.scoring.internal.weight%"

                echo "External validation status: ${'$'}EXTERNAL_STATUS"
                echo "Internal validation status: ${'$'}INTERNAL_STATUS"

                # Calculate scores
                if [ "${'$'}EXTERNAL_STATUS" = "SUCCESS" ]; then
                    EXTERNAL_SCORE=$((BASE_SCORE - 5))  # 95
                else
                    EXTERNAL_SCORE=$((BASE_SCORE - 50)) # 50
                fi

                if [ "${'$'}INTERNAL_STATUS" = "SUCCESS" ]; then
                    INTERNAL_SCORE=$((BASE_SCORE - 5))  # 95
                else
                    INTERNAL_SCORE=$((BASE_SCORE - 50)) # 50
                fi

                # Calculate weighted overall score
                OVERALL_SCORE=$(((EXTERNAL_SCORE * EXTERNAL_WEIGHT + INTERNAL_SCORE * INTERNAL_WEIGHT) / 100))

                # Calculate total critical issues
                EXTERNAL_CRITICAL_ISSUES=${'$'}EXTERNAL_FAILED_SAMPLES
                TOTAL_CRITICAL=$((EXTERNAL_CRITICAL_ISSUES + INTERNAL_CRITICAL_ISSUES))

                # Determine overall status
                if [ ${'$'}TOTAL_CRITICAL -gt ${'$'}MAX_CRITICAL ]; then
                    OVERALL_STATUS="FAILED"
                elif [ ${'$'}OVERALL_SCORE -lt ${'$'}MIN_SCORE ]; then
                    OVERALL_STATUS="FAILED"
                elif [ "${'$'}EXTERNAL_STATUS" = "SUCCESS" ] && [ "${'$'}INTERNAL_STATUS" = "SUCCESS" ]; then
                    OVERALL_STATUS="PASSED"
                else
                    OVERALL_STATUS="FAILED"
                fi

                # Generate recommendations and next steps
                if [ "${'$'}OVERALL_STATUS" = "PASSED" ]; then
                    RECOMMENDATIONS="EAP version %env.KTOR_VERSION% is ready for release"
                    NEXT_STEPS="Prepare release notes and documentation for EAP %env.KTOR_VERSION%"
                    FAILURE_REASONS=""
                elif [ ${'$'}TOTAL_CRITICAL -gt 0 ]; then
                    RECOMMENDATIONS="Address ${'$'}TOTAL_CRITICAL critical issues before release"
                    NEXT_STEPS="Fix critical issues in validation"
                    FAILURE_REASONS="Too many critical issues (${'$'}TOTAL_CRITICAL > ${'$'}MAX_CRITICAL)"
                elif [ ${'$'}OVERALL_SCORE -lt ${'$'}MIN_SCORE ]; then
                    RECOMMENDATIONS="Improve quality score from ${'$'}OVERALL_SCORE to at least ${'$'}MIN_SCORE"
                    NEXT_STEPS="Fix identified issues and re-run validation"
                    FAILURE_REASONS="Score too low (${'$'}OVERALL_SCORE < ${'$'}MIN_SCORE)"
                else
                    RECOMMENDATIONS="Review failed validations and address issues"
                    NEXT_STEPS="Fix identified issues and re-run validation"
                    FAILURE_REASONS="Validation failures detected"
                fi

                echo "=== Quality Gate Evaluation Results ==="
                echo "Overall Status: ${'$'}OVERALL_STATUS"
                echo "Overall Score: ${'$'}OVERALL_SCORE/100 (weighted: External ${'$'}EXTERNAL_WEIGHT%, Internal ${'$'}INTERNAL_WEIGHT%)"
                echo "External Validation: $([[ "${'$'}EXTERNAL_STATUS" == "SUCCESS" ]] && echo "PASSED" || echo "FAILED") (${'$'}EXTERNAL_SCORE/100)"
                echo "Internal Validation: $([[ "${'$'}INTERNAL_STATUS" == "SUCCESS" ]] && echo "PASSED" || echo "FAILED") (${'$'}INTERNAL_SCORE/100)"
                echo "Total Critical Issues: ${'$'}TOTAL_CRITICAL (External: ${'$'}EXTERNAL_CRITICAL_ISSUES, Internal: ${'$'}INTERNAL_CRITICAL_ISSUES)"

                # Set all TeamCity parameters with calculated values
                echo "##teamcity[setParameter name='quality.gate.overall.status' value='${'$'}OVERALL_STATUS']"
                echo "##teamcity[setParameter name='quality.gate.overall.score' value='${'$'}OVERALL_SCORE']"
                echo "##teamcity[setParameter name='quality.gate.total.critical' value='${'$'}TOTAL_CRITICAL']"
                echo "##teamcity[setParameter name='external.gate.status' value='$([[ "${'$'}EXTERNAL_STATUS" == "SUCCESS" ]] && echo "PASSED" || echo "FAILED")']"
                echo "##teamcity[setParameter name='external.gate.score' value='${'$'}EXTERNAL_SCORE']"
                echo "##teamcity[setParameter name='internal.gate.status' value='$([[ "${'$'}INTERNAL_STATUS" == "SUCCESS" ]] && echo "PASSED" || echo "FAILED")']"
                echo "##teamcity[setParameter name='internal.gate.score' value='${'$'}INTERNAL_SCORE']"
                echo "##teamcity[setParameter name='quality.gate.recommendations' value='${'$'}RECOMMENDATIONS']"
                echo "##teamcity[setParameter name='quality.gate.next.steps' value='${'$'}NEXT_STEPS']"
                echo "##teamcity[setParameter name='quality.gate.failure.reasons' value='${'$'}FAILURE_REASONS']"

                if [ "${'$'}OVERALL_STATUS" = "PASSED" ]; then
                    echo "✅ All quality gates passed"
                else
                    echo "❌ Quality gates failed: ${'$'}FAILURE_REASONS"
                fi

                echo "=== Step 4: Quality Gate Evaluation Completed ==="
            """.trimIndent()
        }
    }

    /**
     * Step 5: Report Generation & Notifications
     * Generates comprehensive reports and sends notifications
     */
    private fun BuildSteps.reportGenerationAndNotifications() {
        script {
            name = "Step 5: Report Generation & Notifications"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/bin/bash

                echo "=== Step 5: Report Generation & Notifications ==="
                echo "Generating comprehensive reports and sending notifications"
                echo "EAP Version: %env.KTOR_VERSION%"
                echo "Overall Status: %quality.gate.overall.status%"
                echo "Timestamp: $(date -Iseconds)"

                # Generate comprehensive report
                cat > quality-gate-reports/consolidated-eap-validation-report.txt <<EOF
Consolidated EAP Validation Report - %env.KTOR_VERSION%
======================================================
Generated: $(date -Iseconds)
Architecture: Consolidated Single Build

Overall Assessment:
- Status: %quality.gate.overall.status%
- Score: %quality.gate.overall.score%/100 (weighted)
- Critical Issues: %quality.gate.total.critical%
- Ready for Release: $([[ "%quality.gate.overall.status%" == "PASSED" ]] && echo "YES" || echo "NO")

Step Results:
Step 1 - Version Resolution: SUCCESS
  - Ktor Framework: %env.KTOR_VERSION%
  - Ktor Compiler Plugin: %env.KTOR_COMPILER_PLUGIN_VERSION%
  - Kotlin: %env.KOTLIN_VERSION%

Step 2 - External Samples Validation: %external.gate.status% (%external.gate.score%/100)
  - Total Samples: %external.validation.total.samples%
  - Successful: %external.validation.successful.samples%
  - Failed: %external.validation.failed.samples%
  - Success Rate: %external.validation.success.rate%%

Step 3 - Internal Test Suites: %internal.gate.status% (%internal.gate.score%/100)
  - Total Tests: %internal.validation.total.tests%
  - Passed: %internal.validation.passed.tests%
  - Failed: %internal.validation.failed.tests%
  - Success Rate: %internal.validation.success.rate%%

Step 4 - Quality Gate Evaluation: COMPLETED
  - Scoring Strategy: Weighted (External %quality.gate.scoring.external.weight%%, Internal %quality.gate.scoring.internal.weight%%)
  - Minimum Score Threshold: %quality.gate.thresholds.minimum.score%
  - Critical Issues Threshold: %quality.gate.thresholds.critical.issues%

Step 5 - Report Generation & Notifications: IN PROGRESS

Recommendations: %quality.gate.recommendations%
Next Steps: %quality.gate.next.steps%
$([[ "%quality.gate.overall.status%" == "FAILED" ]] && echo "Failure Reasons: %quality.gate.failure.reasons%" || echo "")
EOF

                # Generate JSON report
                cat > quality-gate-reports/consolidated-validation-results.json <<EOF
{
    "eapVersion": "%env.KTOR_VERSION%",
    "timestamp": "$(date -Iseconds)",
    "architecture": "consolidated",
    "overallStatus": "%quality.gate.overall.status%",
    "overallScore": %quality.gate.overall.score%,
    "totalCriticalIssues": %quality.gate.total.critical%,
    "steps": {
        "versionResolution": {
            "status": "SUCCESS",
            "ktorVersion": "%env.KTOR_VERSION%",
            "compilerPluginVersion": "%env.KTOR_COMPILER_PLUGIN_VERSION%",
            "kotlinVersion": "%env.KOTLIN_VERSION%"
        },
        "externalSamplesValidation": {
            "status": "%external.gate.status%",
            "score": %external.gate.score%,
            "totalSamples": %external.validation.total.samples%,
            "successfulSamples": %external.validation.successful.samples%,
            "failedSamples": %external.validation.failed.samples%,
            "successRate": %external.validation.success.rate%
        },
        "internalTestSuites": {
            "status": "%internal.gate.status%",
            "score": %internal.gate.score%,
            "totalTests": %internal.validation.total.tests%,
            "passedTests": %internal.validation.passed.tests%,
            "failedTests": %internal.validation.failed.tests%,
            "successRate": %internal.validation.success.rate%
        },
        "qualityGateEvaluation": {
            "status": "COMPLETED",
            "scoringStrategy": "weighted",
            "thresholds": {
                "minimumScore": %quality.gate.thresholds.minimum.score%,
                "criticalIssues": %quality.gate.thresholds.critical.issues%
            }
        },
        "reportGeneration": {
            "status": "IN_PROGRESS"
        }
    },
    "recommendations": "%quality.gate.recommendations%",
    "nextSteps": "%quality.gate.next.steps%",
    "readyForRelease": $([[ "%quality.gate.overall.status%" == "PASSED" ]] && echo "true" || echo "false")
}
EOF

                echo "Comprehensive reports generated"
                echo "##teamcity[publishArtifacts 'version-resolution-reports => version-resolution-reports.zip']"
                echo "##teamcity[publishArtifacts 'external-validation-reports => external-validation-reports.zip']"
                echo "##teamcity[publishArtifacts 'internal-validation-reports => internal-validation-reports.zip']"
                echo "##teamcity[publishArtifacts 'quality-gate-reports => quality-gate-reports.zip']"

                STATUS="%quality.gate.overall.status%"
                SCORE="%quality.gate.overall.score%"
                VERSION="%env.KTOR_VERSION%"

                echo "=== Final Consolidated EAP Validation Results ==="
                echo "EAP Version: ${'$'}VERSION"
                echo "Overall Status: ${'$'}STATUS"
                echo "Overall Score: ${'$'}SCORE/100"

                if [ "${'$'}STATUS" = "PASSED" ]; then
                    echo "✅ Consolidated EAP validation passed!"
                    echo "Recommendations: %quality.gate.recommendations%"
                    echo "Next Steps: %quality.gate.next.steps%"
                    echo "=== Step 5: Report Generation & Notifications Completed Successfully ==="
                    exit 0
                else
                    echo "❌ Consolidated EAP validation failed!"
                    echo "Failure Reasons: %quality.gate.failure.reasons%"
                    echo "Recommendations: %quality.gate.recommendations%"
                    echo "Next Steps: %quality.gate.next.steps%"
                    echo "=== Step 5: Report Generation & Notifications Completed with Failures ==="
                    exit 1
                fi
            """.trimIndent()
        }
    }
}
