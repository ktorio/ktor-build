package subprojects.train

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.failureConditions.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger.DAY.Sunday
import subprojects.*
import subprojects.build.*

object EapConstants {
    const val KTOR_EAP_METADATA_URL =
        "https://redirector.kotlinlang.org/maven/ktor-eap/io/ktor/ktor-bom/maven-metadata.xml"
    const val KTOR_COMPILER_PLUGIN_METADATA_URL =
        "https://redirector.kotlinlang.org/maven/ktor-eap/io/ktor/ktor-compiler-plugin/maven-metadata.xml"
    const val KOTLIN_EAP_METADATA_URL =
        "https://redirector.kotlinlang.org/maven/dev/org/jetbrains/kotlin/kotlin-compiler-embeddable/maven-metadata.xml"
}

object ConsolidatedEAPValidation {
    fun createConsolidatedProject(): Project =
        Project {
            id("ConsolidatedEAPValidationProject")
            name = "Consolidated EAP Validation"
            description = "Consolidated EAP validation project for external and internal projects"

            features {
                feature {
                    type = "ReportTab"
                    param("title", "Quality Gate Report")
                    param("startPage", "quality-gate-reports.zip!quality-gate-report.html")
                    param("type", "BuildReportTab")
                    param("buildTypeId", "ConsolidatedEAPValidation")
                }
            }

            buildType(createConsolidatedBuild())

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
                param("env.KOTLIN_VERSION", "2.3.10")

                // Version resolution parameters
                param("version.resolution.errors", "0")

                // External validation parameters
                param("env.TRY_COMPILE_ON_FAILURE", "true")
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

                // Android SDK parameters
                param("env.ANDROID_HOME", "%android-sdk.location%")

                // Slack notification parameters
                param("quality.gate.slack.status.emoji", "⏳")
                param("quality.gate.slack.external.emoji", "⏳")
                param("quality.gate.slack.internal.emoji", "⏳")
                param("quality.gate.slack.critical.emoji", "⏳")

                defaultGradleParams()
            }

            vcs {
                root(VCSCore, "+:. => ktor")
                root(VCSSamples, "+:. => samples")
                root(VCSKtorBuildPlugins, "+:. => ktor-build-plugins")
                branchFilter = "+:*"
                cleanCheckout = true
            }

            steps {
                VersionResolutionStep.apply(this)
                ExternalSamplesValidationStep.apply(this)
                InternalTestSuitesStep.apply(this)
                QualityGateEvaluationStep.apply(this)
                ReportGenerationStep.apply(this)
            }

            triggers {
                schedule {
                    schedulingPolicy = weekly {
                        dayOfWeek = Sunday
                        hour = 22
                    }
                    triggerBuild = always()
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
                agent(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
                exists("env.JAVA_HOME")
                exists("docker.server.version")
            }
        }

}
