package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

/**
 * Step 1: Version Resolution
 *
 * For scheduled / default-branch builds fetches the latest published EAP versions
 * for the Ktor framework, compiler plugin, and Kotlin.
 *
 * For pull request builds it from the merge request sources (the `./ktor` checkout)
 * and publishes it to Maven Local.
 *
 * Continues even if some versions fail to fetch.
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

                KTOR_VERSION=""
                KTOR_COMPILER_PLUGIN_VERSION=""
                KOTLIN_VERSION=""

                # Detect pull request context. When set, validate the samples against the Ktor version built FROM the merge request sources.
                PR_NUMBER=$(echo "%teamcity.pullRequest.number%" | grep -E '^[0-9]+${'$'}' || echo "")

                if [ -n "${'$'}PR_NUMBER" ]; then
                    echo "🔀 Pull request #${'$'}PR_NUMBER detected — building Ktor from merge request sources"

                    if [ ! -d ktor ]; then
                        echo "❌ Ktor sources not found in ./ktor checkout — cannot validate the PR"
                        exit 1
                    fi

                    # Unique, deterministic version so samples can only resolve it from Maven Local.
                    KTOR_VERSION="0.0.0-pr${'$'}{PR_NUMBER}-local"

                    # Scope the build to the KMP targets the PR actually touches
                    BASE_BRANCH=$(echo "%teamcity.pullRequest.targetBranch%" | sed -E 's#^refs/heads/##')
                    [ -z "${'$'}BASE_BRANCH" ] && BASE_BRANCH="main"
                    CHANGED_SETS=""
                    if (cd ktor && git fetch --no-tags --depth 200 origin "${'$'}BASE_BRANCH" >/dev/null 2>&1); then
                        BASE_SHA=$(cd ktor && git merge-base HEAD FETCH_HEAD 2>/dev/null || true)
                        if [ -n "${'$'}BASE_SHA" ]; then
                            CHANGED_SETS=$( (cd ktor && git diff --name-only "${'$'}BASE_SHA"...HEAD 2>/dev/null) \
                                | grep -oE '/(common|jvm|posix|nix|linux|windows|mingw|darwin|macos|ios|tvos|watchos|androidNative|android|js|wasmJs|wasmWasi|web|nonJvm)/' \
                                | sed 's#/##g' | sort -u | tr '\n' ' ' || true)
                        fi
                    fi
                    echo "PR touches source sets: ${'$'}{CHANGED_SETS:-<undetermined — will publish all targets>}"

                    # Map touched source sets → target groups to DISABLE (jvm always stays on).
                    SCOPE_APPLIED=0
                    {
                        echo ""
                        echo "# --- ConsolidatedEAPValidation PR scope (auto-generated) ---"
                        if [ -n "${'$'}CHANGED_SETS" ]; then
                            echo "${'$'}CHANGED_SETS" | grep -qwE 'web|js|wasmJs|nonJvm|common'                                       || { echo "target.web=false";      SCOPE_APPLIED=1; }
                            echo "${'$'}CHANGED_SETS" | grep -qwE 'posix|nix|linux|windows|mingw|darwin|macos|ios|tvos|watchos|androidNative|nonJvm' || { echo "target.posix=false";    SCOPE_APPLIED=1; }
                            echo "${'$'}CHANGED_SETS" | grep -qwE 'wasmWasi|nonJvm'                                                   || { echo "target.wasmWasi=false"; SCOPE_APPLIED=1; }
                            echo "${'$'}CHANGED_SETS" | grep -qwE 'android'                                                           || { echo "target.android=false";  SCOPE_APPLIED=1; }
                        fi
                        [ "${'$'}SCOPE_APPLIED" = "1" ] && echo "ktorbuild.ignoreExtraSourceSets=true"
                    } >> ktor/gradle.properties
                    echo "Applied target scope to ktor/gradle.properties:"
                    grep -E '^(target\.|ktorbuild\.ignoreExtraSourceSets)' ktor/gradle.properties || echo "  (all targets enabled)"

                    # Publish to a local FILE repository
                    KTOR_PR_REPO_DIR="$(pwd)/ktor-pr-repo"
                    rm -rf "${'$'}KTOR_PR_REPO_DIR"; mkdir -p "${'$'}KTOR_PR_REPO_DIR"
                    KTOR_PR_REPO_URL="file://${'$'}KTOR_PR_REPO_DIR"
                    KTOR_PR_REPO_HTTP_URL="https://127.0.0.1:8347"

                    printf '%s\n' \
                        'def repoUrl = System.getenv("KTOR_PR_REPO_OUT")' \
                        'if (repoUrl) {' \
                        '  gradle.rootProject { root ->' \
                        '    root.allprojects { project ->' \
                        '      project.plugins.withId("maven-publish") {' \
                        '        project.extensions.configure(org.gradle.api.publish.PublishingExtension) { publishing ->' \
                        '          publishing.repositories.maven { repo -> repo.name = "prLocalFile"; repo.url = project.uri(repoUrl) }' \
                        '        }' \
                        '      }' \
                        '    }' \
                        '  }' \
                        '}' \
                        > ktor/pr-publish-repo.init.gradle

                    echo "Publishing Ktor ${'$'}KTOR_VERSION from PR sources to ${'$'}KTOR_PR_REPO_URL (this can take a while)..."
                    chmod +x ktor/gradlew || true
                    if ! (cd ktor && KTOR_PR_REPO_OUT="${'$'}KTOR_PR_REPO_URL" \
                            ./gradlew publishAllPublicationsToPrLocalFileRepository \
                              -Pversion="${'$'}KTOR_VERSION" \
                              --init-script pr-publish-repo.init.gradle --no-daemon --stacktrace); then
                        echo "❌ Failed to build/publish Ktor from the PR sources."
                        echo "   A PR check must validate the PR's own Ktor — refusing to fall back to a published EAP."
                        exit 1
                    fi

                    # Verify the artifacts actually landed in the file repo.
                    if [ ! -d "${'$'}KTOR_PR_REPO_DIR/io/ktor/ktor-bom/${'$'}KTOR_VERSION" ]; then
                        echo "❌ Ktor ${'$'}KTOR_VERSION not found in ${'$'}KTOR_PR_REPO_DIR after publish — cannot validate the PR"
                        exit 1
                    fi
                    echo "✅ Ktor PR build available in local file repo: ${'$'}KTOR_PR_REPO_DIR (${'$'}KTOR_VERSION)"
                    VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Framework: ${'$'}KTOR_VERSION (PR_BUILD; sets: ${'$'}{CHANGED_SETS:-all})\n"

                    VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: EAP_FALLBACK (separate repo)\n"

                    # Prefer the Kotlin version the PR sources build with, for metadata compatibility.
                    if [ -f ktor/gradle/libs.versions.toml ]; then
                        KOTLIN_VERSION=$(grep -iE '^[[:space:]]*kotlin[[:space:]]*=' ktor/gradle/libs.versions.toml \
                            | head -1 | sed -E 's/.*"([^"]+)".*/\1/' || true)
                        if [ -n "${'$'}KOTLIN_VERSION" ]; then
                            echo "✅ Using Kotlin version from PR sources: ${'$'}KOTLIN_VERSION"
                            VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (FROM_PR)\n"
                        fi
                    fi

                    echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}KTOR_VERSION']"
                    echo "##teamcity[setParameter name='env.KTOR_PR_TARGETS' value='${'$'}CHANGED_SETS']"
                    echo "##teamcity[setParameter name='env.KTOR_PR_REPO' value='${'$'}KTOR_PR_REPO_HTTP_URL']"
                    echo "##teamcity[setParameter name='env.KTOR_PR_REPO_DIR' value='${'$'}KTOR_PR_REPO_DIR']"
                else
                    echo "Fetching latest EAP versions for Ktor framework, compiler plugin, and Kotlin"

                    # Fetch Ktor Framework EAP version
                    echo "Fetching Ktor Framework EAP version..."
                    KTOR_METADATA=""
                    if KTOR_METADATA=$(curl "${'$'}{CURL_FLAGS[@]}" "${EapConstants.KTOR_EAP_METADATA_URL}"); then
                        KTOR_VERSION=$(echo "${'$'}KTOR_METADATA" \
                            | grep -oE "<version>[^<]+</version>" \
                            | sed -E 's#</?version>##g' \
                            | grep -E '^[0-9]+\.[0-9]+\.[0-9]+-eap-[0-9]+${'$'}' \
                            | sort -V \
                            | tail -1 || true)

                        if [ -n "${'$'}KTOR_VERSION" ]; then
                            echo "✅ Latest Ktor EAP version: ${'$'}KTOR_VERSION"
                            echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}KTOR_VERSION']"
                            VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Framework: ${'$'}KTOR_VERSION (SUCCESS)\n"
                        else
                            echo "❌ Failed to parse Ktor EAP version from metadata (no valid semantic EAP versions found)"
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
                    KTOR_PLUGIN_METADATA=""
                    if KTOR_PLUGIN_METADATA=$(curl "${'$'}{CURL_FLAGS[@]}" "${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}"); then
                        KTOR_COMPILER_PLUGIN_VERSION=$(echo "${'$'}KTOR_PLUGIN_METADATA" \
                            | grep -oE "<version>[^<]+</version>" \
                            | sed -E 's#</?version>##g' \
                            | grep -E '^[0-9]+\.[0-9]+\.[0-9]+-eap-[0-9]+${'$'}' \
                            | grep -vE -- "(-SNAPSHOT|SNAPSHOT)" \
                            | grep -vE -- "(^|[-.])openapi(${'$'}|[-.])" \
                            | sort -V \
                            | tail -1 || true)

                        if [ -n "${'$'}KTOR_COMPILER_PLUGIN_VERSION" ]; then
                            echo "✅ Latest Ktor Compiler Plugin EAP version: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
                            echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}KTOR_COMPILER_PLUGIN_VERSION']"
                            VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: ${'$'}KTOR_COMPILER_PLUGIN_VERSION (SUCCESS)\n"
                        else
                            echo "❌ Failed to parse Ktor Compiler Plugin EAP version from metadata (no valid semantic EAP versions found)"
                            FETCH_ERRORS=$((FETCH_ERRORS + 1))
                            VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: PARSE_ERROR\n"
                        fi
                    else
                        echo "❌ Failed to fetch Ktor Compiler Plugin EAP version from ${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}"
                        FETCH_ERRORS=$((FETCH_ERRORS + 1))
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: FETCH_ERROR\n"
                    fi
                fi

                # Fetch Kotlin version (try EAP first, fallback to stable)
                if [ -z "${'$'}KOTLIN_VERSION" ]; then
                    echo "Fetching Kotlin version..."
                    if KOTLIN_VERSION=$(curl "${'$'}{CURL_FLAGS[@]}" "${EapConstants.KOTLIN_EAP_METADATA_URL}" \
                        | grep -oE "<version>2\.[0-9]+\.[0-9]+(-[A-Za-z0-9.\-]+)?</version>" \
                        | sed -E 's#</?version>##g' \
                        | head -1 2>/dev/null); then
                        if [ -n "${'$'}KOTLIN_VERSION" ]; then
                            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

                            if [[ "${'$'}KOTLIN_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-[0-9]+${'$'} ]]; then
                                ORIGINAL_VERSION="${'$'}KOTLIN_VERSION"
                                KOTLIN_VERSION=$(echo "${'$'}KOTLIN_VERSION" | sed 's/-[0-9]*${'$'}//')
                                echo "⚠️  Invalid Kotlin version format: ${'$'}ORIGINAL_VERSION (looks like build number)"
                                echo "🔧 Using corrected Kotlin version: ${'$'}KOTLIN_VERSION"
                                VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (EAP_SUCCESS_CORRECTED)\n"
                            else
                                echo "✅ Latest Kotlin version: ${'$'}KOTLIN_VERSION (from EAP repository)"
                                VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (EAP_SUCCESS)\n"
                            fi
                        else
                            KOTLIN_VERSION="2.3.10"
                            echo "⚠️ Using fallback Kotlin version: ${'$'}KOTLIN_VERSION"
                            VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (FALLBACK)\n"
                        fi
                    else
                        KOTLIN_VERSION="2.3.10"
                        echo "⚠️ Failed to fetch Kotlin EAP version, using stable fallback: ${'$'}KOTLIN_VERSION"
                        VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (FALLBACK)\n"
                    fi
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
