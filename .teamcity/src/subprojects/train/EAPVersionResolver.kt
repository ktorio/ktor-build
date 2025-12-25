package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.*
import subprojects.*
import subprojects.Agents.ANY
import subprojects.Agents.Arch
import subprojects.Agents.OS
import subprojects.build.*

/**
 * Shared EAP Version Resolver configuration used by both TriggerProjectSamplesOnEAP and ExternalSamplesEAPValidation
 */
object EAPVersionResolver {

    fun createVersionResolver(
        id: String,
        name: String,
        description: String
    ): BuildType {
        return BuildType {
            this.id(id)
            this.name = name
            this.description = description

            vcs {
                coreEap()
            }

            requirements {
                agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)
            }

            params {
                defaultGradleParams()
                param("teamcity.build.skipDependencyBuilds", "true")
                param("teamcity.runAsFirstBuild", "true")
                param("env.KTOR_VERSION", "")
                param("env.KTOR_COMPILER_PLUGIN_VERSION", "")
            }

            steps {
                debugEnvironmentVariables()
                addEAPVersionFetchingSteps()
                addEAPVersionValidationStep()
            }

            failureConditions {
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "ERROR:"
                    failureMessage = "Error detected in version resolution"
                    stopBuildOnFailure = true
                }
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "CRITICAL ERROR:"
                    failureMessage = "Critical error in version resolution"
                    stopBuildOnFailure = true
                }
                executionTimeoutMin = 10
            }
        }
    }
}

fun BuildSteps.addEAPVersionFetchingSteps() {
    script {
        name = "Fetch Latest EAP Ktor Version"
        scriptContent = """
            #!/bin/bash
            set -e

            echo "=== Fetching Latest Ktor EAP Framework Version ==="

            # Fetch Ktor BOM version
            METADATA_URL="${EapConstants.KTOR_EAP_METADATA_URL}"
            TEMP_METADATA=$(mktemp)

            echo "Fetching framework metadata from: ${'$'}METADATA_URL"

            if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}METADATA_URL" -o "${'$'}TEMP_METADATA" 2>/dev/null; then
                echo "Successfully fetched framework metadata"
                echo "Framework metadata content:"
                cat "${'$'}TEMP_METADATA"

                LATEST_EAP_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_METADATA" | sed 's/<latest>//;s/<\/latest>//')

                if [ -z "${'$'}LATEST_EAP_VERSION" ]; then
                    LATEST_EAP_VERSION=$(grep -o "${EapConstants.EAP_VERSION_REGEX}" "${'$'}TEMP_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
                fi

                if [ -n "${'$'}LATEST_EAP_VERSION" ]; then
                    echo "Found latest EAP framework version: ${'$'}LATEST_EAP_VERSION"
                    echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}LATEST_EAP_VERSION']"
                else
                    echo "ERROR: No EAP version found in metadata"
                    echo "Metadata content:"
                    cat "${'$'}TEMP_METADATA"
                    exit 1
                fi

                rm -f "${'$'}TEMP_METADATA"
            else
                echo "ERROR: Failed to fetch framework metadata from ${'$'}METADATA_URL"
                exit 1
            fi

            echo "=== Framework Version Set Successfully ==="
        """.trimIndent()
    }

    script {
        name = "Fetch Latest EAP Ktor Compiler Plugin Version"
        scriptContent = """
            #!/bin/bash
            set -e

            echo "=== Fetching Latest Ktor Compiler Plugin Version ==="

            COMPILER_METADATA_URL="${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}"
            TEMP_COMPILER_METADATA=$(mktemp)

            echo "Fetching compiler plugin metadata from: ${'$'}COMPILER_METADATA_URL"

            if curl -fsSL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 "${'$'}COMPILER_METADATA_URL" -o "${'$'}TEMP_COMPILER_METADATA" 2>/dev/null; then
                echo "Successfully fetched compiler plugin metadata"
                echo "Compiler plugin metadata content:"
                cat "${'$'}TEMP_COMPILER_METADATA"

                LATEST_COMPILER_VERSION=$(grep -o "<latest>[^<]*</latest>" "${'$'}TEMP_COMPILER_METADATA" | sed 's/<latest>//;s/<\/latest>//')

                if [ -z "${'$'}LATEST_COMPILER_VERSION" ]; then
                    LATEST_COMPILER_VERSION=$(grep -o "${EapConstants.EAP_VERSION_REGEX}" "${'$'}TEMP_COMPILER_METADATA" | sed 's/[><]//g' | sort -V | tail -n 1)
                fi

                if [ -n "${'$'}LATEST_COMPILER_VERSION" ]; then
                    echo "Found latest compiler plugin version: ${'$'}LATEST_COMPILER_VERSION"
                    echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}LATEST_COMPILER_VERSION']"
                else
                    echo "ERROR: No compiler plugin version found in metadata"
                    echo "Metadata content:"
                    cat "${'$'}TEMP_COMPILER_METADATA"
                    exit 1
                fi

                rm -f "${'$'}TEMP_COMPILER_METADATA"
            else
                echo "ERROR: Failed to fetch compiler plugin metadata from ${'$'}COMPILER_METADATA_URL"
                exit 1
            fi

            echo "=== Compiler Plugin Version Set Successfully ==="
        """.trimIndent()
    }
}

