package subprojects.train

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
