package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

/**
 * Step 4: Quality Gate Evaluation
 * Evaluates all validation results against quality gate criteria
 * Always runs regardless of previous step outcomes
 */
object QualityGateEvaluationStep {
    fun apply(steps: BuildSteps) {
        steps.script {
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

                if [ "${'$'}SCORE_CHECK" = "PASSED" ] && [ "${'$'}CRITICAL_CHECK" = "PASSED" ]; then
                    OVERALL_STATUS="PASSED"
                    RECOMMENDATIONS="EAP validation passed successfully. Ready for release."
                    NEXT_STEPS="Proceed with EAP release process"
                else
                    if [ "${'$'}SCORE_CHECK" = "FAILED" ]; then
                        FAILURE_REASONS="${'$'}FAILURE_REASONS- Overall score (${'$'}OVERALL_SCORE) is below threshold (${'$'}MINIMUM_SCORE)\n"
                    fi
                    if [ "${'$'}CRITICAL_CHECK" = "FAILED" ]; then
                        FAILURE_REASONS="${'$'}FAILURE_REASONS- Critical issues count (${'$'}TOTAL_CRITICAL) exceeds threshold (${'$'}CRITICAL_THRESHOLD)\n"
                    fi
                fi

                echo ""
                echo "=== FINAL DECISION: ${'$'}OVERALL_STATUS ==="

                # Report results to TeamCity
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

                # Set Slack emojis based on status
                STATUS_EMOJI="❌"
                if [ "${'$'}OVERALL_STATUS" = "PASSED" ]; then STATUS_EMOJI="✅"; fi
                
                EXTERNAL_EMOJI="❌"
                if [ "${'$'}EXTERNAL_GATE_STATUS" = "PASSED" ]; then EXTERNAL_EMOJI="✅"; fi
                
                INTERNAL_EMOJI="❌"
                if [ "${'$'}INTERNAL_GATE_STATUS" = "PASSED" ]; then INTERNAL_EMOJI="✅"; fi

                CRITICAL_EMOJI="✅"
                if [ "${'$'}TOTAL_CRITICAL" -gt 0 ]; then CRITICAL_EMOJI="❌"; fi

                echo "##teamcity[setParameter name='quality.gate.slack.status.emoji' value='${'$'}STATUS_EMOJI']"
                echo "##teamcity[setParameter name='quality.gate.slack.external.emoji' value='${'$'}EXTERNAL_EMOJI']"
                echo "##teamcity[setParameter name='quality.gate.slack.internal.emoji' value='${'$'}INTERNAL_EMOJI']"
                echo "##teamcity[setParameter name='quality.gate.slack.critical.emoji' value='${'$'}CRITICAL_EMOJI']"

                # Explicitly fail the build if quality gate failed
                if [ "${'$'}OVERALL_STATUS" = "FAILED" ]; then
                    echo "QUALITY_GATE_FAILED: The validation results do not meet the required quality standards."
                fi

                echo "=== Step 4: Quality Gate Evaluation Completed ==="
                
                # Always exit successfully here - the build failure condition will handle the actual failure
                exit 0
            """.trimIndent()
        }
    }
}
