package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

/**
 * Step 1: Version Resolution (resolve-only) and the per-OS PR publish.
 *
 * [applyResolveOnly] runs once in the shared `EAPResolveVersions` build: it decides WHAT to
 * validate (latest published EAP versions, or the `0.0.0-<source>-local` version string for a
 * PR/branch source build) and which source sets a PR touched, then writes `eap-version.properties`
 * for the per-OS validators to consume. It does NOT build Ktor.
 *
 * [applyPublish] runs in each per-OS validator: it reads `eap-version.properties`, re-publishes the
 * resolved versions as build parameters, and — in source mode — builds and publishes the PR's Ktor
 * from the checked-out sources into a host-local `file://` repo. Each host publishes the klibs it
 * can build (Ktor enables native targets by host), so no targets are disabled.
 */
object VersionResolutionStep {
    fun applyResolveOnly(steps: BuildSteps) {
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
                CHANGED_SETS=""

                # Decide WHAT to validate:
                [ -z "${'$'}EAP_VALIDATION_MODE" ] && EAP_VALIDATION_MODE="source"

                PR_NUMBER=$(echo "%teamcity.pullRequest.number%" | grep -E '^[0-9]+${'$'}' || echo "")
                if [ -z "${'$'}PR_NUMBER" ]; then
                    PR_NUMBER=$(echo "%teamcity.build.branch%" | sed -nE 's#.*pull/([0-9]+).*#\1#p' | head -1)
                fi

                if [ "${'$'}EAP_VALIDATION_MODE" != "published" ]; then
                    echo "🔨 Source-validation mode — resolving the version string for the checked-out sources"

                    if [ ! -d ktor ]; then
                        echo "❌ Ktor sources not found in ./ktor checkout — cannot validate from source"
                        exit 1
                    fi

                    # Identify the source being validated: PR number → branch name → short commit SHA.
                    BUILD_BRANCH=$(echo "%teamcity.build.branch%" | grep -v '%teamcity\.build\.branch%' || echo "")
                    KTOR_SHA=$(cd ktor && git rev-parse --short HEAD 2>/dev/null || echo "")
                    if [ -n "${'$'}PR_NUMBER" ]; then
                        SOURCE_ID="pr${'$'}PR_NUMBER"
                        SOURCE_DESC="pull request #${'$'}PR_NUMBER"
                    else
                        SAFE_BRANCH=$(printf '%s' "${'$'}{BUILD_BRANCH:-source}" | sed -E 's#[^A-Za-z0-9]+#-#g; s#^-+##; s#-+${'$'}##' | cut -c1-40)
                        [ -z "${'$'}SAFE_BRANCH" ] && SAFE_BRANCH="source"
                        SOURCE_ID="${'$'}SAFE_BRANCH${'$'}{KTOR_SHA:+-${'$'}KTOR_SHA}"
                        SOURCE_DESC="branch ${'$'}{BUILD_BRANCH:-<unknown>}"
                    fi
                    # Unique, deterministic version so samples can only resolve it from the local source repo.
                    KTOR_VERSION="0.0.0-${'$'}{SOURCE_ID}-local"
                    echo "Validating ${'$'}SOURCE_DESC → Ktor ${'$'}KTOR_VERSION"

                    # Determine the base to diff against for KMP target scoping.
                    CHANGED_FILES=""
                    if [ -n "${'$'}PR_NUMBER" ]; then
                        BASE_BRANCH=$(echo "%teamcity.pullRequest.targetBranch%" | sed -E 's#^refs/heads/##' | grep -v '%teamcity\.pullRequest\.targetBranch%' || echo "")
                        NPARENTS=$(cd ktor && git rev-list --parents -n1 HEAD 2>/dev/null | wc -w)
                        if [ "${'$'}{NPARENTS:-0}" -ge 3 ]; then
                            echo "PR checkout is a merge commit — diffing the PR against its base parent (HEAD^1)"
                            [ -z "${'$'}BASE_BRANCH" ] && BASE_BRANCH="(merge base parent)"
                            CHANGED_FILES=$(cd ktor && git diff --name-only 'HEAD^1' HEAD 2>/dev/null || true)
                        else
                            [ -z "${'$'}BASE_BRANCH" ] && BASE_BRANCH="main"
                            echo "Determining changed source sets against base branch: ${'$'}BASE_BRANCH"
                            (cd ktor && git fetch --no-tags --depth 500 origin "${'$'}BASE_BRANCH") 2>&1 \
                                | sed 's/^/  [git fetch] /' | head -20 || echo "  ⚠️  git fetch origin ${'$'}BASE_BRANCH failed"
                            BASE_SHA=$(cd ktor && git merge-base HEAD FETCH_HEAD 2>/dev/null || true)
                            if [ -z "${'$'}BASE_SHA" ]; then
                                echo "  merge-base not found (shallow history?) — deepening history and retrying"
                                (cd ktor && git fetch --no-tags --unshallow origin "${'$'}BASE_BRANCH" 2>/dev/null \
                                    || git fetch --no-tags --deepen=2000 origin "${'$'}BASE_BRANCH" 2>/dev/null) || true
                                BASE_SHA=$(cd ktor && git merge-base HEAD FETCH_HEAD 2>/dev/null || true)
                            fi
                            if [ -n "${'$'}BASE_SHA" ]; then
                                echo "  Base SHA: ${'$'}BASE_SHA"
                                CHANGED_FILES=$(cd ktor && git diff --name-only "${'$'}BASE_SHA"...HEAD 2>/dev/null || true)
                            else
                                echo "  Still no merge-base — falling back to two-dot diff against FETCH_HEAD"
                                CHANGED_FILES=$(cd ktor && git diff --name-only FETCH_HEAD HEAD 2>/dev/null || true)
                            fi
                        fi
                        if [ -n "${'$'}CHANGED_FILES" ]; then
                            echo "  Changed files: $(printf '%s\n' "${'$'}CHANGED_FILES" | grep -c . || true) (base: ${'$'}BASE_BRANCH)"
                            CHANGED_SETS=$(printf '%s\n' "${'$'}CHANGED_FILES" \
                                | grep -oE '/(common|jvm|posix|nix|linux|windows|mingw|darwin|macos|ios|tvos|watchos|androidNative|android|js|wasmJs|wasmWasi|web|nonJvm)/' \
                                | sed 's#/##g' | sort -u | tr '\n' ' ' || true)
                        else
                            echo "  ⚠️  Could not determine changed files — scoping skipped (all targets)"
                        fi
                        echo "PR touches source sets: ${'$'}{CHANGED_SETS:-<undetermined — will validate all targets>}"
                    else
                        echo "Branch build (${'$'}{BUILD_BRANCH:-unknown}) — validating all targets (no diff scoping)"
                    fi

                    echo "PR-affected source sets (used for sample scoping only): ${'$'}{CHANGED_SETS:-<all>}"

                    echo "✅ Resolved source version: ${'$'}KTOR_VERSION (${'$'}SOURCE_DESC; sets: ${'$'}{CHANGED_SETS:-all})"
                    echo "   Each per-OS validator will build & publish Ktor from these sources for its own targets."
                    VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Framework: ${'$'}KTOR_VERSION (SOURCE_BUILD: ${'$'}SOURCE_DESC; sets: ${'$'}{CHANGED_SETS:-all})\n"

                    echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}KTOR_VERSION']"
                    echo "##teamcity[setParameter name='env.KTOR_PR_TARGETS' value='${'$'}CHANGED_SETS']"

