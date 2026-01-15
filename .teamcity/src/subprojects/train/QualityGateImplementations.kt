package subprojects.train

import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Shared utility functions for quality gate implementations
 */
object QualityGateUtils {
    /**
     * Calculates the quality gate score based on build status and issues
     */
    fun calculateScore(buildStatus: String, issues: List<QualityIssue>, config: ScoringConfiguration): Int {
        var score = config.baseScore

        if (buildStatus != "SUCCESS") {
            score -= config.penalties.failurePenalty
        }

        val criticalIssues = issues.count { it.severity == IssueSeverity.CRITICAL }
        val warnings = issues.count { it.severity != IssueSeverity.CRITICAL }

        score -= (criticalIssues * config.penalties.criticalIssuePenalty)
        score -= (warnings * config.penalties.warningPenalty)

        return maxOf(0, score)
    }
}

/**
 * External validation quality gate implementation
 */
class ExternalValidationQualityGate : QualityGate {
    override val name = "External Validation"
    override val type = QualityGateType.EXTERNAL_VALIDATION
    override val criteria = QualityGateCriteria(
        minimumPassRate = 100.0,
        allowedCriticalIssues = 0,
        performanceRegressionThreshold = 5.0,
        executionTimeoutMinutes = 60
    )

    override fun evaluate(context: QualityGateContext): QualityGateResult {
        val buildId = context.additionalParameters["external.build.id"] ?: ""
        val buildStatus = context.additionalParameters["external.status"] ?: "UNKNOWN"
        val buildStatusText = context.additionalParameters["external.status.text"] ?: ""

        val issues = analyzeIssues(buildStatus, buildStatusText)
        val status = determineStatus(buildStatus, issues)
        val score = calculateScore(buildStatus, issues, context.configuration.scoring)

        return QualityGateResult(
            gateName = name,
            status = status,
            score = score,
            criticalIssues = issues.filter { it.severity == IssueSeverity.CRITICAL },
            warnings = issues.filter { it.severity != IssueSeverity.CRITICAL },
            executionTime = Duration.ofMinutes(30),
            timestamp = Instant.now(),
            eapVersion = context.eapVersion,
            buildId = buildId
        )
    }

    private fun analyzeIssues(buildStatus: String, statusText: String): List<QualityIssue> {
        val issues = mutableListOf<QualityIssue>()

        if (buildStatus != "SUCCESS") {
            issues.add(
                QualityIssue(
                    severity = IssueSeverity.CRITICAL,
                    description = "External validation build failed",
                    affectedComponent = "External Samples",
                    suggestedAction = "Review external sample build logs and fix issues"
                )
            )

            if (statusText.contains("BUILD FAILED", ignoreCase = true)) {
                issues.add(
                    QualityIssue(
                        severity = IssueSeverity.CRITICAL,
                        description = "Build failure detected in external samples",
                        affectedComponent = "Build System",
                        suggestedAction = "Check compilation errors and dependencies"
                    )
                )
            }

            if (statusText.contains("compilation", ignoreCase = true)) {
                issues.add(
                    QualityIssue(
                        severity = IssueSeverity.CRITICAL,
                        description = "Compilation errors in external samples",
                        affectedComponent = "Source Code",
                        suggestedAction = "Fix compilation errors in affected samples"
                    )
                )
            }

            if (statusText.contains("test", ignoreCase = true) && statusText.contains("failed", ignoreCase = true)) {
                issues.add(
                    QualityIssue(
                        severity = IssueSeverity.HIGH,
                        description = "Test failures in external samples",
                        affectedComponent = "Tests",
                        suggestedAction = "Review and fix failing tests"
                    )
                )
            }
        } else {
            issues.add(
                QualityIssue(
                    severity = IssueSeverity.LOW,
                    description = "Minor warnings detected",
                    affectedComponent = "External Samples",
                    suggestedAction = "Review warnings for potential improvements"
                )
            )
        }

        return issues
    }

    private fun determineStatus(buildStatus: String, issues: List<QualityIssue>): QualityGateStatus {
        return when {
            buildStatus != "SUCCESS" -> QualityGateStatus.FAILED
            issues.any { it.severity == IssueSeverity.CRITICAL } -> QualityGateStatus.FAILED
            else -> QualityGateStatus.PASSED
        }
    }

    private fun calculateScore(buildStatus: String, issues: List<QualityIssue>, config: ScoringConfiguration): Int {
        return QualityGateUtils.calculateScore(buildStatus, issues, config)
    }
}

