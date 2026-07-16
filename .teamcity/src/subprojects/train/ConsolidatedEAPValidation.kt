package subprojects.train

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
        "https://redirector.kotlinlang.org/maven/ktor-eap/io/ktor/plugin/io.ktor.plugin.gradle.plugin/maven-metadata.xml"
    const val KOTLIN_EAP_METADATA_URL =
        "https://redirector.kotlinlang.org/maven/dev/org/jetbrains/kotlin/kotlin-compiler-embeddable/maven-metadata.xml"
}

/**
 * Consolidated EAP validation, split into a per-OS build chain:
 *
 * - [resolveBuild] resolves the versions to validate and writes `eap-version.properties`.
 * - Each [validatorBuild] publishes the PR's Ktor for its own host and validates only the samples
 *   routed to that OS (see [EapSampleRouting]), emitting `os-results/<os>-*.properties` + reports.
 * - [aggregateBuild] (kept id `ConsolidatedEAPValidation`) sums the per-OS counts, then runs the
 *   quality gate, failure investigation and report generation once.
 */
object ConsolidatedEAPValidation {

    private const val AGGREGATE_ID = "ConsolidatedEAPValidation"

    fun createConsolidatedProject(): Project =
        Project {
            id("ConsolidatedEAPValidationProject")
            name = "Consolidated EAP Validation"
            description = "Consolidated EAP validation project for external and internal projects"

            val resolve = resolveBuild()
            val validators = EapSampleRouting.active.map { os -> validatorBuild(os, resolve) }
            val aggregate = aggregateBuild(resolve, validators)

            features {
                feature {
                    type = "ReportTab"
                    param("title", "Quality Gate Report")
                    param("startPage", "quality-gate-reports.zip!quality-gate-report.html")
                    param("type", "BuildReportTab")
                    param("buildTypeId", AGGREGATE_ID)
                }
            }

            buildType(resolve)
            validators.forEach(::buildType)
            buildType(aggregate)
        }

    private fun ParametrizedWithType.eapVersionParams() {
        param("env.EAP_VALIDATION_MODE", "source")
        param("env.KTOR_VERSION", "")
        param("env.KTOR_COMPILER_PLUGIN_VERSION", "")
        param("env.KOTLIN_VERSION", "2.3.10")
        param("env.KTOR_PR_TARGETS", "")
        param("env.KTOR_PR_REPO", "")
        param("env.KTOR_PR_REPO_DIR", "")

        param("teamcity.pullRequest.number", "")
        param("teamcity.pullRequest.targetBranch", "")

        param("version.resolution.errors", "0")
    }

    private fun ParametrizedWithType.eapValidatorParams() {
        param("env.TRY_COMPILE_ON_FAILURE", "true")
        param("env.ANDROID_HOME", "%android-sdk.location%")
        param("env.JAVA_HOME", Env.JDK_LTS)
        defaultGradleParams()
    }

