package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

/**
 * Step 5: Report Generation & Notifications
 * Generates comprehensive reports and sends notifications
 * Always runs to ensure reports are available even for failed builds
 */
object ReportGenerationStep {
    fun apply(steps: BuildSteps) {
        steps.script {
            name = "Step 5: Report Generation & Notifications"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/bin/bash

                echo "=== Step 5: Report Generation & Notifications ==="
                echo "Generating comprehensive reports and sending notifications"
                echo "Timestamp: $(date -Iseconds)"

                # Read all runtime parameter values with safe defaults and parameter extraction
                KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | grep -v "^%env\.KTOR_VERSION%$" || echo "unknown")
                KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | grep -E '^[0-9.]+$' || echo "2.1.21")
                KTOR_COMPILER_PLUGIN_VERSION=$(echo "%env.KTOR_COMPILER_PLUGIN_VERSION%" | grep -v "^%env\.KTOR_COMPILER_PLUGIN_VERSION%$" || echo "N/A")
                
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

                OVERALL_STATUS=$(echo "%quality.gate.overall.status%" | grep -v "^%quality\.gate\.overall\.status%$" || echo "UNKNOWN")
                OVERALL_SCORE=$(echo "%quality.gate.overall.score%" | grep -E '^[0-9]+$' || echo "0")
                TOTAL_CRITICAL=$(echo "%quality.gate.total.critical%" | grep -E '^[0-9]+$' || echo "0")

                EXTERNAL_GATE_STATUS=$(echo "%external.gate.status%" | grep -v "^%external\.gate\.status%$" || echo "UNKNOWN")
                EXTERNAL_GATE_SCORE=$(echo "%external.gate.score%" | grep -E '^[0-9]+$' || echo "0")
                EXTERNAL_TOTAL_SAMPLES=$(echo "%external.validation.total.samples%" | grep -E '^[0-9]+$' || echo "0")
                EXTERNAL_SUCCESSFUL_SAMPLES=$(echo "%external.validation.successful.samples%" | grep -E '^[0-9]+$' || echo "0")
                EXTERNAL_FAILED_SAMPLES=$(echo "%external.validation.failed.samples%" | grep -E '^[0-9]+$' || echo "0")
                EXTERNAL_SKIPPED_SAMPLES=$(echo "%external.validation.skipped.samples%" | grep -E '^[0-9]+$' || echo "0")
                EXTERNAL_SUCCESS_RATE=$(echo "%external.validation.success.rate%" | grep -E '^[0-9.]+$' || echo "0.0")

