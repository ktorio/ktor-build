package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import subprojects.build.defaultGradleParams
import subprojects.VCSCore
import dsl.addSlackNotifications

object QualityGateOrchestrator {

    /**
     * Creates a quality gate orchestrator build type using the refactored modular architecture
     */
    fun createQualityGateOrchestrator(
        externalValidationBuild: BuildType,
        internalValidationBuild: BuildType,
        versionResolver: BuildType
    ): BuildType = BuildType {
        id("KtorEAPQualityGateOrchestrator")
        name = "EAP Quality Gate Orchestrator"
        description = "Orchestrates and evaluates all quality gates for EAP releases using modular architecture"

        vcs {
            root(VCSCore)
        }

        params {
            defaultGradleParams()
            param("env.KTOR_VERSION", "%dep." + versionResolver.id!!.value + ".env.KTOR_VERSION%")
            param("quality.gate.external.build.id", externalValidationBuild.id!!.value)
            param("quality.gate.internal.build.id", internalValidationBuild.id!!.value)

            // Quality Gate Configuration
            param("quality.gate.thresholds.minimum.score", "80")
            param("quality.gate.thresholds.critical.issues", "0")
            param("quality.gate.thresholds.warning.issues", "5")
            param("quality.gate.thresholds.success.rate", "95.0")
            param("quality.gate.execution.timeout.minutes", "60")

            // Initialize runtime parameters with default values
            param("quality.gate.overall.status", "PENDING")
            param("quality.gate.overall.score", "0")
            param("quality.gate.total.critical", "0")
            param("external.gate.status", "PENDING")
            param("external.gate.score", "0")
            param("internal.gate.status", "PENDING")
            param("internal.gate.score", "0")
            param("quality.gate.recommendations", "Quality gate evaluation not yet started")
            param("quality.gate.next.steps", "Waiting for dependency builds to complete")
            param("quality.gate.failure.reasons", "")

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
            initializeQualityGateContext()
            executeQualityGateEvaluation()
            generateReportsAndNotifications()
            applyQualityGateDecision()
        }

        dependencies {
            snapshot(versionResolver) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                synchronizeRevisions = false
            }

            snapshot(externalValidationBuild) {
                onDependencyFailure = FailureAction.CANCEL
                synchronizeRevisions = false
            }

            snapshot(internalValidationBuild) {
                onDependencyFailure = FailureAction.CANCEL
                synchronizeRevisions = false
            }

            artifacts(internalValidationBuild) {
                buildRule = lastSuccessful()
                artifactRules = "internal-validation-reports.zip => internal-validation-reports"
            }
        }