    private fun resolveBuild(): BuildType =
        BuildType {
            id("EAPResolveVersions")
            name = "EAP Validation — Resolve Versions"
            description = "Resolves the Ktor/Kotlin versions (or PR source version) to validate"

            artifactRules = """
                eap-version.properties => .
                version-resolution-reports => version-resolution-reports
            """.trimIndent()

            params { eapVersionParams() }

            vcs {
                root(VCSCore, "+:. => ktor")
                branchFilter = "+:*"
                cleanCheckout = true
            }

            steps { VersionResolutionStep.applyResolveOnly(this) }

            features { githubPullRequestsLoader(VCSCore.id) }

            requirements {
                agent(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
                exists("env.JAVA_HOME")
            }
        }

    private fun validatorBuild(os: Agents.OS, resolve: BuildType): BuildType =
        BuildType {
            id("EAPValidate${os.id}")
            name = "EAP Validation — Samples on ${os.id}"
            description = "Publishes the PR's Ktor on ${os.id} and validates the samples routed to it"

            artifactRules = """
                os-results => os-results
                external-validation-reports => external-validation-reports
                internal-validation-reports => internal-validation-reports
                failed-samples => failed-samples
            """.trimIndent()

            params {
                eapVersionParams()
                eapValidatorParams()
            }

            vcs {
                root(VCSCore, "+:. => ktor")
                root(VCSSamples, "+:. => samples")
                root(VCSKtorBuildPlugins, "+:. => ktor-build-plugins")
                branchFilter = "+:*"
                cleanCheckout = true
            }

            dependencies {
                snapshot(resolve) {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                    reuseBuilds = ReuseBuilds.SUCCESSFUL
                }
                artifacts(resolve.id!!) {
                    artifactRules = "eap-version.properties => ."
                }
            }

            steps {
                VersionResolutionStep.applyPublish(this)
                ExternalSamplesValidationStep.apply(this, os)
                InternalTestSuitesStep.apply(this, os)
            }

            features { githubPullRequestsLoader(VCSCore.id) }

            requirements {
                when (os) {
                    Agents.OS.Linux -> {
                        agent(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
                        exists("env.JAVA_HOME")
                        exists("docker.server.version")
                    }
                    Agents.OS.MacOS -> {
                        agent(Agents.OS.MacOS, Agents.Arch.Arm64, Agents.MEDIUM)
                    }
                    Agents.OS.Windows -> {
                        agent(Agents.OS.Windows, hardwareCapacity = Agents.LARGE)
                    }
                }
            }
        }

    private fun aggregateBuild(resolve: BuildType, validators: List<BuildType>): BuildType =
        BuildType {
            id(AGGREGATE_ID)
            name = "Consolidated EAP Validation"
            description = "Aggregates the per-OS validation results and evaluates the quality gate"

            artifactRules = """
                version-resolution-reports => version-resolution-reports.zip
                external-validation-reports => external-validation-reports.zip
                internal-validation-reports => internal-validation-reports.zip
                quality-gate-reports => quality-gate-reports.zip
                failed-samples/*.zip => failed-samples.zip
            """.trimIndent()

            params {
                eapVersionParams()

                // Quality Gate Configuration Parameters
                param("quality.gate.scoring.external.weight", "60")
                param("quality.gate.scoring.internal.weight", "40")
                param("quality.gate.thresholds.minimum.score", "80")
                param("quality.gate.thresholds.critical.issues", "0")

                // Optional Slack webhook for detailed notifications
                password("env.SLACK_WEBHOOK_URL", "%system.slack.webhook.url%")

                // YouTrack integration: on failure the investigation step files a KTOR issue.
                password("env.YOUTRACK_TOKEN", "%system.youtrack.token%")
                param("env.YOUTRACK_URL", "https://youtrack.jetbrains.com")
                param("env.YOUTRACK_PROJECT", "KTOR")
                param("env.YOUTRACK_TAG", "ktor-eap-validation")
                param("quality.gate.youtrack.issue", "")

                // External validation aggregate parameters
                param("external.validation.total.samples", "0")
                param("external.validation.successful.samples", "0")
                param("external.validation.failed.samples", "0")
                param("external.validation.skipped.samples", "0")
                param("external.validation.success.rate", "0.0")

                // Internal validation aggregate parameters
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
            }

            vcs {
                root(VCSCore, "+:. => ktor")
                branchFilter = "+:*"
                cleanCheckout = true
            }

            dependencies {
                snapshot(resolve) {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                    reuseBuilds = ReuseBuilds.SUCCESSFUL
                }
                artifacts(resolve.id!!) {
                    artifactRules = """
                        eap-version.properties => .
                        version-resolution-reports => version-resolution-reports
                    """.trimIndent()
                }
                validators.forEach { validator ->
                    snapshot(validator) {
                        onDependencyFailure = FailureAction.ADD_PROBLEM
                        onDependencyCancel = FailureAction.CANCEL
                        reuseBuilds = ReuseBuilds.SUCCESSFUL
                    }
                    artifacts(validator.id!!) {
                        artifactRules = """
                            os-results => os-results
                            external-validation-reports => external-validation-reports
                            internal-validation-reports => internal-validation-reports
                            failed-samples => failed-samples
                        """.trimIndent()
                    }
                }
            }

            steps {
                AggregateResultsStep.apply(this)
                QualityGateEvaluationStep.apply(this)
                FailureInvestigationStep.apply(this)
                ReportGenerationStep.apply(this)
            }

            features {
                githubPullRequestsLoader(VCSCore.id)
                githubCommitStatusPublisher(VCSCore.id)
            }

            triggers {
                schedule {
                    schedulingPolicy = weekly {
                        dayOfWeek = Sunday
                        hour = 22
                    }
                    branchFilter = BranchFilter.DefaultBranch
                    triggerBuild = always()
                    param("reverse.dep.*.env.EAP_VALIDATION_MODE", "published")
                }

                vcs {
                    branchFilter = BranchFilter.DefaultOrPullRequest
                    triggerRules = "+:root=${VCSCore.id}:**"
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

            requirements {
                agent(Agents.OS.Linux, hardwareCapacity = Agents.MEDIUM)
                exists("env.JAVA_HOME")
            }
        }
}