                INTERNAL_GATE_STATUS=$(echo "%internal.gate.status%" | grep -v "^%internal\.gate\.status%$" || echo "UNKNOWN")
                INTERNAL_GATE_SCORE=$(echo "%internal.gate.score%" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_TOTAL_TESTS=$(echo "%internal.validation.total.tests%" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_PASSED_TESTS=$(echo "%internal.validation.passed.tests%" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_FAILED_TESTS=$(echo "%internal.validation.failed.tests%" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_ERROR_TESTS=$(echo "%internal.validation.error.tests%" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_SKIPPED_TESTS=$(echo "%internal.validation.skipped.tests%" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_SUCCESS_RATE=$(echo "%internal.validation.success.rate%" | grep -E '^[0-9.]+$' || echo "0.0")

                RECOMMENDATIONS=$(echo "%quality.gate.recommendations%" | grep -v "^%quality\.gate\.recommendations%$" || echo "Quality gate evaluation not completed")
                NEXT_STEPS=$(echo "%quality.gate.next.steps%" | grep -v "^%quality\.gate\.next\.steps%$" || echo "Review validation results")
                FAILURE_REASONS=$(echo "%quality.gate.failure.reasons%" | grep -v "^%quality\.gate\.failure\.reasons%$" || echo "")

                VERSION_ERRORS=$(echo "%version.resolution.errors%" | grep -E '^[0-9]+$' || echo "0")

                # Read quality gate configuration parameters
                EXTERNAL_WEIGHT=$(echo "%quality.gate.scoring.external.weight%" | grep -E '^[0-9]+$' || echo "60")
                INTERNAL_WEIGHT=$(echo "%quality.gate.scoring.internal.weight%" | grep -E '^[0-9]+$' || echo "40")
                MINIMUM_SCORE=$(echo "%quality.gate.thresholds.minimum.score%" | grep -E '^[0-9]+$' || echo "80")
                CRITICAL_ISSUES_THRESHOLD=$(echo "%quality.gate.thresholds.critical.issues%" | grep -E '^[0-9]+$' || echo "0")

                STATUS_CLASS="status-unknown"
                PROGRESS_CLASS="progress-danger"
                if [ "${'$'}OVERALL_STATUS" = "PASSED" ]; then
                    STATUS_CLASS="status-passed"
                    PROGRESS_CLASS="progress-success"
                elif [ "${'$'}OVERALL_STATUS" = "FAILED" ]; then
                    STATUS_CLASS="status-failed"
                fi

                EXTERNAL_STATUS_CLASS="status-unknown"
                if [ "${'$'}EXTERNAL_GATE_STATUS" = "PASSED" ]; then
                    EXTERNAL_STATUS_CLASS="status-passed"
                elif [ "${'$'}EXTERNAL_GATE_STATUS" = "FAILED" ]; then
                    EXTERNAL_STATUS_CLASS="status-failed"
                fi

                INTERNAL_STATUS_CLASS="status-unknown"
                if [ "${'$'}INTERNAL_GATE_STATUS" = "PASSED" ]; then
                    INTERNAL_STATUS_CLASS="status-passed"
                elif [ "${'$'}INTERNAL_GATE_STATUS" = "FAILED" ]; then
                    INTERNAL_STATUS_CLASS="status-failed"
                fi

                CRITICAL_ISSUES_COLOR="#cb2431"
                if [ "${'$'}TOTAL_CRITICAL" = "0" ]; then
                    CRITICAL_ISSUES_COLOR="#22863a"
                fi

                FAILURE_REASONS_BLOCK=""
                if [ -n "${'$'}FAILURE_REASONS" ]; then
                    FAILURE_REASONS_BLOCK=$(cat <<EOF
        <div style="margin-top: 20px;">
            <strong>Failure Reasons:</strong>
            <pre style="background: #f8f9fa; padding: 15px; border-radius: 4px; overflow-x: auto; font-family: monospace; font-size: 13px;">${'$'}{FAILURE_REASONS}</pre>
        </div>
EOF
                    )
                fi

                echo "=== Report Data Summary ==="
                echo "EAP Version: ${'$'}KTOR_VERSION"
                echo "Overall Status: ${'$'}OVERALL_STATUS"
                echo "Overall Score: ${'$'}OVERALL_SCORE/100"
                echo "Critical Issues: ${'$'}TOTAL_CRITICAL"

                mkdir -p quality-gate-reports

                # Generate HTML report for TeamCity
                cat > quality-gate-reports/quality-gate-report.html <<EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quality Gate Report - ${'$'}{KTOR_VERSION}</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #333; max-width: 1000px; margin: 0 auto; padding: 20px; background-color: #f5f7f9; }
        .card { background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); padding: 25px; margin-bottom: 25px; }
        .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #eee; padding-bottom: 15px; margin-bottom: 20px; }
        h1, h2, h3 { margin-top: 0; }
        .status-badge { padding: 8px 16px; border-radius: 20px; font-weight: bold; text-transform: uppercase; font-size: 14px; }
        .status-passed { background-color: #e6ffed; color: #22863a; border: 1px solid #28a745; }
        .status-failed { background-color: #ffeef0; color: #cb2431; border: 1px solid #d73a49; }
        .status-unknown { background-color: #f1f8ff; color: #0366d6; border: 1px solid #0366d6; }
        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }
        .metric { margin-bottom: 15px; }
        .metric-label { font-size: 14px; color: #666; margin-bottom: 5px; }
        .metric-value { font-size: 24px; font-weight: bold; }
        .progress-bar { height: 10px; background-color: #eee; border-radius: 5px; margin-top: 10px; overflow: hidden; }
        .progress-fill { height: 100%; }
        .progress-success { background-color: #28a745; }
        .progress-danger { background-color: #dc3545; }
        .details-table { width: 100%; border-collapse: collapse; margin-top: 15px; }
        .details-table th, .details-table td { text-align: left; padding: 12px; border-bottom: 1px solid #eee; }
        .details-table th { background-color: #f8f9fa; font-weight: 600; }
        .recommendation { background-color: #fffbdd; border-left: 4px solid #d4a017; padding: 15px; border-radius: 0 4px 4px 0; }
        .footer { text-align: center; font-size: 12px; color: #999; margin-top: 40px; }
    </style>
</head>
<body>
    <div class="card">
        <div class="header">
            <div>
                <h1>Quality Gate Report</h1>
                <div style="color: #666;">Ktor EAP Validation • ${'$'}{KTOR_VERSION}</div>
            </div>
            <span class="status-badge ${'$'}{STATUS_CLASS}">${'$'}{OVERALL_STATUS}</span>
        </div>
        
        <div class="grid">
            <div class="metric">
                <div class="metric-label">Overall Weighted Score</div>
                <div class="metric-value">${'$'}{OVERALL_SCORE}/100</div>
                <div class="progress-bar">
                    <div class="progress-fill ${'$'}{PROGRESS_CLASS}" style="width: ${'$'}{OVERALL_SCORE}%"></div>
                </div>
            </div>
            <div class="metric">
                <div class="metric-label">Critical Issues</div>
                <div class="metric-value" style="color: ${'$'}{CRITICAL_ISSUES_COLOR};">${'$'}{TOTAL_CRITICAL}</div>
                <div style="font-size: 12px; color: #666; margin-top: 5px;">
                    Threshold: ${'$'}{CRITICAL_ISSUES_THRESHOLD} allowed
                </div>
            </div>
        </div>
    </div>

    <div class="grid">
        <div class="card">
            <h3>External Samples</h3>
            <div class="metric">
                <div class="metric-label">Gate Status</div>
                <div class="status-badge ${'$'}{EXTERNAL_STATUS_CLASS}" style="display: inline-block; padding: 4px 10px; font-size: 12px;">${'$'}{EXTERNAL_GATE_STATUS}</div>
            </div>
            <div class="metric">
                <div class="metric-label">Success Rate</div>
                <div class="metric-value">${'$'}{EXTERNAL_SUCCESS_RATE}%</div>
                <div style="font-size: 14px; color: #666;">${'$'}{EXTERNAL_SUCCESSFUL_SAMPLES} / ${'$'}{EXTERNAL_TOTAL_SAMPLES} samples passed</div>
            </div>
            <table class="details-table">
                <tr><th>Metric</th><th>Value</th></tr>
                <tr><td>Total Samples</td><td>${'$'}{EXTERNAL_TOTAL_SAMPLES}</td></tr>
                <tr><td>Successful</td><td>${'$'}{EXTERNAL_SUCCESSFUL_SAMPLES}</td></tr>
                <tr><td>Failed</td><td>${'$'}{EXTERNAL_FAILED_SAMPLES}</td></tr>
                <tr><td>Skipped</td><td>${'$'}{EXTERNAL_SKIPPED_SAMPLES}</td></tr>
                <tr><td>Gate Score</td><td>${'$'}{EXTERNAL_GATE_SCORE}/100</td></tr>
                <tr><td>Scoring Weight</td><td>${'$'}{EXTERNAL_WEIGHT}%</td></tr>
            </table>
        </div>

        <div class="card">
            <h3>Internal Test Suites</h3>
            <div class="metric">
                <div class="metric-label">Gate Status</div>
                <div class="status-badge ${'$'}{INTERNAL_STATUS_CLASS}" style="display: inline-block; padding: 4px 10px; font-size: 12px;">${'$'}{INTERNAL_GATE_STATUS}</div>
            </div>
            <div class="metric">
                <div class="metric-label">Success Rate</div>
                <div class="metric-value">${'$'}{INTERNAL_SUCCESS_RATE}%</div>
                <div style="font-size: 14px; color: #666;">${'$'}{INTERNAL_PASSED_TESTS} / ${'$'}{INTERNAL_TOTAL_TESTS} tests passed</div>
            </div>
            <table class="details-table">
                <tr><th>Metric</th><th>Value</th></tr>
                <tr><td>Total Tests</td><td>${'$'}{INTERNAL_TOTAL_TESTS}</td></tr>
                <tr><td>Passed</td><td>${'$'}{INTERNAL_PASSED_TESTS}</td></tr>
                <tr><td>Failed</td><td>${'$'}{INTERNAL_FAILED_TESTS}</td></tr>
                <tr><td>Errors</td><td>${'$'}{INTERNAL_ERROR_TESTS}</td></tr>
                <tr><td>Skipped</td><td>${'$'}{INTERNAL_SKIPPED_TESTS}</td></tr>
                <tr><td>Gate Score</td><td>${'$'}{INTERNAL_GATE_SCORE}/100</td></tr>
                <tr><td>Scoring Weight</td><td>${'$'}{INTERNAL_WEIGHT}%</td></tr>
            </table>
        </div>
    </div>

    <div class="card">
        <h3>Analysis & Next Steps</h3>
        <div class="recommendation">
            <strong>Recommendation:</strong> ${'$'}{RECOMMENDATIONS}
        </div>
        <p><strong>Next Steps:</strong> ${'$'}{NEXT_STEPS}</p>
        ${'$'}{FAILURE_REASONS_BLOCK}
    </div>

    <div class="card">
        <h3>Environment Details</h3>
        <table class="details-table">
            <tr><th>Property</th><th>Value</th></tr>
            <tr><td>Ktor Framework</td><td>${'$'}{KTOR_VERSION}</td></tr>
            <tr><td>Kotlin Version</td><td>${'$'}{KOTLIN_VERSION}</td></tr>
            <tr><td>Compiler Plugin</td><td>${'$'}{KTOR_COMPILER_PLUGIN_VERSION:-N/A}</td></tr>
            <tr><td>Version Resolution Errors</td><td>${'$'}{VERSION_ERRORS}</td></tr>
            <tr><td>Minimum Required Score</td><td>${'$'}{MINIMUM_SCORE}</td></tr>
            <tr><td>VCS Revision</td><td>${'$'}{BUILD_VCS_NUMBER}</td></tr>
            <tr><td>Build Agent</td><td>${'$'}{AGENT_NAME}</td></tr>
            <tr><td>Validation Time</td><td>$(date)</td></tr>
        </table>
    </div>

    <div class="footer">
        Ktor Consolidated EAP Validation System • Generated by TeamCity
    </div>
</body>
</html>
EOF

                echo "HTML report generated at quality-gate-reports/quality-gate-report.html"
                
                echo "=== Step 5: Report Generation Completed ==="
            """.trimIndent()
        }
    }
}