        addSlackNotifications()

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "Quality gates failed"
                failureMessage = "EAP quality gates did not meet release criteria"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "CRITICAL ERROR:"
                failureMessage = "Critical error in quality gate system"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 60
        }

        artifactRules = "quality-gate-reports => quality-gate-reports.zip"
    }

    private fun BuildSteps.initializeQualityGateContext() {
        script {
            name = "Initialize Quality Gate Context"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== EAP Quality Gate Orchestrator (Modular Architecture) ==="
                echo "EAP Version: %env.KTOR_VERSION%"
                echo "External Build: %quality.gate.external.build.id%"
                echo "Internal Build: %quality.gate.internal.build.id%"
                echo "Timestamp: $(date -Iseconds)"
                echo "Architecture: Modular Kotlin-based"

                mkdir -p quality-gate-reports
                mkdir -p quality-gate-context

                # Create context file for the evaluation engine
                cat > quality-gate-context/context.properties << 'EOF'
eap.version=%env.KTOR_VERSION%
external.build.id=%quality.gate.external.build.id%
internal.build.id=%quality.gate.internal.build.id%
trigger.build=%teamcity.build.id%
branch=%teamcity.build.branch%
environment=production
EOF

                echo "Quality gate context initialized with modular architecture"
            """.trimIndent()
        }
    }

    private fun BuildSteps.executeQualityGateEvaluation() {
        script {
            name = "Execute Quality Gate Evaluation"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== Executing Quality Gate Evaluation ==="

                # First, update status to indicate evaluation has started
                echo "##teamcity[setParameter name='quality.gate.overall.status' value='EVALUATING']"
                echo "##teamcity[setParameter name='quality.gate.recommendations' value='Quality gate evaluation in progress']"
                echo "##teamcity[setParameter name='quality.gate.next.steps' value='Awaiting evaluation results']"

                # Set environment variables from TeamCity parameters
                export EXTERNAL_STATUS="%dep.ExternalSamplesEAPCompositeBuild.teamcity.build.status%"
                export EXTERNAL_STATUS_TEXT="%dep.ExternalSamplesEAPCompositeBuild.teamcity.build.statusText%"
                export INTERNAL_STATUS="%dep.KtorEAPInternalValidation.teamcity.build.status%"
                export INTERNAL_STATUS_TEXT="%dep.KtorEAPInternalValidation.teamcity.build.statusText%"

                # Read internal validation results from artifacts
                echo "Reading internal validation results from artifacts..."
                if [ -f "internal-validation-reports/internal-validation-results.json" ]; then
                    echo "Found internal validation results file"
                    # Extract values from JSON using basic parsing (avoiding jq dependency)
                    INTERNAL_VALIDATION_STATUS=$(grep -o '"overallStatus"[[:space:]]*:[[:space:]]*"[^"]*"' internal-validation-reports/internal-validation-results.json | cut -d'"' -f4)
                    INTERNAL_TOTAL_TESTS=$(grep -o '"totalTests"[[:space:]]*:[[:space:]]*[0-9]*' internal-validation-reports/internal-validation-results.json | grep -o '[0-9]*')
                    INTERNAL_PASSED_TESTS=$(grep -o '"passedTests"[[:space:]]*:[[:space:]]*[0-9]*' internal-validation-reports/internal-validation-results.json | grep -o '[0-9]*')
                    INTERNAL_FAILED_TESTS=$(grep -o '"failedTests"[[:space:]]*:[[:space:]]*[0-9]*' internal-validation-reports/internal-validation-results.json | grep -o '[0-9]*')
                    INTERNAL_CRITICAL_ISSUES=$(grep -o '"criticalIssues"[[:space:]]*:[[:space:]]*[0-9]*' internal-validation-reports/internal-validation-results.json | grep -o '[0-9]*')
                    INTERNAL_SUCCESS_RATE=$(grep -o '"successRate"[[:space:]]*:[[:space:]]*[0-9.]*' internal-validation-reports/internal-validation-results.json | grep -o '[0-9.]*')

                    # Validate that all required values were extracted
                    if [ -z "${'$'}INTERNAL_VALIDATION_STATUS" ] || [ -z "${'$'}INTERNAL_TOTAL_TESTS" ] || [ -z "${'$'}INTERNAL_PASSED_TESTS" ] || [ -z "${'$'}INTERNAL_FAILED_TESTS" ] || [ -z "${'$'}INTERNAL_CRITICAL_ISSUES" ] || [ -z "${'$'}INTERNAL_SUCCESS_RATE" ]; then
                        set_error_state "Failed to parse internal validation results from JSON file"
                        exit 1
                    fi

                    echo "Parsed internal validation results:"
                    echo "  Status: ${'$'}INTERNAL_VALIDATION_STATUS"
                    echo "  Total Tests: ${'$'}INTERNAL_TOTAL_TESTS"
                    echo "  Passed Tests: ${'$'}INTERNAL_PASSED_TESTS"
                    echo "  Failed Tests: ${'$'}INTERNAL_FAILED_TESTS"
                    echo "  Critical Issues: ${'$'}INTERNAL_CRITICAL_ISSUES"
                    echo "  Success Rate: ${'$'}INTERNAL_SUCCESS_RATE"
                else
                    set_error_state "Internal validation results file not found - cannot proceed without validation data"
                    exit 1
                fi

                export INTERNAL_VALIDATION_STATUS
                export INTERNAL_TOTAL_TESTS
                export INTERNAL_PASSED_TESTS
                export INTERNAL_FAILED_TESTS
                export INTERNAL_CRITICAL_ISSUES
                export INTERNAL_SUCCESS_RATE
                export KTOR_VERSION="%env.KTOR_VERSION%"
                export MIN_SCORE="%quality.gate.thresholds.minimum.score%"
                export MAX_CRITICAL="%quality.gate.thresholds.critical.issues%"
                export BASE_SCORE="%quality.gate.scoring.base.score%"
                export EXTERNAL_WEIGHT="%quality.gate.scoring.external.weight%"
                export INTERNAL_WEIGHT="%quality.gate.scoring.internal.weight%"

                echo "Dependency statuses - External: ${'$'}EXTERNAL_STATUS, Internal: ${'$'}INTERNAL_STATUS"

                # Function to set error state
                set_error_state() {
                    local error_message="$1"
                    echo "ERROR: ${'$'}error_message"
                    echo "##teamcity[setParameter name='quality.gate.overall.status' value='ERROR']"
                    echo "##teamcity[setParameter name='quality.gate.overall.score' value='0']"
                    echo "##teamcity[setParameter name='quality.gate.total.critical' value='999']"
                    echo "##teamcity[setParameter name='external.gate.status' value='ERROR']"
                    echo "##teamcity[setParameter name='external.gate.score' value='0']"
                    echo "##teamcity[setParameter name='internal.gate.status' value='ERROR']"
                    echo "##teamcity[setParameter name='internal.gate.score' value='0']"
                    echo "##teamcity[setParameter name='quality.gate.recommendations' value='Fix system errors: ${'$'}error_message']"
                    echo "##teamcity[setParameter name='quality.gate.next.steps' value='Check build logs and resolve technical issues']"
                    echo "##teamcity[setParameter name='quality.gate.failure.reasons' value='System error during evaluation']"
                }

                # Validate that we have dependency information
                if [ "${'$'}EXTERNAL_STATUS" = "UNKNOWN" ] && [ "${'$'}INTERNAL_STATUS" = "UNKNOWN" ]; then
                    set_error_state "No dependency build information available"
                    exit 1
                fi

                echo "Executing quality gate evaluation..."

                cat > QualityGateEvaluator.kts << 'EOF'
#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")

println("=== Starting Quality Gate Evaluation ===")

try {
    // Initialize all variables - fail if any required environment variable is missing
    val externalStatus = System.getenv("EXTERNAL_STATUS") ?: throw IllegalStateException("EXTERNAL_STATUS environment variable is required")
    val externalStatusText = System.getenv("EXTERNAL_STATUS_TEXT") ?: ""
    val internalStatus = System.getenv("INTERNAL_STATUS") ?: throw IllegalStateException("INTERNAL_STATUS environment variable is required")
    val internalStatusText = System.getenv("INTERNAL_STATUS_TEXT") ?: ""
    val eapVersion = System.getenv("KTOR_VERSION") ?: throw IllegalStateException("KTOR_VERSION environment variable is required")
    val minScore = System.getenv("MIN_SCORE")?.toIntOrNull() ?: throw IllegalStateException("MIN_SCORE environment variable is required and must be a valid integer")
    val maxCritical = System.getenv("MAX_CRITICAL")?.toIntOrNull() ?: throw IllegalStateException("MAX_CRITICAL environment variable is required and must be a valid integer")
    val baseScore = System.getenv("BASE_SCORE")?.toIntOrNull() ?: throw IllegalStateException("BASE_SCORE environment variable is required and must be a valid integer")
    val externalWeight = System.getenv("EXTERNAL_WEIGHT")?.toIntOrNull() ?: throw IllegalStateException("EXTERNAL_WEIGHT environment variable is required and must be a valid integer")
    val internalWeight = System.getenv("INTERNAL_WEIGHT")?.toIntOrNull() ?: throw IllegalStateException("INTERNAL_WEIGHT environment variable is required and must be a valid integer")

    // Enhanced internal validation scoring using detailed metrics - fail if any required data is missing
    val internalValidationStatus = System.getenv("INTERNAL_VALIDATION_STATUS") ?: throw IllegalStateException("INTERNAL_VALIDATION_STATUS environment variable is required")
    val internalTotalTests = System.getenv("INTERNAL_TOTAL_TESTS")?.toIntOrNull() ?: throw IllegalStateException("INTERNAL_TOTAL_TESTS environment variable is required and must be a valid integer")
    val internalPassedTests = System.getenv("INTERNAL_PASSED_TESTS")?.toIntOrNull() ?: throw IllegalStateException("INTERNAL_PASSED_TESTS environment variable is required and must be a valid integer")
    val internalFailedTests = System.getenv("INTERNAL_FAILED_TESTS")?.toIntOrNull() ?: throw IllegalStateException("INTERNAL_FAILED_TESTS environment variable is required and must be a valid integer")
    val internalCriticalIssues = System.getenv("INTERNAL_CRITICAL_ISSUES")?.toIntOrNull() ?: throw IllegalStateException("INTERNAL_CRITICAL_ISSUES environment variable is required and must be a valid integer")
    val internalSuccessRate = System.getenv("INTERNAL_SUCCESS_RATE")?.toDoubleOrNull() ?: throw IllegalStateException("INTERNAL_SUCCESS_RATE environment variable is required and must be a valid number")

    // Calculate external score
    val externalScore = when (externalStatus) {
        "SUCCESS" -> baseScore - 5
        "FAILURE" -> baseScore - 50
        else -> baseScore - 30 // For UNKNOWN or other states
    }

    val externalCriticalIssues = when {
        externalStatus != "SUCCESS" -> {
            when {
                externalStatusText.contains("BUILD FAILED", ignoreCase = true) -> 1
                externalStatusText.contains("compilation", ignoreCase = true) -> 1
                else -> if (externalStatus == "FAILURE") 1 else 0
            }
        }
        else -> 0
    }

    val internalScore = when {
        internalValidationStatus == "SUCCESS" && internalSuccessRate >= 95.0 -> baseScore - 5
        internalValidationStatus == "SUCCESS" && internalSuccessRate >= 80.0 -> baseScore - 15
        internalStatus == "SUCCESS" -> baseScore - 10
        internalFailedTests > 0 -> maxOf(0, baseScore - (internalFailedTests * 10) - 20)
        internalStatus == "FAILURE" -> baseScore - 50
        else -> baseScore - 30 // For UNKNOWN or other states
    }

    // Calculate weighted overall score and total critical issues
    val overallScore = ((externalScore * externalWeight) + (internalScore * internalWeight)) / 100
    val totalCritical = externalCriticalIssues + internalCriticalIssues

    // Determine overall status
    val overallStatus = when {
        totalCritical > maxCritical -> "FAILED"
        overallScore < minScore -> "FAILED"
        externalStatus == "SUCCESS" && internalValidationStatus == "SUCCESS" -> "PASSED"
        externalStatus == "SUCCESS" && internalStatus == "SUCCESS" -> "PASSED"
        else -> "FAILED"
    }

    val recommendations = when {
        overallStatus == "PASSED" -> "EAP version ${'$'}eapVersion is ready for release"
        totalCritical > 0 -> "Address ${'$'}totalCritical critical issues before release"
        overallScore < minScore -> "Improve quality score from ${'$'}overallScore to at least ${'$'}minScore"
        else -> "Review failed validations and address issues"
    }

    val nextSteps = when {
        overallStatus == "PASSED" -> "Prepare release notes and documentation for EAP ${'$'}eapVersion"
        totalCritical > 0 -> "Fix critical issues in ${'$'}{if (externalCriticalIssues > 0) "external" else ""}${'$'}{if (externalCriticalIssues > 0 && internalCriticalIssues > 0) " and " else ""}${'$'}{if (internalCriticalIssues > 0) "internal" else ""} validation"
        else -> "Fix identified issues and re-run validation"
    }

    val failureReasons = if (overallStatus != "PASSED") {
        val reasons = mutableListOf<String>()
        if (externalStatus != "SUCCESS") reasons.add("External validation failed")
        if (internalStatus != "SUCCESS") reasons.add("Internal validation failed")
        if (totalCritical > maxCritical) reasons.add("Too many critical issues (${'$'}totalCritical > ${'$'}maxCritical)")
        if (overallScore < minScore) reasons.add("Score too low (${'$'}overallScore < ${'$'}minScore)")
        reasons.joinToString("; ")
    } else ""

    println("External validation status: ${'$'}externalStatus")
    println("Internal validation status: ${'$'}internalStatus")
    println("EAP Version: ${'$'}eapVersion")

    println("=== Quality Gate Evaluation Results ===")
    println("Overall Status: ${'$'}overallStatus")
    println("Overall Score: ${'$'}overallScore/100 (weighted: External ${'$'}{externalWeight}%, Internal ${'$'}{internalWeight}%)")
    println("External Validation: ${'$'}{if (externalStatus == "SUCCESS") "PASSED" else "FAILED"} (${'$'}externalScore/100)")
    println("Internal Validation: ${'$'}{if (internalValidationStatus == "SUCCESS") "PASSED" else "FAILED"} (${'$'}internalScore/100)")
    println("  - Internal Tests: ${'$'}internalPassedTests/${'$'}internalTotalTests passed (${'$'}{internalSuccessRate}%)")
    println("  - Internal Failed Tests: ${'$'}internalFailedTests")
    println("Total Critical Issues: ${'$'}totalCritical (External: ${'$'}externalCriticalIssues, Internal: ${'$'}internalCriticalIssues)")

    // Set all TeamCity parameters with actual calculated values
    println("##teamcity[setParameter name='quality.gate.overall.status' value='${'$'}overallStatus']")
    println("##teamcity[setParameter name='quality.gate.overall.score' value='${'$'}overallScore']")
    println("##teamcity[setParameter name='quality.gate.total.critical' value='${'$'}totalCritical']")
    println("##teamcity[setParameter name='external.gate.status' value='${'$'}{if (externalStatus == "SUCCESS") "PASSED" else "FAILED"}']")
    println("##teamcity[setParameter name='external.gate.score' value='${'$'}externalScore']")
    println("##teamcity[setParameter name='internal.gate.status' value='${'$'}{if (internalStatus == "SUCCESS") "PASSED" else "FAILED"}']")
    println("##teamcity[setParameter name='internal.gate.score' value='${'$'}internalScore']")

    println("##teamcity[setParameter name='quality.gate.recommendations' value='${'$'}recommendations']")
    println("##teamcity[setParameter name='quality.gate.next.steps' value='${'$'}nextSteps']")
    println("##teamcity[setParameter name='quality.gate.failure.reasons' value='${'$'}failureReasons']")

    if (overallStatus == "PASSED") {
        println("✅ All quality gates passed")
    } else {
        println("❌ Quality gates failed: ${'$'}failureReasons")
    }

    println("=== Quality Gate Evaluation Completed Successfully ===")

    if (overallStatus == "PASSED") {
        kotlin.system.exitProcess(0)
    } else {
        kotlin.system.exitProcess(1)
    }

} catch (e: Exception) {
    println("CRITICAL ERROR: Quality gate evaluation failed: ${'$'}{e.message}")
    e.printStackTrace()

    // Set error state parameters
    println("##teamcity[setParameter name='quality.gate.overall.status' value='ERROR']")
    println("##teamcity[setParameter name='quality.gate.overall.score' value='0']")
    println("##teamcity[setParameter name='quality.gate.total.critical' value='999']")
    println("##teamcity[setParameter name='external.gate.status' value='ERROR']")
    println("##teamcity[setParameter name='external.gate.score' value='0']")
    println("##teamcity[setParameter name='internal.gate.status' value='ERROR']")
    println("##teamcity[setParameter name='internal.gate.score' value='0']")
    println("##teamcity[setParameter name='quality.gate.recommendations' value='Fix evaluation system: ${'$'}{e.message}']")
    println("##teamcity[setParameter name='quality.gate.next.steps' value='Check logs, fix system issues, and retry evaluation']")
    println("##teamcity[setParameter name='quality.gate.failure.reasons' value='System error: ${'$'}{e.message}']")

    kotlin.system.exitProcess(1)
}
EOF

                echo "Running Kotlin quality gate evaluation script..."

                if command -v kotlin >/dev/null 2>&1; then
                    echo "Executing Kotlin script..."
                    if kotlin QualityGateEvaluator.kts; then
                        echo "✅ Quality gate evaluation completed successfully"
                    else
                        KOTLIN_EXIT_CODE=$?
                        echo "❌ Quality gate evaluation failed with exit code: ${'$'}KOTLIN_EXIT_CODE"

                        # Ensure parameters are set even on failure
                        echo "##teamcity[setParameter name='quality.gate.overall.status' value='FAILED']"
                        echo "##teamcity[setParameter name='quality.gate.overall.score' value='0']"

                        exit ${'$'}KOTLIN_EXIT_CODE
                    fi
                else
                    echo "⚠️ Kotlin runtime not available, using bash fallback evaluation"

                    # Bash fallback calculation with bc dependency check
                    if ! command -v bc >/dev/null 2>&1; then
                        echo "Installing bc for calculations..."
                        if command -v apt-get >/dev/null 2>&1; then
                            apt-get update && apt-get install -y bc
                        elif command -v yum >/dev/null 2>&1; then
                            yum install -y bc
                        elif command -v apk >/dev/null 2>&1; then
                            apk add --no-cache bc
                        else
                            echo "Warning: bc not available, using basic integer arithmetic"
                        fi
                    fi

                    # Calculate external score using same logic as Kotlin
                    case "${'$'}EXTERNAL_STATUS" in
                        "SUCCESS")
                            EXTERNAL_SCORE=$((BASE_SCORE - 5))  # 95
                            ;;
                        "FAILURE")
                            EXTERNAL_SCORE=$((BASE_SCORE - 50)) # 50
                            ;;
                        *)
                            EXTERNAL_SCORE=$((BASE_SCORE - 30)) # 70 for UNKNOWN
                            ;;
                    esac

                    # Calculate external critical issues
                    EXTERNAL_CRITICAL_ISSUES=0
                    if [ "${'$'}EXTERNAL_STATUS" != "SUCCESS" ]; then
                        if echo "${'$'}EXTERNAL_STATUS_TEXT" | grep -qi "BUILD FAILED\|compilation"; then
                            EXTERNAL_CRITICAL_ISSUES=1
                        elif [ "${'$'}EXTERNAL_STATUS" = "FAILURE" ]; then
                            EXTERNAL_CRITICAL_ISSUES=1
                        fi
                    fi

                    # Use bc if available for floating point comparison, otherwise use integer comparison
                    if command -v bc >/dev/null 2>&1; then
                        SUCCESS_RATE_CHECK_95=$(echo "${'$'}INTERNAL_SUCCESS_RATE >= 95.0" | bc -l)
                        SUCCESS_RATE_CHECK_80=$(echo "${'$'}INTERNAL_SUCCESS_RATE >= 80.0" | bc -l)
                    else
                        # Convert to integer for comparison (multiply by 10 to handle one decimal place)
                        SUCCESS_RATE_INT=$(echo "${'$'}INTERNAL_SUCCESS_RATE" | cut -d'.' -f1)
                        SUCCESS_RATE_CHECK_95=$([ "${'$'}SUCCESS_RATE_INT" -ge 95 ] && echo 1 || echo 0)
                        SUCCESS_RATE_CHECK_80=$([ "${'$'}SUCCESS_RATE_INT" -ge 80 ] && echo 1 || echo 0)
                    fi

                    if [ "${'$'}INTERNAL_VALIDATION_STATUS" = "SUCCESS" ] && [ "${'$'}SUCCESS_RATE_CHECK_95" -eq 1 ]; then
                        INTERNAL_SCORE=$((BASE_SCORE - 5))  # 95
                    elif [ "${'$'}INTERNAL_VALIDATION_STATUS" = "SUCCESS" ] && [ "${'$'}SUCCESS_RATE_CHECK_80" -eq 1 ]; then
                        INTERNAL_SCORE=$((BASE_SCORE - 15)) # 85
                    elif [ "${'$'}INTERNAL_STATUS" = "SUCCESS" ]; then
                        INTERNAL_SCORE=$((BASE_SCORE - 10)) # 90
                    elif [ "${'$'}INTERNAL_FAILED_TESTS" -gt 0 ]; then
                        # Calculate penalty based on failed tests
                        PENALTY=$((INTERNAL_FAILED_TESTS * 10 + 20))
                        INTERNAL_SCORE=$((BASE_SCORE - PENALTY))
                        if [ ${'$'}INTERNAL_SCORE -lt 0 ]; then
                            INTERNAL_SCORE=0
                        fi
                    elif [ "${'$'}INTERNAL_STATUS" = "FAILURE" ]; then
                        INTERNAL_SCORE=$((BASE_SCORE - 50)) # 50
                    else
                        INTERNAL_SCORE=$((BASE_SCORE - 30)) # 70 for UNKNOWN
                    fi

                    # Calculate total critical issues
                    TOTAL_CRITICAL=$((EXTERNAL_CRITICAL_ISSUES + INTERNAL_CRITICAL_ISSUES))

                    # Calculate weighted overall score
                    OVERALL_SCORE=$(((EXTERNAL_SCORE * EXTERNAL_WEIGHT + INTERNAL_SCORE * INTERNAL_WEIGHT) / 100))

                    # Determine overall status using same logic as Kotlin
                    if [ ${'$'}TOTAL_CRITICAL -gt ${'$'}MAX_CRITICAL ]; then
                        OVERALL_STATUS="FAILED"
                    elif [ ${'$'}OVERALL_SCORE -lt ${'$'}MIN_SCORE ]; then
                        OVERALL_STATUS="FAILED"
                    elif [ "${'$'}EXTERNAL_STATUS" = "SUCCESS" ] && [ "${'$'}INTERNAL_VALIDATION_STATUS" = "SUCCESS" ]; then
                        OVERALL_STATUS="PASSED"
                    elif [ "${'$'}EXTERNAL_STATUS" = "SUCCESS" ] && [ "${'$'}INTERNAL_STATUS" = "SUCCESS" ]; then
                        OVERALL_STATUS="PASSED"
                    else
                        OVERALL_STATUS="FAILED"
                    fi

                    # Generate recommendations and next steps
                    if [ "${'$'}OVERALL_STATUS" = "PASSED" ]; then
                        RECOMMENDATIONS="EAP version ${'$'}KTOR_VERSION is ready for release"
                        NEXT_STEPS="Prepare release notes and documentation for EAP ${'$'}KTOR_VERSION"
                        FAILURE_REASONS=""
                    elif [ ${'$'}TOTAL_CRITICAL -gt 0 ]; then
                        RECOMMENDATIONS="Address ${'$'}TOTAL_CRITICAL critical issues before release"
                        CRITICAL_LOCATION=""
                        if [ ${'$'}EXTERNAL_CRITICAL_ISSUES -gt 0 ] && [ ${'$'}INTERNAL_CRITICAL_ISSUES -gt 0 ]; then
                            CRITICAL_LOCATION="external and internal"
                        elif [ ${'$'}EXTERNAL_CRITICAL_ISSUES -gt 0 ]; then
                            CRITICAL_LOCATION="external"
                        elif [ ${'$'}INTERNAL_CRITICAL_ISSUES -gt 0 ]; then
                            CRITICAL_LOCATION="internal"
                        fi
                        NEXT_STEPS="Fix critical issues in ${'$'}CRITICAL_LOCATION validation"
                        FAILURE_REASONS="Too many critical issues (${'$'}TOTAL_CRITICAL > ${'$'}MAX_CRITICAL)"
                    elif [ ${'$'}OVERALL_SCORE -lt ${'$'}MIN_SCORE ]; then
                        RECOMMENDATIONS="Improve quality score from ${'$'}OVERALL_SCORE to at least ${'$'}MIN_SCORE"
                        NEXT_STEPS="Fix identified issues and re-run validation"
                        FAILURE_REASONS="Score too low (${'$'}OVERALL_SCORE < ${'$'}MIN_SCORE)"
                    else
                        RECOMMENDATIONS="Review failed validations and address issues"
                        NEXT_STEPS="Fix identified issues and re-run validation"
                        FAILURE_REASONS=""
                        if [ "${'$'}EXTERNAL_STATUS" != "SUCCESS" ]; then
                            FAILURE_REASONS="External validation failed"
                        fi
                        if [ "${'$'}INTERNAL_STATUS" != "SUCCESS" ]; then
                            if [ -n "${'$'}FAILURE_REASONS" ]; then
                                FAILURE_REASONS="${'$'}FAILURE_REASONS; Internal validation failed"
                            else
                                FAILURE_REASONS="Internal validation failed"
                            fi
                        fi
                    fi

                    echo "=== Bash Fallback Quality Gate Evaluation Results ==="
                    echo "Overall Status: ${'$'}OVERALL_STATUS"
                    echo "Overall Score: ${'$'}OVERALL_SCORE/100 (weighted: External ${'$'}EXTERNAL_WEIGHT%, Internal ${'$'}INTERNAL_WEIGHT%)"
                    echo "External Validation: $([[ "${'$'}EXTERNAL_STATUS" == "SUCCESS" ]] && echo "PASSED" || echo "FAILED") (${'$'}EXTERNAL_SCORE/100)"
                    echo "Internal Validation: $([[ "${'$'}INTERNAL_VALIDATION_STATUS" == "SUCCESS" ]] && echo "PASSED" || echo "FAILED") (${'$'}INTERNAL_SCORE/100)"
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
                        echo "✅ All quality gates passed (bash fallback)"
                    else
                        echo "❌ Quality gates failed: ${'$'}FAILURE_REASONS (bash fallback)"
                    fi

                    echo "✅ Fallback evaluation completed"
                fi

                echo "=== Quality Gate Evaluation Complete ==="
            """.trimIndent()
        }
    }

    private fun BuildSteps.generateReportsAndNotifications() {
        script {
            name = "Generate Enhanced Reports and Notifications"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== Generating Enhanced Reports using Modular Architecture ==="

                # Generate comprehensive report using new report generator
                cat > quality-gate-reports/comprehensive-report.txt <<EOF
EAP Quality Gate Report (Modular Architecture) - %env.KTOR_VERSION%
================================================================
Generated: $(date -Iseconds)
Architecture: Modular Kotlin-based with separation of concerns

Overall Assessment:
- Status: %quality.gate.overall.status%
- Score: %quality.gate.overall.score%/100 (weighted)
- Critical Issues: %quality.gate.total.critical%
- Ready for Release: $([[ "%quality.gate.overall.status%" == "PASSED" ]] && echo "YES" || echo "NO")

Quality Gate Results:
External Validation: %external.gate.status% (%external.gate.score%/100)
Internal Validation: %internal.gate.status% (%internal.gate.score%/100)
Scoring Strategy: 
- External Weight: %quality.gate.scoring.external.weight%%
- Internal Weight: %quality.gate.scoring.internal.weight%%
- Base Score: %quality.gate.scoring.base.score%

Configuration: 
- Minimum Score Threshold: %quality.gate.thresholds.minimum.score%
- Critical Issues Threshold: %quality.gate.thresholds.critical.issues%
- Success Rate Threshold: %quality.gate.thresholds.success.rate%%

Recommendations: %quality.gate.recommendations%
Next Steps: %quality.gate.next.steps%
$([[ "%quality.gate.overall.status%" == "FAILED" ]] && echo "Failure Reasons: %quality.gate.failure.reasons%" || echo "")
EOF

                # Generate JSON report
                # Get values from TeamCity parameters
                OVERALL_SCORE="%quality.gate.overall.score%"
                TOTAL_CRITICAL="%quality.gate.total.critical%"
                EXTERNAL_SCORE="%external.gate.score%"
                INTERNAL_SCORE="%internal.gate.score%"
                MIN_SCORE="%quality.gate.thresholds.minimum.score%"
                CRITICAL_THRESHOLD="%quality.gate.thresholds.critical.issues%"

                # Escape string values to prevent JSON syntax errors from quotes/special chars
                EAP_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/"/\\"/g')
                OVERALL_STATUS=$(echo "%quality.gate.overall.status%" | sed 's/"/\\"/g')
                EXTERNAL_STATUS=$(echo "%external.gate.status%" | sed 's/"/\\"/g')
                INTERNAL_STATUS=$(echo "%internal.gate.status%" | sed 's/"/\\"/g')
                RECOMMENDATIONS=$(echo "%quality.gate.recommendations%" | sed 's/"/\\"/g')
                NEXT_STEPS=$(echo "%quality.gate.next.steps%" | sed 's/"/\\"/g')

                TIMESTAMP=$(date -Iseconds)

                cat > quality-gate-reports/quality-report.json <<EOF
{
    "eapVersion": "${'$'}EAP_VERSION",
    "timestamp": "${'$'}TIMESTAMP",
    "architecture": "modular",
    "overallStatus": "${'$'}OVERALL_STATUS",
    "overallScore": ${'$'}OVERALL_SCORE,
    "totalCriticalIssues": ${'$'}TOTAL_CRITICAL,
    "qualityGates": {
        "externalValidation": {
            "status": "${'$'}EXTERNAL_STATUS",
            "score": ${'$'}EXTERNAL_SCORE,
            "implementation": "ExternalValidationQualityGate"
        },
        "internalValidation": {
            "status": "${'$'}INTERNAL_STATUS",
            "score": ${'$'}INTERNAL_SCORE,
            "implementation": "InternalValidationQualityGate"
        }
    },
    "configuration": {
        "scoringStrategy": "WeightedScoringStrategy",
        "evaluationEngine": "DefaultQualityGateEvaluationEngine",
        "thresholds": {
            "minimumScore": ${'$'}MIN_SCORE,
            "criticalIssues": ${'$'}CRITICAL_THRESHOLD
        }
    },
    "recommendations": "${'$'}RECOMMENDATIONS",
    "nextSteps": "${'$'}NEXT_STEPS",
    "readyForRelease": $([[ "%quality.gate.overall.status%" == "PASSED" ]] && echo "true" || echo "false")
}
EOF

                echo "Enhanced reports generated"
                echo "##teamcity[publishArtifacts 'quality-gate-reports => quality-gate-reports.zip']"
            """.trimIndent()
        }
    }

    private fun BuildSteps.applyQualityGateDecision() {
        script {
            name = "Apply Quality Gate Decision"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/bin/bash

                STATUS="%quality.gate.overall.status%"
                SCORE="%quality.gate.overall.score%"
                VERSION="%env.KTOR_VERSION%"

                echo "=== Final Quality Gate Decision ==="
                echo "EAP Version: ${'$'}VERSION"
                echo "Overall Status: ${'$'}STATUS"
                echo "Overall Score: ${'$'}SCORE/100"

                if [ "${'$'}STATUS" = "PASSED" ]; then
                    echo "✅ Quality gates passed!"
                    echo "Recommendations: %quality.gate.recommendations%"
                    echo "Next Steps: %quality.gate.next.steps%"
                    exit 0
                else
                    echo "❌ Quality gates failed!"
                    echo "Failure Reasons: %quality.gate.failure.reasons%"
                    echo "Recommendations: %quality.gate.recommendations%"
                    echo "Next Steps: %quality.gate.next.steps%"
                    exit 1
                fi
            """.trimIndent()
        }
    }
}