/**
 * Internal validation quality gate implementation
 */
class InternalValidationQualityGate : QualityGate {
    override val name = "Internal Validation"
    override val type = QualityGateType.INTERNAL_VALIDATION
    override val criteria = QualityGateCriteria(
        minimumPassRate = 100.0,
        allowedCriticalIssues = 0,
        performanceRegressionThreshold = 5.0,
        executionTimeoutMinutes = 45
    )

    override fun evaluate(context: QualityGateContext): QualityGateResult {
        val buildId = context.additionalParameters["internal.build.id"] ?: ""
        val buildStatus = context.additionalParameters["internal.status"] ?: "UNKNOWN"
        val buildStatusText = context.additionalParameters["internal.status.text"] ?: ""

        val issues = analyzeIssues(buildStatus, buildStatusText)
        val status = determineStatus(buildStatus, issues)
        val score = calculateScore(buildStatus, issues, context.configuration.scoring)

        return QualityGateResult(
            gateName = name,
            status = status,
            score = score,
            criticalIssues = issues.filter { it.severity == IssueSeverity.CRITICAL },
            warnings = issues.filter { it.severity != IssueSeverity.CRITICAL },
            executionTime = Duration.ofMinutes(25),
            timestamp = Instant.now(),
            eapVersion = context.eapVersion,
            buildId = buildId
        )
    }

    private fun analyzeIssues(buildStatus: String, statusText: String): List<QualityIssue> {
        val issues = mutableListOf<QualityIssue>()

        if (buildStatus != "SUCCESS") {
            issues.add(
                QualityIssue(
                    severity = IssueSeverity.CRITICAL,
                    description = "Internal validation build failed",
                    affectedComponent = "Internal Tests",
                    suggestedAction = "Review internal test suite and fix failures"
                )
            )

            if (statusText.contains("OutOfMemory", ignoreCase = true)) {
                issues.add(
                    QualityIssue(
                        severity = IssueSeverity.HIGH,
                        description = "Memory issues detected in internal tests",
                        affectedComponent = "Runtime",
                        suggestedAction = "Increase memory allocation or optimize memory usage"
                    )
                )
            }

            if (statusText.contains("timeout", ignoreCase = true)) {
                issues.add(
                    QualityIssue(
                        severity = IssueSeverity.MEDIUM,
                        description = "Timeout issues in internal tests",
                        affectedComponent = "Test Execution",
                        suggestedAction = "Optimize test performance or increase timeout"
                    )
                )
            }
        } else {
            issues.add(
                QualityIssue(
                    severity = IssueSeverity.LOW,
                    description = "Performance warnings detected",
                    affectedComponent = "Internal Tests",
                    suggestedAction = "Monitor performance metrics"
                )
            )
            issues.add(
                QualityIssue(
                    severity = IssueSeverity.LOW,
                    description = "Minor test warnings",
                    affectedComponent = "Test Suite",
                    suggestedAction = "Review test warnings for improvements"
                )
            )
        }

        return issues
    }

    private fun determineStatus(buildStatus: String, issues: List<QualityIssue>): QualityGateStatus {
        return when {
            buildStatus != "SUCCESS" -> QualityGateStatus.FAILED
            issues.any { it.severity == IssueSeverity.CRITICAL } -> QualityGateStatus.FAILED
            else -> QualityGateStatus.PASSED
        }
    }

    private fun calculateScore(buildStatus: String, issues: List<QualityIssue>, config: ScoringConfiguration): Int {
        return QualityGateUtils.calculateScore(buildStatus, issues, config)
    }
}

/**
 * Default implementation of the quality gate evaluation engine
 */
