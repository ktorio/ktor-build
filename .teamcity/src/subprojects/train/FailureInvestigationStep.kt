package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

/**
 * Step 4.5: Failure Investigation & YouTrack Reporting
 *
 * Runs after the quality gate evaluation and only acts when the gate FAILED.
 * It researches why the validation failed by aggregating the quality-gate
 * failure reasons together with the per-sample build logs, then files a
 * YouTrack issue in the KTOR project.
 */
object FailureInvestigationStep {
    fun apply(steps: BuildSteps) {
        steps.script {
            name = "Step 4.5: Failure Investigation & YouTrack Reporting"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/bin/bash

                echo "=== Step 4.5: Failure Investigation & YouTrack Reporting ==="
                echo "Timestamp: $(date -Iseconds)"

                OVERALL_STATUS=$(echo "%quality.gate.overall.status%" | grep -v "^%quality\.gate\.overall\.status%$" || echo "UNKNOWN")
                echo "Quality gate status: ${'$'}OVERALL_STATUS"
                if [ "${'$'}OVERALL_STATUS" != "FAILED" ]; then
                    echo "Quality gate did not fail — nothing to investigate."
                    exit 0
                fi

                # Never file YouTrack issues for pull request runs
                PR_NUMBER=$(echo "%teamcity.pullRequest.number%" | grep -E '^[0-9]+${'$'}' || echo "")
                if [ -z "${'$'}PR_NUMBER" ]; then
                    PR_NUMBER=$(echo "%teamcity.build.branch%" | sed -nE 's#.*pull/([0-9]+).*#\1#p' | head -1)
                fi
                if [ -n "${'$'}PR_NUMBER" ]; then
                    echo "Pull request #${'$'}PR_NUMBER run — skipping YouTrack issue filing (PR failures are non-blocking and not tracked as EAP regressions)."
                    exit 0
                fi

                KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | grep -v "^%env\.KTOR_VERSION%$" || echo "")
                [ -z "${'$'}KTOR_VERSION" ] && KTOR_VERSION="unknown"
                KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | grep -E '^[0-9.]+' || echo "unknown")
                KTOR_COMPILER_PLUGIN_VERSION=$(echo "%env.KTOR_COMPILER_PLUGIN_VERSION%" | grep -v "^%env\.KTOR_COMPILER_PLUGIN_VERSION%$" || echo "N/A")
                [ -z "${'$'}KTOR_COMPILER_PLUGIN_VERSION" ] && KTOR_COMPILER_PLUGIN_VERSION="N/A"

