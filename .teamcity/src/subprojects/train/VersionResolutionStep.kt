package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

/**
 * Step 1: Version Resolution
 * Fetches the latest EAP versions for Ktor framework, compiler plugin, and Kotlin
 * Continues even if some versions fail to fetch
 */
object VersionResolutionStep {
    fun apply(steps: BuildSteps) {
        steps.script {
            name = "Step 1: Version Resolution"
            scriptContent = """
                #!/bin/bash
            
                echo "=== Step 1: Version Resolution ==="
                echo "Fetching latest EAP versions for Ktor framework, compiler plugin, and Kotlin"

                mkdir -p version-resolution-reports

                FETCH_ERRORS=0
                VERSION_REPORT=""

                CURL_FLAGS=(-sSfL --connect-timeout 10 --max-time 30 --retry 3 --retry-delay 2 --retry-all-errors)

                # Fetch Ktor Framework EAP version
                echo "Fetching Ktor Framework EAP version..."
                KTOR_VERSION=""
                KTOR_METADATA=""
                if KTOR_METADATA=$(curl "${'$'}{CURL_FLAGS[@]}" "${EapConstants.KTOR_EAP_METADATA_URL}"); then
                    KTOR_VERSION=$(echo "${'$'}KTOR_METADATA" \
                        | grep -oE "<version>[^<]+</version>" \
                        | sed -E 's#</?version>##g' \
                        | grep -E -- "-eap-" \
                        | grep -vE -- "-rc|-beta|-alpha" \
                        | sort -V \
                        | tail -1 || true)

                    if [ -n "${'$'}KTOR_VERSION" ]; then
                        echo "✅ Latest Ktor EAP version: ${'$'}KTOR_VERSION"
                        echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}KTOR_VERSION']"
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Framework: ${'$'}KTOR_VERSION (SUCCESS)\n"
                    else
                        echo "❌ Failed to parse Ktor EAP version from metadata (no -eap- versions found)"
                        FETCH_ERRORS=$((FETCH_ERRORS + 1))
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Framework: PARSE_ERROR\n"
                    fi
                else
                    echo "❌ Failed to fetch Ktor EAP version from ${EapConstants.KTOR_EAP_METADATA_URL}"
                    FETCH_ERRORS=$((FETCH_ERRORS + 1))
                    VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Framework: FETCH_ERROR\n"
                fi

                # Fetch Ktor Compiler Plugin EAP version
                echo "Fetching Ktor Compiler Plugin EAP version..."
                KTOR_COMPILER_PLUGIN_VERSION=""
                KTOR_PLUGIN_METADATA=""
                if KTOR_PLUGIN_METADATA=$(curl "${'$'}{CURL_FLAGS[@]}" "${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}"); then
                    KTOR_COMPILER_PLUGIN_VERSION=$(echo "${'$'}KTOR_PLUGIN_METADATA" \
                        | grep -oE "<version>[^<]+</version>" \
                        | sed -E 's#</?version>##g' \
                        | grep -E -- "-eap-" \
                        | grep -vE -- "-rc|-beta|-alpha" \
                        | grep -vE -- "(-SNAPSHOT|SNAPSHOT)" \
                        | grep -vE -- "(^|[-.])openapi($|[-.])" \
                        | sort -V \
                        | tail -1 || true)

                    if [ -n "${'$'}KTOR_COMPILER_PLUGIN_VERSION" ]; then
                        echo "✅ Latest Ktor Compiler Plugin EAP version: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
                        echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}KTOR_COMPILER_PLUGIN_VERSION']"
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: ${'$'}KTOR_COMPILER_PLUGIN_VERSION (SUCCESS)\n"
                    else
                        echo "❌ Failed to parse Ktor Compiler Plugin EAP version from metadata (no -eap- versions found)"
                        FETCH_ERRORS=$((FETCH_ERRORS + 1))
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: PARSE_ERROR\n"
                    fi
                else
                    echo "❌ Failed to fetch Ktor Compiler Plugin EAP version from ${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}"
                    FETCH_ERRORS=$((FETCH_ERRORS + 1))
                    VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: FETCH_ERROR\n"
                fi

                # Fetch Kotlin version (try EAP first, fallback to stable)
                echo "Fetching Kotlin version..."
                KOTLIN_VERSION=""
                if KOTLIN_VERSION=$(curl "${'$'}{CURL_FLAGS[@]}" "${EapConstants.KOTLIN_EAP_METADATA_URL}" \
                    | grep -oE "<version>2\.[0-9]+\.[0-9]+(-[A-Za-z0-9.\-]+)?</version>" \
                    | sed -E 's#</?version>##g' \
                    | head -1 2>/dev/null); then
                    if [ -n "${'$'}KOTLIN_VERSION" ]; then
                        echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

                        if [[ "${'$'}KOTLIN_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-[0-9]+$ ]]; then
                            ORIGINAL_VERSION="${'$'}KOTLIN_VERSION"
                            KOTLIN_VERSION=$(echo "${'$'}KOTLIN_VERSION" | sed 's/-[0-9]*$//')
                            echo "⚠️  Invalid Kotlin version format: ${'$'}ORIGINAL_VERSION (looks like build number)"
                            echo "🔧 Using corrected Kotlin version: ${'$'}KOTLIN_VERSION"
                            VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (EAP_SUCCESS_CORRECTED)\n"
                        else
                            echo "✅ Latest Kotlin version: ${'$'}KOTLIN_VERSION (from EAP repository)"
                            VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (EAP_SUCCESS)\n"
                        fi
                    else
                        KOTLIN_VERSION="2.1.21"
                        echo "⚠️ Using fallback Kotlin version: ${'$'}KOTLIN_VERSION"
                        VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (FALLBACK)\n"
                    fi
                else
                    KOTLIN_VERSION="2.1.21"
                    echo "⚠️ Failed to fetch Kotlin EAP version, using stable fallback: ${'$'}KOTLIN_VERSION"
                    VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (FALLBACK)\n"
                fi
            
                echo "##teamcity[setParameter name='env.KOTLIN_VERSION' value='${'$'}KOTLIN_VERSION']"

                # Save version resolution report
                echo -e "${'$'}VERSION_REPORT" > version-resolution-reports/versions.txt
                echo "##teamcity[setParameter name='version.resolution.errors' value='${'$'}FETCH_ERRORS']"

                echo "=== Version Resolution Completed ==="
            """.trimIndent()
        }
    }
}