                    echo "Fetching latest Ktor Gradle plugin EAP version (build tooling; not built from the PR)..."
                    if KTOR_PLUGIN_METADATA=$(curl "${'$'}{CURL_FLAGS[@]}" "${EapConstants.KTOR_COMPILER_PLUGIN_METADATA_URL}"); then
                        KTOR_COMPILER_PLUGIN_VERSION=$(echo "${'$'}KTOR_PLUGIN_METADATA" \
                            | grep -oE "<version>[^<]+</version>" \
                            | sed -E 's#</?version>##g' \
                            | grep -E '^[0-9]+\.[0-9]+\.[0-9]+-eap-[0-9]+${'$'}' \
                            | grep -vE -- "(-SNAPSHOT|SNAPSHOT)" \
                            | grep -vE -- "(^|[-.])openapi(${'$'}|[-.])" \
                            | sort -V \
                            | tail -1 || true)
                    fi
                    if [ -n "${'$'}KTOR_COMPILER_PLUGIN_VERSION" ]; then
                        echo "✅ Using Ktor Gradle plugin EAP version: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
                        echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}KTOR_COMPILER_PLUGIN_VERSION']"
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: ${'$'}KTOR_COMPILER_PLUGIN_VERSION (EAP; build tooling, not from PR)\n"
                    else
                        echo "⚠️  Could not resolve a Ktor Gradle plugin EAP version — samples will keep their declared plugin version."
                        VERSION_REPORT="${'$'}VERSION_REPORT- Ktor Compiler Plugin: UNRESOLVED (samples keep declared version)\n"
                    fi

                    # Prefer the Kotlin version the PR sources build with, for metadata compatibility.
                    if [ -f ktor/gradle/libs.versions.toml ]; then
                        KOTLIN_VERSION=$(grep -iE '^[[:space:]]*kotlin[[:space:]]*=' ktor/gradle/libs.versions.toml \
                            | head -1 | sed -E 's/.*"([^"]+)".*/\1/' || true)
                        if [ -n "${'$'}KOTLIN_VERSION" ]; then
                            echo "✅ Using Kotlin version from PR sources: ${'$'}KOTLIN_VERSION"
                            VERSION_REPORT="${'$'}VERSION_REPORT- Kotlin: ${'$'}KOTLIN_VERSION (FROM_PR)\n"
                        fi
                    fi
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

                # Hand off the resolved versions to the per-OS validator builds.
                cat > eap-version.properties <<EOF