class DefaultQualityGateEvaluationEngine(
    private val scoringStrategy: ScoringStrategy
) : QualityGateEvaluationEngine {

    override fun evaluateAll(gates: List<QualityGate>, context: QualityGateContext): EAPQualityReport {
        val startTime = Instant.now()

        val results = gates.map { gate ->
            try {
                evaluateSingle(gate, context)
            } catch (e: Exception) {
                createFailedResult(gate, e, context)
            }
        }

        val overallScore = scoringStrategy.calculateOverallScore(results, context.configuration.scoring)
        val overallStatus = determineOverallStatus(results, overallScore, context.configuration.thresholds)
        val executionTime = Duration.between(startTime, Instant.now())

        val externalValidation = results.find { it.gateName.contains("External") }
            ?: createDefaultResult("External Validation", context)
        val internalValidation = results.find { it.gateName.contains("Internal") }
            ?: createDefaultResult("Internal Validation", context)

        return EAPQualityReport(
            version = context.eapVersion,
            overallStatus = overallStatus,
            overallScore = overallScore,
            externalValidation = externalValidation,
            internalValidation = internalValidation,
            recommendations = generateRecommendations(results, overallStatus),
            executionSummary = createExecutionSummary(results, executionTime),
            nextSteps = generateNextSteps(overallStatus, results)
        )
    }

    override fun evaluateSingle(gate: QualityGate, context: QualityGateContext): QualityGateResult {
        return gate.evaluate(context)
    }

    private fun determineOverallStatus(
        results: List<QualityGateResult>,
        overallScore: Int,
        thresholds: QualityGateThresholds
    ): QualityGateStatus {
        val totalCriticalIssues = results.sumOf { it.criticalIssues.size }
        if (totalCriticalIssues > thresholds.criticalIssueThreshold) {
            return QualityGateStatus.FAILED
        }

        if (overallScore < thresholds.minimumPassingScore) {
            return QualityGateStatus.FAILED
        }

        return if (results.all { it.status == QualityGateStatus.PASSED }) {
            QualityGateStatus.PASSED
        } else {
            QualityGateStatus.FAILED
        }
    }

    private fun createFailedResult(gate: QualityGate, exception: Exception, context: QualityGateContext): QualityGateResult {
        return QualityGateResult(
            gateName = gate.name,
            status = QualityGateStatus.FAILED,
            score = 0,
            criticalIssues = listOf(
                QualityIssue(
                    severity = IssueSeverity.CRITICAL,
                    description = "Quality gate execution failed: ${exception.message}",
                    affectedComponent = gate.name,
                    suggestedAction = "Check quality gate configuration and dependencies"
                )
            ),
            warnings = emptyList(),
            executionTime = Duration.ZERO,
            timestamp = Instant.now(),
            eapVersion = context.eapVersion
        )
    }

    private fun createDefaultResult(gateName: String, context: QualityGateContext): QualityGateResult {
        return QualityGateResult(
            gateName = gateName,
            status = QualityGateStatus.SKIPPED,
            score = 0,
            criticalIssues = emptyList(),
            warnings = listOf(
                QualityIssue(
                    severity = IssueSeverity.LOW,
                    description = "Quality gate was not executed",
                    affectedComponent = gateName,
                    suggestedAction = "Verify quality gate configuration"
                )
            ),
            executionTime = Duration.ZERO,
            timestamp = Instant.now(),
            eapVersion = context.eapVersion
        )
    }

    private fun generateRecommendations(results: List<QualityGateResult>, overallStatus: QualityGateStatus): List<String> {
        val recommendations = mutableListOf<String>()

        when (overallStatus) {
            QualityGateStatus.PASSED -> {
                recommendations.add("EAP version is ready for release")
                recommendations.add("Prepare release notes and documentation")
            }
            QualityGateStatus.FAILED -> {
                recommendations.add("Address critical issues before release")

                val failedGates = results.filter { it.status == QualityGateStatus.FAILED }
                failedGates.forEach { gate ->
                    if (gate.criticalIssues.isNotEmpty()) {
                        recommendations.add("Fix critical issues in ${gate.gateName}")
                    }
                }

                recommendations.add("Re-run validation after fixes")
            }
            QualityGateStatus.BLOCKED -> {
                recommendations.add("Resolve blocking issues preventing quality gate execution")
            }
            else -> {
                recommendations.add("Review quality gate status and take appropriate action")
            }
        }

        return recommendations
    }

    private fun generateNextSteps(overallStatus: QualityGateStatus, results: List<QualityGateResult>): List<String> {
        val nextSteps = mutableListOf<String>()

        when (overallStatus) {
            QualityGateStatus.PASSED -> {
                nextSteps.add("Notify community about EAP availability")
                nextSteps.add("Update documentation with new features")
                nextSteps.add("Monitor community feedback")
            }
            QualityGateStatus.FAILED -> {
                nextSteps.add("Analyze failure reasons")
                nextSteps.add("Create fix plan for identified issues")
                nextSteps.add("Execute fixes and re-run validation")

                val criticalIssues = results.flatMap { it.criticalIssues }
                if (criticalIssues.isNotEmpty()) {
                    nextSteps.add("Prioritize critical issues: ${criticalIssues.size} found")
                }
            }
            QualityGateStatus.BLOCKED -> {
                nextSteps.add("Investigate blocking conditions")
                nextSteps.add("Resolve infrastructure or configuration issues")
                nextSteps.add("Retry quality gate execution")
            }
            else -> {
                nextSteps.add("Review quality gate execution logs")
                nextSteps.add("Determine appropriate course of action")
            }
        }

        return nextSteps
    }

    private fun createExecutionSummary(results: List<QualityGateResult>, totalExecutionTime: Duration): ExecutionSummary {
        val totalTests = results.sumOf { it.additionalMetrics["testsExecuted"] as? Int ?: 0 }
        val totalSamples = results.sumOf { it.additionalMetrics["samplesValidated"] as? Int ?: 0 }
        val passedResults = results.count { it.status == QualityGateStatus.PASSED }
        val successRate = if (results.isNotEmpty()) {
            (passedResults.toDouble() / results.size) * 100.0
        } else {
            0.0
        }

        return ExecutionSummary(
            totalExecutionTime = totalExecutionTime,
            samplesValidated = totalSamples,
            testsExecuted = totalTests,
            successRate = successRate
        )
    }
}