fun BuildSteps.addKotlinVersionFetchingStep() {
    script {
        name = "Fetch Kotlin Version from Project Repository"
        scriptContent = """
            #!/bin/bash
            set -e

            echo "=== Fetching Kotlin Version from Project Repository ==="

            if [ ! -f "gradle/libs.versions.toml" ]; then
                echo "ERROR: gradle/libs.versions.toml not found in repository"
                echo "Available files in current directory:"
                ls -la
                echo "Available files in gradle directory (if exists):"
                ls -la gradle/ || echo "gradle directory not found"
                exit 1
            fi

            echo "Found gradle/libs.versions.toml, extracting Kotlin version..."
            cat gradle/libs.versions.toml

            KOTLIN_VERSION=$(grep -E '^kotlin\s*=' gradle/libs.versions.toml | sed 's/.*=\s*"\([^"]*\)".*/\1/' | head -n 1)

            if [ -z "${'$'}KOTLIN_VERSION" ]; then
                # Try alternative patterns
                KOTLIN_VERSION=$(grep -E '^kotlinVersion\s*=' gradle/libs.versions.toml | sed 's/.*=\s*"\([^"]*\)".*/\1/' | head -n 1)
            fi

            if [ -z "${'$'}KOTLIN_VERSION" ]; then
                # Try kotlin-version pattern
                KOTLIN_VERSION=$(grep -E '^kotlin-version\s*=' gradle/libs.versions.toml | sed 's/.*=\s*"\([^"]*\)".*/\1/' | head -n 1)
            fi

            if [ -z "${'$'}KOTLIN_VERSION" ]; then
                # Fallback: check build.gradle.kts files
                echo "Kotlin version not found in libs.versions.toml, checking build.gradle.kts files..."
                KOTLIN_VERSION=$(find . -name "build.gradle.kts" -exec grep -h "kotlin.*version" {} \; | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+' | head -n 1)
            fi

            if [ -n "${'$'}KOTLIN_VERSION" ]; then
                echo "Found Kotlin version: ${'$'}KOTLIN_VERSION"
                echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='${'$'}KOTLIN_VERSION']"
            else
                echo "ERROR: Could not determine Kotlin version from repository"
                echo "Contents of gradle/libs.versions.toml:"
                cat gradle/libs.versions.toml
                exit 1
            fi

            echo "=== Kotlin Version Set Successfully ==="
        """.trimIndent()
    }
}

fun BuildSteps.addEAPVersionValidationStep() {
    script {
        name = "Final Validation"
        scriptContent = """
            #!/bin/bash
            set -e

            echo "=== Final Validation of Resolved Versions ==="

            if [ -z "%env.KTOR_VERSION%" ] || [ "%env.KTOR_VERSION%" = "" ]; then
                echo "CRITICAL ERROR: KTOR_VERSION is not set after resolution"
                exit 1
            fi

            if [ -z "%env.KTOR_COMPILER_PLUGIN_VERSION%" ] || [ "%env.KTOR_COMPILER_PLUGIN_VERSION%" = "" ]; then
                echo "CRITICAL ERROR: KTOR_COMPILER_PLUGIN_VERSION is not set after resolution"
                exit 1
            fi

            echo "✓ Framework version validated: %env.KTOR_VERSION%"
            echo "✓ Compiler plugin version validated: %env.KTOR_COMPILER_PLUGIN_VERSION%"
            echo "=== Version Resolution SUCCESSFUL ==="
        """.trimIndent()
    }
}