KTOR_VERSION="${'$'}KTOR_VERSION"
KOTLIN_VERSION="${'$'}KOTLIN_VERSION"
KTOR_COMPILER_PLUGIN_VERSION="${'$'}KTOR_COMPILER_PLUGIN_VERSION"
KTOR_PR_TARGETS="${'$'}CHANGED_SETS"
EAP_VALIDATION_MODE="${'$'}EAP_VALIDATION_MODE"
VERSION_RESOLUTION_ERRORS="${'$'}FETCH_ERRORS"
EOF
                echo "Wrote eap-version.properties:"
                cat eap-version.properties

                echo "=== Version Resolution Completed ==="
            """.trimIndent()
        }
    }

    fun applyPublish(steps: BuildSteps) {
        steps.script {
            name = "Step 1b: Publish PR Ktor for this OS"
            scriptContent = """
                #!/bin/bash
                set -e

                echo "=== Step 1b: Publish PR Ktor for this OS ==="

                if [ ! -f eap-version.properties ]; then
                    echo "❌ eap-version.properties not found — the resolve build artifact was not downloaded"
                    exit 1
                fi
                # shellcheck disable=SC1091
                source eap-version.properties
                [ -z "${'$'}{EAP_VALIDATION_MODE:-}" ] && EAP_VALIDATION_MODE="source"

                echo "Resolved versions from EAPResolveVersions:"
                echo "  Ktor:            ${'$'}{KTOR_VERSION:-<none>}"
                echo "  Kotlin:          ${'$'}{KOTLIN_VERSION:-<none>}"
                echo "  Compiler plugin: ${'$'}{KTOR_COMPILER_PLUGIN_VERSION:-<none>}"
                echo "  PR targets:      ${'$'}{KTOR_PR_TARGETS:-<all>}"
                echo "  Mode:            ${'$'}EAP_VALIDATION_MODE"

                # Re-publish versions as env.* build params so the external/internal steps can read them.
                emit() { [ -n "${'$'}2" ] && echo "##teamcity[setParameter name='${'$'}1' value='${'$'}2']" || true; }
                emit env.KTOR_VERSION "${'$'}{KTOR_VERSION:-}"
                emit env.KOTLIN_VERSION "${'$'}{KOTLIN_VERSION:-2.3.10}"
                emit env.KTOR_COMPILER_PLUGIN_VERSION "${'$'}{KTOR_COMPILER_PLUGIN_VERSION:-}"
                emit env.KTOR_PR_TARGETS "${'$'}{KTOR_PR_TARGETS:-}"
                emit env.EAP_VALIDATION_MODE "${'$'}EAP_VALIDATION_MODE"
                emit version.resolution.errors "${'$'}{VERSION_RESOLUTION_ERRORS:-0}"

                if [ "${'$'}EAP_VALIDATION_MODE" = "published" ]; then
                    echo "Published-EAP mode — nothing to build; samples resolve from the published EAP repo."
                    exit 0
                fi

                if [ ! -d ktor ]; then
                    echo "❌ Ktor sources not found in ./ktor checkout — cannot validate from source"
                    exit 1
                fi

                # Publish to a host-local FILE repository. The external/internal steps serve it over HTTPS.
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

                # Route Ktor's own dependency/plugin resolution through the PUBLIC cache-redirector.
                printf '%s\n' \
                    'def swap = { u ->' \
                    '    if (u == null) return null' \
                    '    def n = u.replace("https://artifacts-caching-proxy.aws.intellij.net/", "https://cache-redirector.jetbrains.com/")' \
                    '             .replace("http://artifacts-caching-proxy.aws.intellij.net/", "https://cache-redirector.jetbrains.com/")' \
                    '    n = n.replace("cache-redirector.jetbrains.com/repo.maven.apache.org/", "cache-redirector.jetbrains.com/repo1.maven.org/")' \
                    '    return n' \
                    '}' \
                    'def replace = { repo ->' \
                    '    if (repo instanceof org.gradle.api.artifacts.repositories.MavenArtifactRepository) {' \
                    '        def u = repo.url?.toString()' \
                    '        def n = swap(u)' \
                    '        if (n != null && n != u) repo.url = n' \
                    '    }' \
                    '}' \
                    'beforeSettings { settings -> settings.pluginManagement.repositories.all(replace) }' \
                    'settingsEvaluated { settings ->' \
                    '    settings.pluginManagement.repositories.all(replace)' \
                    '    if (settings.dependencyResolutionManagement) settings.dependencyResolutionManagement.repositories.all(replace)' \
                    '}' \
                    'allprojects {' \
                    '    buildscript.repositories.all(replace)' \
                    '    repositories.all(replace)' \
                    '}' \
                    > ktor/pr-cache-redirector.init.gradle

                echo "Publishing Ktor ${'$'}KTOR_VERSION from the checked-out sources to ${'$'}KTOR_PR_REPO_URL (this can take a while)..."
                chmod +x ktor/gradlew || true
                if ! (cd ktor && KTOR_PR_REPO_OUT="${'$'}KTOR_PR_REPO_URL" \
                        ./gradlew publishAllPublicationsToPrLocalFileRepository \
                          -Pversion="${'$'}KTOR_VERSION" \
                          --init-script pr-publish-repo.init.gradle \
                          --init-script pr-cache-redirector.init.gradle --no-daemon --stacktrace); then
                    echo "❌ Failed to build/publish Ktor from the checked-out sources."
                    echo "   A source check must validate the actual sources — refusing to fall back to a published EAP."
                    exit 1
                fi

                # Verify the artifacts actually landed in the file repo.
                if [ ! -d "${'$'}KTOR_PR_REPO_DIR/io/ktor/ktor-bom/${'$'}KTOR_VERSION" ]; then
                    echo "❌ Ktor ${'$'}KTOR_VERSION not found in ${'$'}KTOR_PR_REPO_DIR after publish — cannot validate"
                    exit 1
                fi
                echo "✅ Ktor source build available in local file repo: ${'$'}KTOR_PR_REPO_DIR (${'$'}KTOR_VERSION)"

                echo "##teamcity[setParameter name='env.KTOR_PR_REPO' value='${'$'}KTOR_PR_REPO_HTTP_URL']"
                echo "##teamcity[setParameter name='env.KTOR_PR_REPO_DIR' value='${'$'}KTOR_PR_REPO_DIR']"

                echo "=== Step 1b Completed ==="
            """.trimIndent()
        }
    }
}
