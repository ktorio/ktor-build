package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.build.defaultGradleParams
import subprojects.VCSCore
import subprojects.VCSSamples
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

            vcs {
                root(VCSCore)
                root(VCSSamples, "+:. => samples")
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
                # Don't use 'set -e' - we want to collect as much data as possible

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
                        echo "✅ Latest Kotlin version: ${'$'}KOTLIN_VERSION (from EAP repository)"
                        VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (EAP_SUCCESS)\n"
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
                # Don't use 'set -e' - we want to process all samples and collect comprehensive results

                echo "=== Step 2: External Samples Validation ==="
                
                # Get current parameter values or use fallback defaults
                KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
                KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.1.21/' || echo "2.1.21")
                
                echo "Validating external GitHub samples against EAP versions"
                echo "Ktor Version: ${'$'}KTOR_VERSION"
                echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

                mkdir -p external-validation-reports

                # Check if samples directory exists
                if [ ! -d "samples" ]; then
                    echo "❌ Samples directory not found - cannot proceed with external validation"
                    echo "##teamcity[setParameter name='external.validation.total.samples' value='0']"
                    echo "##teamcity[setParameter name='external.validation.successful.samples' value='0']"
                    echo "##teamcity[setParameter name='external.validation.failed.samples' value='0']"
                    echo "##teamcity[setParameter name='external.validation.skipped.samples' value='0']"
                    echo "##teamcity[setParameter name='external.validation.success.rate' value='0.0']"
                    
                    cat > external-validation-reports/external-validation.txt <<EOF
External Samples Validation Report
==================================
Generated: $(date -Iseconds)
Ktor Version: ${'$'}KTOR_VERSION
Kotlin Version: ${'$'}KOTLIN_VERSION

ERROR: Samples directory not found
Cannot proceed with external validation

Status: DIRECTORY_NOT_FOUND
EOF
                    exit 0
                fi

                cd samples

                # Find all Gradle projects (both build.gradle and build.gradle.kts)
                SAMPLE_PROJECTS=$(find . -name "build.gradle*" -not -path "*/build/*" -not -path "*/.gradle/*" | xargs dirname | sort | uniq)
                TOTAL_SAMPLES=0
                SUCCESSFUL_SAMPLES=0
                FAILED_SAMPLES=0
                SKIPPED_SAMPLES=0
                
                echo "Found sample projects:"
                echo "${'$'}SAMPLE_PROJECTS" | head -20
                TOTAL_FOUND=$(echo "${'$'}SAMPLE_PROJECTS" | wc -l)
                if [ "${'$'}TOTAL_FOUND" -gt 20 ]; then
                    echo "... and $((TOTAL_FOUND - 20)) more projects"
                fi
                
                # Create a temporary gradle.properties with EAP versions
                cat > gradle.properties.eap <<EOF
kotlin_version=${'$'}KOTLIN_VERSION
ktor_version=${'$'}KTOR_VERSION
logback_version=1.4.14
kotlin.mpp.stability.nowarn=true
EOF

                # Initialize result tracking files
                > external-validation-reports/successful-samples.txt
                > external-validation-reports/failed-samples.txt
                > external-validation-reports/skipped-samples.txt

                # Process each sample, but don't exit on failures
                for project_dir in ${'$'}SAMPLE_PROJECTS; do
                    if [ -z "${'$'}project_dir" ] || [ "${'$'}project_dir" = "." ]; then
                        continue
                    fi
                    
                    TOTAL_SAMPLES=$((TOTAL_SAMPLES + 1))
                    echo ""
                    echo "=== [${'$'}TOTAL_SAMPLES] Validating sample: ${'$'}project_dir ==="
                    
                    # Check if project directory exists and is accessible
                    if [ ! -d "${'$'}project_dir" ]; then
                        echo "⚠️  Sample ${'$'}project_dir: DIRECTORY_NOT_FOUND - skipping"
                        SKIPPED_SAMPLES=$((SKIPPED_SAMPLES + 1))
                        echo "SKIPPED: ${'$'}project_dir (directory not found)" >> external-validation-reports/skipped-samples.txt
                        continue
                    fi
                    
                    cd "${'$'}project_dir"
                    
                    # Check if gradlew exists
                    if [ ! -f "./gradlew" ]; then
                        echo "⚠️  Sample ${'$'}project_dir: NO_GRADLE_WRAPPER - skipping"
                        SKIPPED_SAMPLES=$((SKIPPED_SAMPLES + 1))
                        echo "SKIPPED: ${'$'}project_dir (no gradle wrapper)" >> ../external-validation-reports/skipped-samples.txt
                        cd ..
                        continue
                    fi
                    
                    # Backup original gradle.properties if it exists
                    if [ -f "gradle.properties" ]; then
                        cp gradle.properties gradle.properties.backup
                    fi
                    
                    # Apply EAP versions
                    cp ../gradle.properties.eap gradle.properties
                    
                    # Try to build the project - capture exit code but don't exit
                    BUILD_LOG="../external-validation-reports/build-$(basename "${'$'}project_dir").log"
                    echo "Building with timeout of 300 seconds..."
                    
                    if timeout 300 ./gradlew build --no-daemon --continue --stacktrace --no-build-cache > "${'$'}BUILD_LOG" 2>&1; then
                        echo "✅ Sample ${'$'}project_dir: BUILD SUCCESSFUL"
                        SUCCESSFUL_SAMPLES=$((SUCCESSFUL_SAMPLES + 1))
                        echo "SUCCESS: ${'$'}project_dir" >> ../external-validation-reports/successful-samples.txt
                    else
                        BUILD_EXIT_CODE=$?
                        echo "❌ Sample ${'$'}project_dir: BUILD FAILED (exit code: ${'$'}BUILD_EXIT_CODE)"
                        FAILED_SAMPLES=$((FAILED_SAMPLES + 1))
                        echo "FAILED: ${'$'}project_dir (exit code: ${'$'}BUILD_EXIT_CODE)" >> ../external-validation-reports/failed-samples.txt
                        
                        # Extract error summary from build log
                        if [ -f "${'$'}BUILD_LOG" ]; then
                            echo "Build error summary:" >> ../external-validation-reports/failed-samples.txt
                            tail -20 "${'$'}BUILD_LOG" | grep -E "(FAILURE|ERROR|Exception)" | head -5 >> ../external-validation-reports/failed-samples.txt || true
                            echo "---" >> ../external-validation-reports/failed-samples.txt
                        fi
                    fi
                    
                    # Restore original gradle.properties if it existed
                    if [ -f "gradle.properties.backup" ]; then
                        mv gradle.properties.backup gradle.properties
                    else
                        rm -f gradle.properties
                    fi
                    
                    cd ..
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
                cat > external-validation-reports/external-validation.txt <<EOF
External Samples Validation Report
==================================
Generated: $(date -Iseconds)
Ktor Version: ${'$'}KTOR_VERSION
Kotlin Version: ${'$'}KOTLIN_VERSION

Results:
- Total Samples Processed: ${'$'}TOTAL_SAMPLES
- Successful: ${'$'}SUCCESSFUL_SAMPLES
- Failed: ${'$'}FAILED_SAMPLES  
- Skipped: ${'$'}SKIPPED_SAMPLES
- Success Rate: ${'$'}SUCCESS_RATE%

Successful Samples (${'$'}SUCCESSFUL_SAMPLES):
$(cat external-validation-reports/successful-samples.txt 2>/dev/null | head -50 || echo "None")
$([[ $(wc -l < external-validation-reports/successful-samples.txt 2>/dev/null || echo 0) -gt 50 ]] && echo "... and more (see artifacts)" || echo "")

Failed Samples (${'$'}FAILED_SAMPLES):
$(cat external-validation-reports/failed-samples.txt 2>/dev/null | head -100 || echo "None")
$([[ $(wc -l < external-validation-reports/failed-samples.txt 2>/dev/null || echo 0) -gt 100 ]] && echo "... and more (see artifacts)" || echo "")

Skipped Samples (${'$'}SKIPPED_SAMPLES):
$(cat external-validation-reports/skipped-samples.txt 2>/dev/null || echo "None")

Status: COMPLETED
EOF

                echo "=== Step 2: External Samples Validation Completed ==="
                
                # Always succeed - let quality gate evaluate the results
                # Even if all samples failed, that's valuable information for the quality gate
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
        gradle {
            name = "Step 3: Internal Test Suites - Build"
            tasks = "clean test"
            gradleParams = """
                -PktorVersion=%env.KTOR_VERSION% 
                -PkotlinVersion=%env.KOTLIN_VERSION%
                --continue
                --stacktrace
                --no-build-cache
            """.trimIndent()

            param("org.gradle.jvmargs", "-Xmx4g -XX:MaxMetaspaceSize=1g")

            executionMode = BuildStep.ExecutionMode.RUN_ON_FAILURE
        }

        script {
            name = "Step 3: Internal Test Suites - Process Results"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/bin/bash
                # Don't use 'set -e' - we want to process results regardless of test outcomes

                echo "=== Step 3: Processing Internal Test Results ==="
                
                # Get current parameter values or use fallback defaults
                KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
                KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.1.21/' || echo "2.1.21")
                BUILD_STATUS=$(echo "%teamcity.build.status%" | sed 's/^%teamcity\.build\.status%$/UNKNOWN/' || echo "UNKNOWN")
                
                echo "Analyzing internal test suite results"
                echo "Ktor Version: ${'$'}KTOR_VERSION"
                echo "Kotlin Version: ${'$'}KOTLIN_VERSION"
                echo "Build Status: ${'$'}BUILD_STATUS"

                mkdir -p internal-validation-reports

                # Find all test result XML files
                TEST_RESULT_FILES=$(find . -path "*/test-results/*" -name "*.xml" -type f 2>/dev/null || true)
                
                TOTAL_TESTS=0
                PASSED_TESTS=0
                FAILED_TESTS=0
                SKIPPED_TESTS=0
                ERROR_TESTS=0
                PROCESSED_FILES=0

                echo "Searching for test result files..."
                if [ -n "${'$'}TEST_RESULT_FILES" ]; then
                    echo "Found test result files:"
                    echo "${'$'}TEST_RESULT_FILES" | head -10
                    TOTAL_FILES=$(echo "${'$'}TEST_RESULT_FILES" | wc -l)
                    if [ "${'$'}TOTAL_FILES" -gt 10 ]; then
                        echo "... and $((TOTAL_FILES - 10)) more files"
                    fi
                    
                    # Process each test result file
                    for result_file in ${'$'}TEST_RESULT_FILES; do
                        if [ -f "${'$'}result_file" ] && [ -s "${'$'}result_file" ]; then
                            echo "Processing: ${'$'}result_file"
                            
                            # Extract test counts from XML (handle multiple formats)
                            TESTS=$(grep -o 'tests="[0-9]*"' "${'$'}result_file" | sed 's/tests="//g' | sed 's/"//g' | head -1 || echo "0")
                            FAILURES=$(grep -o 'failures="[0-9]*"' "${'$'}result_file" | sed 's/failures="//g' | sed 's/"//g' | head -1 || echo "0")
                            ERRORS=$(grep -o 'errors="[0-9]*"' "${'$'}result_file" | sed 's/errors="//g' | sed 's/"//g' | head -1 || echo "0")
                            SKIPPED=$(grep -o 'skipped="[0-9]*"' "${'$'}result_file" | sed 's/skipped="//g' | sed 's/"//g' | head -1 || echo "0")
                            
                            # Ensure values are numeric
                            TESTS=$(echo "${'$'}TESTS" | grep -E '^[0-9]+$' || echo "0")
                            FAILURES=$(echo "${'$'}FAILURES" | grep -E '^[0-9]+$' || echo "0")  
                            ERRORS=$(echo "${'$'}ERRORS" | grep -E '^[0-9]+$' || echo "0")
                            SKIPPED=$(echo "${'$'}SKIPPED" | grep -E '^[0-9]+$' || echo "0")
                            
                            TOTAL_TESTS=$((TOTAL_TESTS + TESTS))
                            FAILED_TESTS=$((FAILED_TESTS + FAILURES))
                            ERROR_TESTS=$((ERROR_TESTS + ERRORS))
                            SKIPPED_TESTS=$((SKIPPED_TESTS + SKIPPED))
                            PROCESSED_FILES=$((PROCESSED_FILES + 1))
                            
                            echo "  Tests: ${'$'}TESTS, Failures: ${'$'}FAILURES, Errors: ${'$'}ERRORS, Skipped: ${'$'}SKIPPED"
                        fi
                    done
                    
                    # Calculate passed tests
                    PASSED_TESTS=$((TOTAL_TESTS - FAILED_TESTS - ERROR_TESTS - SKIPPED_TESTS))
                    
                    # Ensure no negative values
                    if [ "${'$'}PASSED_TESTS" -lt 0 ]; then
                        PASSED_TESTS=0
                    fi
                else
                    echo "No JUnit XML test results found, checking build status and logs..."
                    
                    echo "Build status: ${'$'}BUILD_STATUS"
                    
                    # Look for compilation or build errors in recent logs
                    if find . -name "*.log" -newer gradle.properties 2>/dev/null | head -5 | xargs grep -l -i "error\|exception\|failed" >/dev/null 2>&1; then
                        echo "Found error indicators in build logs - assuming build failure"
                        TOTAL_TESTS=1
                        PASSED_TESTS=0
                        FAILED_TESTS=1
                        ERROR_TESTS=0
                        SKIPPED_TESTS=0
                    elif [ "${'$'}BUILD_STATUS" = "SUCCESS" ]; then
                        echo "Build successful but no test results - assuming minimal validation passed"
                        TOTAL_TESTS=1
                        PASSED_TESTS=1
                        FAILED_TESTS=0
                        ERROR_TESTS=0
                        SKIPPED_TESTS=0
                    else
                        echo "Build status unclear - reporting as build validation failure"
                        TOTAL_TESTS=1
                        PASSED_TESTS=0
                        FAILED_TESTS=0
                        ERROR_TESTS=1
                        SKIPPED_TESTS=0
                    fi
                fi

                # Calculate success rate
                if [ "${'$'}TOTAL_TESTS" -gt 0 ]; then
                    SUCCESS_RATE=$(echo "scale=1; ${'$'}PASSED_TESTS * 100 / ${'$'}TOTAL_TESTS" | bc -l 2>/dev/null || echo "0.0")
                else
                    SUCCESS_RATE="0.0"
                fi

                echo ""
                echo "=== Internal Test Suite Results ==="
                echo "Test result files processed: ${'$'}PROCESSED_FILES"
                echo "Total tests: ${'$'}TOTAL_TESTS"
                echo "Passed: ${'$'}PASSED_TESTS"
                echo "Failed: ${'$'}FAILED_TESTS"
                echo "Errors: ${'$'}ERROR_TESTS"  
                echo "Skipped: ${'$'}SKIPPED_TESTS"
                echo "Success rate: ${'$'}SUCCESS_RATE%"

                # Set parameters for quality gate evaluation
                echo "##teamcity[setParameter name='internal.validation.total.tests' value='${'$'}TOTAL_TESTS']"
                echo "##teamcity[setParameter name='internal.validation.passed.tests' value='${'$'}PASSED_TESTS']"
                echo "##teamcity[setParameter name='internal.validation.failed.tests' value='${'$'}FAILED_TESTS']"
                echo "##teamcity[setParameter name='internal.validation.error.tests' value='${'$'}ERROR_TESTS']"
                echo "##teamcity[setParameter name='internal.validation.skipped.tests' value='${'$'}SKIPPED_TESTS']"
                echo "##teamcity[setParameter name='internal.validation.success.rate' value='${'$'}SUCCESS_RATE']"
                echo "##teamcity[setParameter name='internal.validation.processed.files' value='${'$'}PROCESSED_FILES']"

                # Generate internal validation report
                cat > internal-validation-reports/internal-validation.txt <<EOF
Internal Test Suites Report
===========================
Generated: $(date -Iseconds)
Ktor Version: ${'$'}KTOR_VERSION
Kotlin Version: ${'$'}KOTLIN_VERSION

Results:
- Total Tests: ${'$'}TOTAL_TESTS
- Passed: ${'$'}PASSED_TESTS
- Failed: ${'$'}FAILED_TESTS
- Errors: ${'$'}ERROR_TESTS
- Skipped: ${'$'}SKIPPED_TESTS
- Success Rate: ${'$'}SUCCESS_RATE%

Processing Details:
- Test Result Files Found: ${'$'}PROCESSED_FILES
- Analysis Method: $([[ ${'$'}PROCESSED_FILES -gt 0 ]] && echo "JUnit XML parsing" || echo "Build status inference")

$([[ ${'$'}PROCESSED_FILES -gt 0 ]] && echo "
Test Result Files Processed:
$(echo "${'$'}TEST_RESULT_FILES" | head -20)
$([[ $(echo "${'$'}TEST_RESULT_FILES" | wc -l) -gt 20 ]] && echo "... and more (see build logs)" || echo "")" || echo "
Note: No JUnit XML files found. Results inferred from build status and log analysis.
This may indicate:
- Tests were not run due to compilation errors
- Test framework configuration issues  
- Build process interrupted before test execution")

Status: COMPLETED
EOF

                echo "=== Step 3: Internal Test Suites Completed ==="
                
                # Always succeed - let quality gate evaluate the results
                exit 0
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
                # Don't use 'set -e' - we want to complete evaluation even if some data is missing

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
                    NEXT_STEPS="Proceed with release process and stakeholder notification"
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
                echo "- Critical Check: ${'$'}CRITICAL_CHECK (${'$'}TOTAL_CRITICAL <= ${'$'}CRITICAL_THRESHOLD)"
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
                # Don't use 'set -e' - we want to generate as complete a report as possible

                echo "=== Step 5: Report Generation & Notifications ==="
                echo "Generating comprehensive reports and sending notifications"
                echo "Timestamp: $(date -Iseconds)"

                # Read all runtime parameter values with safe defaults and parameter extraction
                KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
                KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.1.21/' || echo "2.1.21")
                KTOR_COMPILER_PLUGIN_VERSION=$(echo "%env.KTOR_COMPILER_PLUGIN_VERSION%" | sed 's/^%env\.KTOR_COMPILER_PLUGIN_VERSION%$//' || echo "")
                BUILD_VCS_NUMBER=$(echo "%build.vcs.number%" | sed 's/^%build\.vcs\.number%$/unknown/' || echo "unknown")
                AGENT_NAME=$(echo "%agent.name%" | sed 's/^%agent\.name%$/unknown/' || echo "unknown")
                
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
                
                # Combine into multi-line status
                STATUS_TEXT="${'$'}STATUS_LINE1
${'$'}STATUS_LINE2
${'$'}STATUS_LINE3"
                
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
            conditions {
                doesNotEqual("system.slack.webhook.url", "")
            }
            scriptContent = """
                #!/bin/bash
                # Don't use 'set -e' - notification failures shouldn't break the build
                
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
                SLACK_WEBHOOK="%system.slack.webhook.url%"
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
