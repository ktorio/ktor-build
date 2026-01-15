package subprojects.train

import java.time.Duration
import java.time.Instant

/**
 * Core abstraction for quality gates
 */
interface QualityGate {
    val name: String
    val type: QualityGateType
    val criteria: QualityGateCriteria
    fun evaluate(context: QualityGateContext): QualityGateResult
}

/**
 * Quality gate evaluation engine interface
 */
interface QualityGateEvaluationEngine {
    fun evaluateAll(gates: List<QualityGate>, context: QualityGateContext): EAPQualityReport
    fun evaluateSingle(gate: QualityGate, context: QualityGateContext): QualityGateResult
}

/**
 * Scoring strategy interface
 */
interface ScoringStrategy {
    fun calculateScore(result: QualityGateResult, config: ScoringConfiguration): Int
    fun calculateOverallScore(results: List<QualityGateResult>, config: ScoringConfiguration): Int
}

/**
 * Quality gate types
 */
enum class QualityGateType {
    EXTERNAL_VALIDATION,
    INTERNAL_VALIDATION
}

/**
 * Quality gate criteria for evaluation
 */
data class QualityGateCriteria(
    val minimumPassRate: Double = 100.0,
    val allowedCriticalIssues: Int = 0,
    val performanceRegressionThreshold: Double = 5.0,
    val executionTimeoutMinutes: Int = 60
)

/**
 * Scoring configuration for quality gates
 */
data class ScoringConfiguration(
    val baseScore: Int = 100,
    val weights: Map<QualityGateType, Int> = mapOf(
        QualityGateType.EXTERNAL_VALIDATION to 50,
        QualityGateType.INTERNAL_VALIDATION to 50
    ),
    val penalties: PenaltyConfiguration = PenaltyConfiguration()
)

/**
 * Penalty configuration for scoring
 */
data class PenaltyConfiguration(
    val failurePenalty: Int = 50,
    val criticalIssuePenalty: Int = 20,
    val warningPenalty: Int = 5
)

/**
 * Represents the status of a quality gate
 */
enum class QualityGateStatus {
    PASSED,
    FAILED,
    BLOCKED,
    SKIPPED
}

/**
 * Severity levels for quality issues
 */
enum class IssueSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Represents a specific quality issue found during validation
 */
data class QualityIssue(
    val severity: IssueSeverity,
    val description: String,
    val affectedComponent: String,
    val suggestedAction: String,
    val relatedBuild: String? = null,
    val errorDetails: String? = null
)

/**
 * Result of a quality gate execution
 */
data class QualityGateResult(
    val gateName: String,
    val status: QualityGateStatus,
    val score: Int,
    val criticalIssues: List<QualityIssue>,
    val warnings: List<QualityIssue>,
    val executionTime: Duration,
    val timestamp: Instant,
    val eapVersion: String,
    val buildId: String? = null,
    val additionalMetrics: Map<String, Any> = emptyMap()
)

/**
 * Comprehensive report of EAP quality assessment
 */
data class EAPQualityReport(
    val version: String,
    val overallStatus: QualityGateStatus,
    val overallScore: Int,
    val externalValidation: QualityGateResult,
    val internalValidation: QualityGateResult,
    val recommendations: List<String>,
    val executionSummary: ExecutionSummary,
    val nextSteps: List<String> = emptyList()
)

/**
 * Summary of quality gate execution
 */
data class ExecutionSummary(
    val totalExecutionTime: Duration,
    val samplesValidated: Int,
    val testsExecuted: Int,
    val successRate: Double
)

/**
 * Context information for quality gate execution
 */
data class QualityGateContext(
    val eapVersion: String,
    val triggerBuild: String,
    val branch: String,
    val environment: String = "production",
    val thresholds: QualityGateThresholds = QualityGateThresholds(),
    val skipConditions: List<String> = emptyList(),
    val additionalParameters: Map<String, String> = emptyMap(),
    val configuration: QualityGateConfiguration = QualityGateConfiguration.default()
)

/**
 * Configuration for quality gate thresholds
 */
data class QualityGateThresholds(
    val minimumPassingScore: Int = 80,
    val criticalIssueThreshold: Int = 0,
    val warningIssueThreshold: Int = 5,
    val performanceRegressionThreshold: Double = 10.0,
    val executionTimeoutMinutes: Int = 60,
    val successRateThreshold: Double = 95.0
)

/**
 * Centralized configuration for quality gates
 */
data class QualityGateConfiguration(
    val thresholds: QualityGateThresholds,
    val scoring: ScoringConfiguration,
    val notifications: NotificationConfiguration,
    val gates: List<QualityGateConfig>
) {
    companion object {

        fun default(): QualityGateConfiguration {
            return QualityGateConfiguration(
                thresholds = QualityGateThresholds(),
                scoring = ScoringConfiguration(),
                notifications = NotificationConfiguration(),
                gates = listOf(
                    QualityGateConfig(QualityGateType.EXTERNAL_VALIDATION, "External Validation", true),
                    QualityGateConfig(QualityGateType.INTERNAL_VALIDATION, "Internal Validation", true)
                )
            )
        }
    }
}

/**
 * Configuration for individual quality gates
 */
data class QualityGateConfig(
    val type: QualityGateType,
    val name: String,
    val enabled: Boolean,
    val criteria: QualityGateCriteria = QualityGateCriteria()
)

/**
 * Notification configuration
 */
data class NotificationConfiguration(
    val mainChannel: String = "#ktor-projects-on-eap",
    val alertsChannel: String = "#ktor-projects-on-eap",
    val connection: String = "PROJECT_EXT_5",
    val enableRichFormatting: Boolean = true,
    val enableProblemReporting: Boolean = true
)
