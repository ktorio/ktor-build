package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

/**
 * Aggregation step for the `EAPValidateAggregate` build.
 *
 * The per-OS validator builds each publish `os-results/<os>-external.properties` and
 * `os-results/<os>-internal.properties`; the aggregate downloads them all (via artifact
 * dependencies) into `os-results/`. This step sums those counts into the single
 * `external.validation.*` / `internal.validation.*` build parameters that
 * [QualityGateEvaluationStep] and [ReportGenerationStep] already consume, and re-publishes the
 * resolved versions from `eap-version.properties` (also downloaded from the resolve build).
 *
 * Runs ALWAYS so the quality gate and report still run when a validator failed.
 */
object AggregateResultsStep {
    fun apply(steps: BuildSteps) {
        steps.script {
            name = "Aggregate per-OS validation results"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/bin/bash

                echo "=== Aggregate per-OS validation results ==="

                # Re-publish the versions resolved by EAPResolveVersions so the report/gate steps see them.
                if [ -f eap-version.properties ]; then
                    source eap-version.properties
                    echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}{KTOR_VERSION:-}']"
                    echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='${'$'}{KOTLIN_VERSION:-2.3.10}']"
                    echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}{KTOR_COMPILER_PLUGIN_VERSION:-}']"
                    echo "##teamcity[setParameter name='env.EAP_VALIDATION_MODE' value='${'$'}{EAP_VALIDATION_MODE:-source}']"
                    echo "##teamcity[setParameter name='version.resolution.errors' value='${'$'}{VERSION_RESOLUTION_ERRORS:-0}']"
                else
                    echo "⚠️  eap-version.properties not found — report will show unknown versions"
                fi

                get() {
                    grep -E "^${'$'}2=" "${'$'}1" 2>/dev/null | head -1 | cut -d= -f2 | grep -E '^[0-9]+${'$'}' || echo 0
                }

                EXT_TOTAL=0; EXT_OK=0; EXT_FAIL=0; EXT_SKIP=0
                for f in os-results/*-external.properties; do
                    [ -f "${'$'}f" ] || continue
                    echo "--- ${'$'}f ---"; cat "${'$'}f"
                    EXT_TOTAL=$((EXT_TOTAL + $(get "${'$'}f" external_total)))
                    EXT_OK=$((EXT_OK + $(get "${'$'}f" external_successful)))
                    EXT_FAIL=$((EXT_FAIL + $(get "${'$'}f" external_failed)))
                    EXT_SKIP=$((EXT_SKIP + $(get "${'$'}f" external_skipped)))
                done

                INT_TOTAL=0; INT_PASS=0; INT_FAIL=0; INT_ERR=0; INT_SKIP=0
                for f in os-results/*-internal.properties; do
                    [ -f "${'$'}f" ] || continue
                    echo "--- ${'$'}f ---"; cat "${'$'}f"
                    INT_TOTAL=$((INT_TOTAL + $(get "${'$'}f" internal_total)))
                    INT_PASS=$((INT_PASS + $(get "${'$'}f" internal_passed)))
                    INT_FAIL=$((INT_FAIL + $(get "${'$'}f" internal_failed)))
                    INT_ERR=$((INT_ERR + $(get "${'$'}f" internal_error)))
                    INT_SKIP=$((INT_SKIP + $(get "${'$'}f" internal_skipped)))
                done

                EXT_RATE=$(awk -v s="${'$'}EXT_OK" -v t="${'$'}EXT_TOTAL" 'BEGIN{ if (t>0) printf "%.1f", s*100/t; else printf "0.0" }')
                INT_RATE=$(awk -v s="${'$'}INT_PASS" -v t="${'$'}INT_TOTAL" 'BEGIN{ if (t>0) printf "%.1f", s*100/t; else printf "0.0" }')

                echo "==================================================="
                echo "Aggregated External: ${'$'}EXT_OK/${'$'}EXT_TOTAL passed (${'$'}EXT_RATE%), failed ${'$'}EXT_FAIL, skipped ${'$'}EXT_SKIP"
                echo "Aggregated Internal: ${'$'}INT_PASS/${'$'}INT_TOTAL passed (${'$'}INT_RATE%), failed ${'$'}INT_FAIL, errors ${'$'}INT_ERR, skipped ${'$'}INT_SKIP"

                echo "##teamcity[setParameter name='external.validation.total.samples' value='${'$'}EXT_TOTAL']"
                echo "##teamcity[setParameter name='external.validation.successful.samples' value='${'$'}EXT_OK']"
                echo "##teamcity[setParameter name='external.validation.failed.samples' value='${'$'}EXT_FAIL']"
                echo "##teamcity[setParameter name='external.validation.skipped.samples' value='${'$'}EXT_SKIP']"
                echo "##teamcity[setParameter name='external.validation.success.rate' value='${'$'}EXT_RATE']"

                echo "##teamcity[setParameter name='internal.validation.total.tests' value='${'$'}INT_TOTAL']"
                echo "##teamcity[setParameter name='internal.validation.passed.tests' value='${'$'}INT_PASS']"
                echo "##teamcity[setParameter name='internal.validation.failed.tests' value='${'$'}INT_FAIL']"
                echo "##teamcity[setParameter name='internal.validation.error.tests' value='${'$'}INT_ERR']"
                echo "##teamcity[setParameter name='internal.validation.skipped.tests' value='${'$'}INT_SKIP']"
                echo "##teamcity[setParameter name='internal.validation.success.rate' value='${'$'}INT_RATE']"

                echo "=== Aggregation complete ==="
            """.trimIndent()
        }
    }
}