                OVERALL_SCORE=$(echo "%quality.gate.overall.score%" | grep -E '^[0-9]+$' || echo "0")
                TOTAL_CRITICAL=$(echo "%quality.gate.total.critical%" | grep -E '^[0-9]+$' || echo "0")
                EXTERNAL_TOTAL=$(echo "%external.validation.total.samples%" | grep -E '^[0-9]+$' || echo "0")
                EXTERNAL_SUCCESS=$(echo "%external.validation.successful.samples%" | grep -E '^[0-9]+$' || echo "0")
                EXTERNAL_FAILED=$(echo "%external.validation.failed.samples%" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_TOTAL=$(echo "%internal.validation.total.tests%" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_PASSED=$(echo "%internal.validation.passed.tests%" | grep -E '^[0-9]+$' || echo "0")
                INTERNAL_FAILED=$(echo "%internal.validation.failed.tests%" | grep -E '^[0-9]+$' || echo "0")

                SERVER_URL=$(echo "%teamcity.serverUrl%" | grep -v "^%teamcity\.serverUrl%$" || echo "")
                BUILD_ID=$(echo "%teamcity.build.id%" | grep -E '^[0-9]+$' || echo "")
                BUILD_LINK="(local build)"
                if [ -n "${'$'}SERVER_URL" ] && [ -n "${'$'}BUILD_ID" ]; then
                    BUILD_LINK="${'$'}SERVER_URL/viewLog.html?buildId=${'$'}BUILD_ID"
                fi

                extract_errors() {
                    log="$1"
                    if [ ! -f "${'$'}log" ]; then
                        echo "(log not found: ${'$'}log)"
                        return
                    fi
                    echo '```'
                    grep -niE 'error:|FAILED|FAILURE|Exception|Caused by|Could not|Unresolved reference|cannot access|cannot find symbol|> Task' "${'$'}log" 2>/dev/null | tail -n 15
                    echo '--- last lines of log ---'
                    tail -n 20 "${'$'}log" 2>/dev/null
                    echo '```'
                }

                is_ktor_related() {
                    log="$1"
                    [ -f "${'$'}log" ] || return 1
                    grep -qiE '(error|exception|caused by|could not|cannot|unresolved|failed|no such|noclassdef|nosuchmethod|classnotfound).*(io\.ktor[.:]|ktor-bom)|(io\.ktor[.:]|ktor-bom).*(not found|could not|unresolved|no such|does not exist|failed)' "${'$'}log" || return 1
                    if grep -qiE '429|too many requests|connection (reset|refused|timed out)|read timed out|sdk location not found|sdkmanager|missing on disk' "${'$'}log" \
                       && ! grep -qiE 'unresolved reference.*io\.ktor|cannot access.*io\.ktor|could not (resolve|find).*io\.ktor|(noclassdef|nosuchmethod|classnotfound).*io[./]ktor' "${'$'}log"; then
                        return 1
                    fi
                    return 0
                }

                mkdir -p quality-gate-reports
                DESC_FILE=quality-gate-reports/youtrack-description.md
                : > "${'$'}DESC_FILE"

                {
                    echo "**Build URL:** %teamcity.serverUrl%/viewLog.html?buildId=%teamcity.build.id%&tab=buildLog"
                    echo ""
                } >> "${'$'}DESC_FILE"

                KTOR_SECTION=$(mktemp)
                SIG_SRC=$(mktemp)
                KTOR_ZIPS=$(mktemp)
                ROOT_CAUSES=$(mktemp)
                KTOR_COUNT=0
                PRIMARY_RC=""
                SAMPLE_ONLY=""

                sig_extract() {
                    grep -hoiE 'unresolved reference:? *[a-z0-9_.]+|(could not (resolve|find)) [^ ]*io\.ktor[a-z0-9_.:/-]*|(noclassdeffounderror|nosuchmethoderror|classnotfoundexception|nosuchfielderror)[: ]*[a-z0-9_./]*io[./]ktor[a-z0-9_./]*|cannot access[^ ]* *[a-z0-9_.]*io\.ktor[a-z0-9_.]*|io\.ktor[.:][a-z0-9_.:/-]+' "$1" 2>/dev/null \
                        | sed -E 's/[0-9]+/N/g' | tr 'A-Z' 'a-z' | sort -u
                }

                root_cause() {
                    grep -hoiE 'could not (find|resolve)[^.]*io\.ktor[a-z0-9_.:/-]*|unresolved reference:? *[a-z0-9_.]+|cannot access[^,]*io\.ktor[a-z0-9_.]*|(noclassdeffounderror|nosuchmethoderror|classnotfoundexception)[: ]*[a-z0-9_./]*io[./]ktor[a-z0-9_./]*' "$1" 2>/dev/null \
                        | head -1 | sed -E 's/^[[:space:]>*-]+//; s/[[:space:]]+/ /g' | cut -c1-140
                }

                classify_failure() {
                    name="$1"; log="$2"; zip="$3"
                    if is_ktor_related "${'$'}log"; then
                        KTOR_COUNT=$((KTOR_COUNT + 1))
                        sig_extract "${'$'}log" >> "${'$'}SIG_SRC"
                        [ -n "${'$'}zip" ] && [ -f "${'$'}zip" ] && echo "${'$'}zip" >> "${'$'}KTOR_ZIPS"
                        rc=$(root_cause "${'$'}log")
                        [ -z "${'$'}rc" ] && rc="build failure — see attached log"
                        echo "- **${'$'}name**: ${'$'}rc" >> "${'$'}ROOT_CAUSES"
                        [ -z "${'$'}PRIMARY_RC" ] && PRIMARY_RC="${'$'}rc"
                        if [ "${'$'}KTOR_COUNT" -le 8 ]; then
                            {
                                echo ""
                                extract_errors "${'$'}log"
                            } >> "${'$'}KTOR_SECTION"
                        fi
                    else
                        SAMPLE_ONLY="${'$'}SAMPLE_ONLY ${'$'}name"
                    fi
                }

                if ls external-validation-reports/*.failed >/dev/null 2>&1; then
                    for marker in external-validation-reports/*.failed; do
                        name=$(basename "${'$'}marker" .failed)
                        classify_failure "${'$'}name (external)" "external-validation-reports/${'$'}name-build.log" "failed-samples/${'$'}name.zip"
                    done
                fi

                if ls internal-validation-reports/*.log >/dev/null 2>&1; then
                    for log in internal-validation-reports/*.log; do
                        [ -f "${'$'}log" ] || continue
                        if grep -qiE 'BUILD FAILED|BUILD FAILURE|FAILURE: Build failed|> Task .* FAILED' "${'$'}log"; then
                            iname=$(basename "${'$'}log" .log)
                            classify_failure "${'$'}iname (internal)" "${'$'}log" "failed-samples/${'$'}iname.zip"
                        fi
                    done
                fi

                echo "Ktor-related failures: ${'$'}KTOR_COUNT"
                echo "Sample/infra-only failures:${'$'}{SAMPLE_ONLY:- none}"

                if [ "${'$'}KTOR_COUNT" -gt 0 ]; then
                    {
                        echo "### Root cause"
                        cat "${'$'}ROOT_CAUSES"
                    } >> "${'$'}DESC_FILE"
                    cat "${'$'}KTOR_SECTION" >> "${'$'}DESC_FILE"
                    if [ "${'$'}KTOR_COUNT" -gt 8 ]; then
                        echo "" >> "${'$'}DESC_FILE"
                        echo "_…and more Ktor-related failures; see the build artifacts._" >> "${'$'}DESC_FILE"
                    fi
                fi
                if [ -n "${'$'}SAMPLE_ONLY" ]; then
                    {
                        echo ""
                        echo "> Note: these failures showed no Ktor signal and are treated as sample/infra issues (not the cause of this report):${'$'}SAMPLE_ONLY"
                    } >> "${'$'}DESC_FILE"
                fi
                rm -f "${'$'}KTOR_SECTION" "${'$'}ROOT_CAUSES"

                SIGNATURE=""
                if [ -s "${'$'}SIG_SRC" ]; then
                    SIGNATURE=$(sort -u "${'$'}SIG_SRC" | md5sum | cut -c1-16)
                fi
                rm -f "${'$'}SIG_SRC"

                {
                    echo ""
                    echo "---"
                    if [ -n "${'$'}SIGNATURE" ]; then
                        echo "Error signature: \`${'$'}SIGNATURE\`"
                        echo ""
                    fi
                    echo "_Created automatically by the Consolidated EAP Validation build. Only failures showing a Ktor regression signal are reported here._"
                } >> "${'$'}DESC_FILE"

                echo "----- Investigation summary -----"
                cat "${'$'}DESC_FILE"
                echo "---------------------------------"

                if [ "${'$'}KTOR_COUNT" -eq 0 ]; then
                    echo "No Ktor-related failures detected — all failures look sample/infra/version-resolution specific. Not filing a YouTrack issue (non-blocking)."
                    exit 0
                fi

                YOUTRACK_URL=$(echo "%env.YOUTRACK_URL%" | grep -v "^%env\.YOUTRACK_URL%$" || echo "")
                [ -z "${'$'}YOUTRACK_URL" ] && YOUTRACK_URL="https://youtrack.jetbrains.com"
                PROJECT=$(echo "%env.YOUTRACK_PROJECT%" | grep -v "^%env\.YOUTRACK_PROJECT%$" || echo "")
                [ -z "${'$'}PROJECT" ] && PROJECT="KTOR"
                TAG=$(echo "%env.YOUTRACK_TAG%" | grep -v "^%env\.YOUTRACK_TAG%$" || echo "")
                [ -z "${'$'}TAG" ] && TAG="ktor-eap-validation"
                TOKEN=$(echo "%env.YOUTRACK_TOKEN%" | grep -v "^%env\.YOUTRACK_TOKEN%$" || echo "")

                if [ -z "${'$'}TOKEN" ]; then
                    echo "⚠️  YOUTRACK_TOKEN is not configured — skipping YouTrack issue creation (non-blocking)."
                    echo "    Provide a JetBrains Hub permanent token via the secure parameter behind env.YOUTRACK_TOKEN."
                    exit 0
                fi

                RC_SHORT=$(echo "${'$'}PRIMARY_RC" | cut -c1-100)
                if [ -n "${'$'}RC_SHORT" ]; then
                    SUMMARY="Ktor EAP Validation failure: ${'$'}RC_SHORT"
                else
                    SUMMARY="Ktor EAP Validation failure"
                fi

                json_escape_file() {
                    LC_ALL=C sed -r 's/\x1b\[[0-9;]*[a-zA-Z]//g' "$1" \
                        | LC_ALL=C tr -d '\000-\010\013\014\016-\037' \
                        | awk 'BEGIN{ORS=""} NR>1{printf "\\n"} {gsub(/\\/,"\\\\"); gsub(/"/,"\\\""); gsub(/\t/,"\\t"); printf "%s",$0}'
                }
                json_escape_str() {
                    printf '%s' "$1" | awk 'BEGIN{ORS=""} {gsub(/\\/,"\\\\"); gsub(/"/,"\\\""); printf "%s",$0}'
                }

                PROJECT_ID=$(curl -sS -G "${'$'}YOUTRACK_URL/api/admin/projects" \
                    -H "Authorization: Bearer ${'$'}TOKEN" -H "Accept: application/json" \
                    --data-urlencode "fields=id,shortName" --data-urlencode "query=${'$'}PROJECT" 2>/dev/null \
                    | tr ',' '\n' | grep -B1 "\"shortName\":\"${'$'}PROJECT\"" | grep -oE '"id":"[^"]+"' | head -1 | sed 's/.*:"//; s/"$//')
                if [ -z "${'$'}PROJECT_ID" ]; then
                    PROJECT_ID=$(curl -sS -G "${'$'}YOUTRACK_URL/api/issues" \
                        -H "Authorization: Bearer ${'$'}TOKEN" -H "Accept: application/json" \
                        --data-urlencode "query=project: ${'$'}PROJECT" --data-urlencode "fields=project(id,shortName)" 2>/dev/null \
                        | grep -oE '"id":"[^"]+"' | head -1 | sed 's/.*:"//; s/"$//')
                fi
                echo "Resolved project id: ${'$'}{PROJECT_ID:-<none>}"

                find_existing() {
                    curl -sS -G "${'$'}YOUTRACK_URL/api/issues" \
                        -H "Authorization: Bearer ${'$'}TOKEN" -H "Accept: application/json" \
                        --data-urlencode "fields=idReadable" \
                        --data-urlencode "query=$1" 2>/dev/null \
                        | grep -oE '"idReadable":"[^"]+"' | head -1 | sed 's/.*:"//; s/"$//'
                }

                EXISTING_ID=""
                if [ -n "${'$'}SIGNATURE" ]; then
                    EXISTING_ID=$(find_existing "project: ${'$'}PROJECT #Unresolved ${'$'}SIGNATURE")
                    [ -n "${'$'}EXISTING_ID" ] && echo "Matched existing issue ${'$'}EXISTING_ID by error signature ${'$'}SIGNATURE."
                fi
                if [ -z "${'$'}EXISTING_ID" ]; then
                    EXISTING_ID=$(find_existing "project: ${'$'}PROJECT #Unresolved summary: {${'$'}SUMMARY}")
                    [ -n "${'$'}EXISTING_ID" ] && echo "Matched existing issue ${'$'}EXISTING_ID by exact summary."
                fi

                ISSUE_ID=""
                CREATED_NEW=0
                if [ -n "${'$'}EXISTING_ID" ]; then
                    echo "Found existing unresolved issue ${'$'}EXISTING_ID — adding a comment instead of creating a duplicate."
                    COMMENT="Another failing run: ${'$'}BUILD_LINK (score ${'$'}OVERALL_SCORE/100, ${'$'}TOTAL_CRITICAL critical issues)."
                    COMMENT_ESC=$(json_escape_str "${'$'}COMMENT")
                    curl -sS -X POST "${'$'}YOUTRACK_URL/api/issues/${'$'}EXISTING_ID/comments?fields=id" \
                        -H "Authorization: Bearer ${'$'}TOKEN" -H "Content-Type: application/json" -H "Accept: application/json" \
                        -d "{\"text\":\"${'$'}COMMENT_ESC\"}" > /tmp/yt_resp 2>/dev/null || echo "(comment request failed)"
                    ISSUE_ID="${'$'}EXISTING_ID"
                else
                    if [ -z "${'$'}PROJECT_ID" ]; then
                        echo "⚠️  Could not resolve a YouTrack project id for '${'$'}PROJECT' — skipping issue creation (non-blocking)."
                        exit 0
                    fi
                    SUMMARY_ESC=$(json_escape_str "${'$'}SUMMARY")
                    DESC_ESC=$(json_escape_file "${'$'}DESC_FILE")

                    CF_TYPE='{"name":"Type","${'$'}type":"SingleEnumIssueCustomField","value":{"name":"Bug"}}'
                    CUSTOM_FIELDS="${'$'}CF_TYPE"
                    if [ -n "${'$'}KTOR_VERSION" ] && [ "${'$'}KTOR_VERSION" != "unknown" ]; then
                        VER_ESC=$(json_escape_str "${'$'}KTOR_VERSION")
                        CF_VER=$(printf '{"name":"Affected versions","${'$'}type":"MultiVersionIssueCustomField","value":[{"name":"%s"}]}' "${'$'}VER_ESC")
                        CUSTOM_FIELDS="${'$'}CF_TYPE,${'$'}CF_VER"
                    fi

                    create_issue() {
                        if [ -n "$1" ]; then
                            body=$(printf '{"project":{"id":"%s"},"summary":"%s","description":"%s","customFields":[%s]}' "${'$'}PROJECT_ID" "${'$'}SUMMARY_ESC" "${'$'}DESC_ESC" "$1")
                        else
                            body=$(printf '{"project":{"id":"%s"},"summary":"%s","description":"%s"}' "${'$'}PROJECT_ID" "${'$'}SUMMARY_ESC" "${'$'}DESC_ESC")
                        fi
                        curl -sS -X POST "${'$'}YOUTRACK_URL/api/issues?fields=idReadable" \
                            -H "Authorization: Bearer ${'$'}TOKEN" -H "Content-Type: application/json" -H "Accept: application/json" \
                            -d "${'$'}body" > /tmp/yt_resp 2>/dev/null || true
                        grep -oE '"idReadable":"[^"]+"' /tmp/yt_resp 2>/dev/null | head -1 | sed 's/.*:"//; s/"$//'
                    }

                    add_affected_version() {
                        ver="$1"
                        bundle_id=$(curl -sS -G "${'$'}YOUTRACK_URL/api/admin/projects/${'$'}PROJECT_ID/customFields" \
                            -H "Authorization: Bearer ${'$'}TOKEN" -H "Accept: application/json" \
                            --data-urlencode "fields=field(name),bundle(id)" 2>/dev/null \
                            | tr '{' '\n' | grep -iA1 'affected versions' | grep -oE '"id":"[^"]+"' | head -1 | sed 's/.*:"//; s/"$//')
                        if [ -z "${'$'}bundle_id" ]; then
                            echo "  (could not resolve the Affected versions bundle id — skipping)"
                            return 1
                        fi
                        ver_esc=$(json_escape_str "${'$'}ver")
                        curl -sS -X POST "${'$'}YOUTRACK_URL/api/admin/customFieldSettings/bundles/version/${'$'}bundle_id/values?fields=name" \
                            -H "Authorization: Bearer ${'$'}TOKEN" -H "Content-Type: application/json" -H "Accept: application/json" \
                            -d "{\"name\":\"${'$'}ver_esc\"}" > /tmp/yt_ver 2>/dev/null || true
                        echo "  Registered '${'$'}ver' in the Affected versions bundle (or it already existed)."
                    }

                    ISSUE_ID=$(create_issue "${'$'}CUSTOM_FIELDS")
                    if [ -z "${'$'}ISSUE_ID" ] && [ "${'$'}CUSTOM_FIELDS" != "${'$'}CF_TYPE" ]; then
                        echo "⚠️  Create with affected version failed — registering '${'$'}KTOR_VERSION' in the Affected versions bundle and retrying. Response:"
                        cat /tmp/yt_resp 2>/dev/null || true
                        add_affected_version "${'$'}KTOR_VERSION"
                        ISSUE_ID=$(create_issue "${'$'}CUSTOM_FIELDS")
                    fi
                    if [ -z "${'$'}ISSUE_ID" ] && [ "${'$'}CUSTOM_FIELDS" != "${'$'}CF_TYPE" ]; then
                        echo "⚠️  Still failing with affected version — retrying as Bug only. Response:"
                        cat /tmp/yt_resp 2>/dev/null || true
                        ISSUE_ID=$(create_issue "${'$'}CF_TYPE")
                    fi
                    if [ -z "${'$'}ISSUE_ID" ]; then
                        echo "⚠️  Create with custom fields failed — retrying without custom fields. Response:"
                        cat /tmp/yt_resp 2>/dev/null || true
                        ISSUE_ID=$(create_issue "")
                    fi
                    if [ -z "${'$'}ISSUE_ID" ]; then
                        echo "⚠️  Issue creation did not return an id (non-blocking). Response:"
                        cat /tmp/yt_resp 2>/dev/null || true
                        exit 0
                    fi
                    echo "✅ Created YouTrack issue ${'$'}ISSUE_ID"
                    CREATED_NEW=1
                fi

                if [ -n "${'$'}ISSUE_ID" ]; then
                    ISSUE_URL="${'$'}YOUTRACK_URL/issue/${'$'}ISSUE_ID"
                    echo "YouTrack issue: ${'$'}ISSUE_URL"
                    echo "##teamcity[setParameter name='quality.gate.youtrack.issue' value='${'$'}ISSUE_URL']"

                    if [ -n "${'$'}TAG" ]; then
                        echo "Applying tag '${'$'}TAG' to ${'$'}ISSUE_ID ..."
                        curl -sS -X POST "${'$'}YOUTRACK_URL/api/commands" \
                            -H "Authorization: Bearer ${'$'}TOKEN" -H "Content-Type: application/json" -H "Accept: application/json" \
                            -d "{\"query\":\"tag {${'$'}TAG}\",\"issues\":[{\"idReadable\":\"${'$'}ISSUE_ID\"}]}" > /dev/null 2>&1 || echo "(tag request failed)"
                    fi

                    if [ "${'$'}CREATED_NEW" = "1" ] && [ -s "${'$'}KTOR_ZIPS" ]; then
                        sort -u "${'$'}KTOR_ZIPS" | head -8 | while IFS= read -r zip; do
                            [ -f "${'$'}zip" ] || continue
                            echo "Attaching ${'$'}zip to ${'$'}ISSUE_ID ..."
                            curl -sS -X POST "${'$'}YOUTRACK_URL/api/issues/${'$'}ISSUE_ID/attachments?fields=id,name" \
                                -H "Authorization: Bearer ${'$'}TOKEN" \
                                -F "file=@${'$'}zip;type=application/zip" > /dev/null 2>&1 || echo "(attach failed for ${'$'}zip)"
                        done
                    fi
                fi
                rm -f "${'$'}KTOR_ZIPS"

                echo "=== Step 4.5: Failure Investigation Completed ==="
                exit 0
            """.trimIndent()
        }
    }
}