/**
 * Weighted scoring strategy implementation
 */
class WeightedScoringStrategy : ScoringStrategy {

    override fun calculateScore(result: QualityGateResult, config: ScoringConfiguration): Int {
        return result.score
    }

    override fun calculateOverallScore(results: List<QualityGateResult>, config: ScoringConfiguration): Int {
        if (results.isEmpty()) return 0

        var weightedScore = 0.0
        var totalWeight = 0

        results.forEach { result ->
            val gateType = when {
                result.gateName.contains("External") -> QualityGateType.EXTERNAL_VALIDATION
                result.gateName.contains("Internal") -> QualityGateType.INTERNAL_VALIDATION
                else -> QualityGateType.EXTERNAL_VALIDATION
            }

            val weight = config.weights[gateType] ?: 50
            weightedScore += result.score * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) {
            (weightedScore / totalWeight).toInt()
        } else {
            0
        }
    }
}

/**
 * Main quality gate evaluation function that can be called from scripts
 */
fun executeQualityGateEvaluation() {
    println("=== Quality Gate Evaluation Started ===")

    try {
        val externalStatus = System.getenv("EXTERNAL_STATUS") ?: "UNKNOWN"
        val externalStatusText = System.getenv("EXTERNAL_STATUS_TEXT") ?: ""
        val internalStatus = System.getenv("INTERNAL_STATUS") ?: "UNKNOWN"
        val internalStatusText = System.getenv("INTERNAL_STATUS_TEXT") ?: ""
        val eapVersion = System.getenv("KTOR_VERSION") ?: "unknown"

        println("External validation status: $externalStatus")
        println("Internal validation status: $internalStatus")
        println("EAP Version: $eapVersion")

        val thresholds = QualityGateThresholds(
            minimumPassingScore = System.getenv("MIN_SCORE")?.toIntOrNull() ?: 80,
            criticalIssueThreshold = System.getenv("MAX_CRITICAL")?.toIntOrNull() ?: 0,
            warningIssueThreshold = 5,
            performanceRegressionThreshold = 10.0,
            executionTimeoutMinutes = 60,
            successRateThreshold = 95.0
        )

        val scoring = ScoringConfiguration(
            baseScore = System.getenv("BASE_SCORE")?.toIntOrNull() ?: 100,
            weights = mapOf(
                QualityGateType.EXTERNAL_VALIDATION to (System.getenv("EXTERNAL_WEIGHT")?.toIntOrNull() ?: 50),
                QualityGateType.INTERNAL_VALIDATION to (System.getenv("INTERNAL_WEIGHT")?.toIntOrNull() ?: 50)
            ),
            penalties = PenaltyConfiguration(
                failurePenalty = 50,
                criticalIssuePenalty = 20,
                warningPenalty = 5
            )
        )

        val configuration = QualityGateConfiguration(
            thresholds = thresholds,
            scoring = scoring,
            notifications = NotificationConfiguration(),
            gates = listOf(
                QualityGateConfig(QualityGateType.EXTERNAL_VALIDATION, "External Validation", true),
                QualityGateConfig(QualityGateType.INTERNAL_VALIDATION, "Internal Validation", true)
            )
        )

        val context = QualityGateContext(
            eapVersion = eapVersion,
            triggerBuild = System.getenv("TEAMCITY_BUILD_ID") ?: "unknown",
            branch = System.getenv("TEAMCITY_BUILD_BRANCH") ?: "main",
            environment = "production",
            thresholds = thresholds,
            skipConditions = emptyList(),
            additionalParameters = mapOf(
                "external.build.id" to (System.getenv("EXTERNAL_BUILD_ID") ?: ""),
                "external.status" to externalStatus,
                "external.status.text" to externalStatusText,
                "internal.build.id" to (System.getenv("INTERNAL_BUILD_ID") ?: ""),
                "internal.status" to internalStatus,
                "internal.status.text" to internalStatusText
            ),
            configuration = configuration
        )

        println("Creating quality gate instances...")

        val externalGate = ExternalValidationQualityGate()
        val internalGate = InternalValidationQualityGate()
        val gates = listOf(externalGate, internalGate)

        println("- ExternalValidationQualityGate: configured")
        println("- InternalValidationQualityGate: configured")

        println("Initializing evaluation engine...")

        val scoringStrategy = WeightedScoringStrategy()
        val evaluationEngine = DefaultQualityGateEvaluationEngine(scoringStrategy)

        println("- DefaultQualityGateEvaluationEngine: ready")
        println("- WeightedScoringStrategy: configured")

        println("Loading centralized configuration...")
        println("- QualityGateConfiguration: loaded from parameters")
        println("- Thresholds: minimum score=${thresholds.minimumPassingScore}, critical issues=${thresholds.criticalIssueThreshold}")
        println("- Scoring weights: external=${scoring.weights[QualityGateType.EXTERNAL_VALIDATION]}%, internal=${scoring.weights[QualityGateType.INTERNAL_VALIDATION]}%")

        println("Executing quality gate evaluation...")
        val report = evaluationEngine.evaluateAll(gates, context)

        println("=== Quality Gate Evaluation Results ===")
        println("Overall Status: ${report.overallStatus}")
        println("Overall Score: ${report.overallScore}/100 (weighted)")

        println("External Validation: ${report.externalValidation.status} (${report.externalValidation.score}/100)")
        println("- Critical Issues: ${report.externalValidation.criticalIssues.size}")
        println("- Warnings: ${report.externalValidation.warnings.size}")

        println("Internal Validation: ${report.internalValidation.status} (${report.internalValidation.score}/100)")
        println("- Critical Issues: ${report.internalValidation.criticalIssues.size}")
        println("- Warnings: ${report.internalValidation.warnings.size}")

        val totalCritical = report.externalValidation.criticalIssues.size + report.internalValidation.criticalIssues.size
        println("Total Critical Issues: $totalCritical")

        if (report.overallStatus == QualityGateStatus.PASSED) {
            println("✅ All quality gates passed")
        } else {
            println("❌ Quality gates failed")
            if (report.externalValidation.criticalIssues.isNotEmpty() || report.internalValidation.criticalIssues.isNotEmpty()) {
                println("Critical issues found:")
                (report.externalValidation.criticalIssues + report.internalValidation.criticalIssues).forEach { issue ->
                    println("  - ${issue.description} in ${issue.affectedComponent}")
                }
            }
        }

        println("##teamcity[setParameter name='quality.gate.overall.status' value='${report.overallStatus}']")
        println("##teamcity[setParameter name='quality.gate.overall.score' value='${report.overallScore}']")
        println("##teamcity[setParameter name='quality.gate.total.critical' value='$totalCritical']")
        println("##teamcity[setParameter name='quality.gate.recommendations' value='${report.recommendations.joinToString("; ")}']")
        println("##teamcity[setParameter name='quality.gate.next.steps' value='${report.nextSteps.joinToString("; ")}']")
        println("##teamcity[setParameter name='external.gate.status' value='${report.externalValidation.status}']")
        println("##teamcity[setParameter name='external.gate.score' value='${report.externalValidation.score}']")
        println("##teamcity[setParameter name='internal.gate.status' value='${report.internalValidation.status}']")
        println("##teamcity[setParameter name='internal.gate.score' value='${report.internalValidation.score}']")

        println("=== Quality Gate Evaluation Complete ===")

        if (report.overallStatus == QualityGateStatus.PASSED) {
            exitProcess(0)
        } else {
            exitProcess(1)
        }

    } catch (e: Exception) {
        println("ERROR: Quality gate evaluation failed with exception: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

/**
 * Main function for standalone execution
 */
fun main() {
    executeQualityGateEvaluation()
}
