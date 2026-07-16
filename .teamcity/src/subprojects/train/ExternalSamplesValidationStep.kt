package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*

/**
 * Step 2: External Samples Validation
 * Validates external GitHub samples against the resolved EAP versions
 * Processes all samples and reports results
 */
object ExternalSamplesValidationStep {
    fun apply(steps: BuildSteps, os: Agents.OS) {
        val targetOs = EapSampleRouting.osId(os)

        if (os == Agents.OS.Linux) {
            steps.script {
                name = "Prerequisites: Accept Android SDK licenses"
                scriptContent = "yes | JAVA_HOME=${Env.JDK_LTS} %env.ANDROID_SDKMANAGER_PATH% --licenses"
            }

            steps.script {
                name = "Prerequisites: Warm up Docker images"
                scriptContent = """
                    #!/bin/bash
                    echo "Pulling common Docker images used by external samples..."
                    docker pull postgres:16-alpine || true
                    docker pull postgres:18-alpine || true
                    docker pull redis:7-alpine || true
                    docker pull quay.io/keycloak/keycloak:26.5.2 || true
                    docker pull rabbitmq:4.2.2-alpine || true
                    docker pull testcontainers/ryuk:0.11.0 || true
                    docker pull dpage/pgadmin4:latest || true
                    docker pull alpine:latest || true
                    docker pull ollama/ollama:latest || true
                    docker pull prom/prometheus:latest || true
                    docker pull grafana/grafana:latest || true
                    docker pull nginx:latest || true

                    echo "Starting Ollama for ktor-ai-server..."
                    docker run -d --name ollama -p 11434:11434 ollama/ollama || true

                    echo "Pulling llama3.2 model for Ollama..."
                    # Wait a bit for Ollama to start before pulling
                    sleep 5
                    docker exec ollama ollama pull llama3.2 || true
                """.trimIndent()
            }

            steps.script {
                name = "Prerequisites: Install Playwright browsers"
                scriptContent = """
                    #!/bin/bash
                    echo "Installing Playwright browsers..."
                    npx playwright install --with-deps chromium || true
                """.trimIndent()
            }
        }

        steps.script {
            name = "Step 2: External Samples Validation"
            scriptContent = """
            #!/bin/bash

            echo "=== Step 2: External Samples Validation ==="

            EAP_TARGET_OS="$targetOs"
            EAP_ACTIVE_OSES="${EapSampleRouting.activeIds}"
            echo "Validator OS: ${'$'}EAP_TARGET_OS (active matrix: ${'$'}EAP_ACTIVE_OSES)"

            # Get current parameter values or use fallback defaults
            KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
            KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.3.10/' || echo "2.3.10")
            
            # Option to fallback to compile on failure (enabled by default)
            TRY_COMPILE_ON_FAILURE=$(echo "%env.TRY_COMPILE_ON_FAILURE%" | sed 's/^%env\.TRY_COMPILE_ON_FAILURE%$/true/' || echo "true")
            
            echo "Validating external GitHub samples against EAP versions"
            echo "Ktor Version: ${'$'}KTOR_VERSION"
            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"
            echo "Try Compile on Failure: ${'$'}TRY_COMPILE_ON_FAILURE"

            # PR diff-scoping for sample selection
            PR_TARGETS="%env.KTOR_PR_TARGETS%"
            case "${'$'}PR_TARGETS" in *KTOR_PR_TARGETS*) PR_TARGETS="";; esac

            compute_pr_platforms() {
                local sets="${'$'}1" out=""
                [ -z "${'$'}sets" ] && { echo "ALL"; return; }
                echo "${'$'}sets" | grep -qw common && { echo "ALL"; return; }
                echo "${'$'}sets" | grep -qwE 'jvm'                                                                    && out="${'$'}out jvm"
                echo "${'$'}sets" | grep -qwE 'web|js|wasmJs'                                                          && out="${'$'}out web"
                echo "${'$'}sets" | grep -qwE 'posix|nix|linux|windows|mingw|darwin|macos|ios|tvos|watchos|androidNative' && out="${'$'}out native"
                echo "${'$'}sets" | grep -qwE 'wasmWasi'                                                               && out="${'$'}out wasmWasi"
                echo "${'$'}sets" | grep -qwE 'android'                                                                && out="${'$'}out android"
                echo "${'$'}sets" | grep -qwE 'nonJvm'                                                                 && out="${'$'}out web native wasmWasi"
                echo "${'$'}out" | tr ' ' '\n' | grep -v '^${'$'}' | sort -u | tr '\n' ' '
            }
            PR_PLATFORMS="$(compute_pr_platforms "${'$'}PR_TARGETS")"
            echo "PR-affected platforms for sample selection: ${'$'}{PR_PLATFORMS:-ALL}"

            # Detect which platforms a sample project targets
            detect_sample_platforms() {
                local dir="${'$'}1" out="" content=""
                content=$(find "${'$'}dir" -maxdepth 8 \( -name '*.gradle.kts' -o -name '*.gradle' -o -name 'module.yaml' -o -name '*.versions.toml' \) -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.gradle/*' -not -path '*/node_modules/*' -exec cat {} + 2>/dev/null)
                [ -z "${'$'}content" ] && { echo "jvm"; return; }
                echo "${'$'}content" | grep -qE '(^|[^A-Za-z])jvm[[:space:]]*[({]|kotlin\("jvm"\)|"application"|org\.jetbrains\.kotlin\.jvm' && out="${'$'}out jvm"
                echo "${'$'}content" | grep -qE '(^|[^A-Za-z])js[[:space:]]*[({]|wasmJs[[:space:]]*[({]' && out="${'$'}out web"
                echo "${'$'}content" | grep -qE 'linuxX64|linuxArm64|mingwX64|macosX64|macosArm64|iosX64|iosArm64|iosSimulatorArm64|watchos|tvos' && out="${'$'}out native"
                echo "${'$'}content" | grep -qE 'wasmWasi[[:space:]]*[({]' && out="${'$'}out wasmWasi"
                echo "${'$'}content" | grep -qE 'androidTarget[[:space:]]*[({]|com\.android\.(application|library)' && out="${'$'}out android"
                [ -z "${'$'}out" ] && out="jvm"
                echo "${'$'}out" | tr ' ' '\n' | grep -v '^${'$'}' | sort -u | tr '\n' ' '
            }

            detect_sample_native() {
                local dir="${'$'}1" out="" content=""
                content=$(find "${'$'}dir" -maxdepth 8 \( -name '*.gradle.kts' -o -name '*.gradle' -o -name 'module.yaml' -o -name '*.versions.toml' \) -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.gradle/*' -not -path '*/node_modules/*' -exec cat {} + 2>/dev/null | sed 's://.*::')
                echo "${'$'}content" | grep -qE 'linuxX64|linuxArm64' && out="${'$'}out linux"
                echo "${'$'}content" | grep -qE 'macosX64|macosArm64|iosX64|iosArm64|iosSimulatorArm64|watchos|tvos' && out="${'$'}out apple"
                echo "${'$'}content" | grep -qE 'mingwX64' && out="${'$'}out mingw"
                echo "${'$'}out" | tr ' ' '\n' | grep -v '^${'$'}' | sort -u | tr '\n' ' '
            }

${EapSampleRouting.routingBash.prependIndent("            ")}

            # Detects if the sample should run given the PR scope
            sample_in_scope() {
                local dir="${'$'}1"
                { [ "${'$'}PR_PLATFORMS" = "ALL" ] || [ -z "${'$'}PR_PLATFORMS" ]; } && return 0
                local sp p
                sp="$(detect_sample_platforms "${'$'}dir")"
                for p in ${'$'}sp; do
                    echo "${'$'}PR_PLATFORMS" | grep -qw "${'$'}p" && return 0
                done
                echo "⏭️  Skipping $(basename "${'$'}dir"): targets [${'$'}sp] not in PR-affected platforms [${'$'}PR_PLATFORMS]"
                return 1
            }

            PR_PUBLISHED_PLATFORMS=""
            if [ -n "${'$'}{KTOR_PR_REPO_DIR:-}" ] && [ -d "${'$'}{KTOR_PR_REPO_DIR:-}/io/ktor" ]; then
                KROOT="${'$'}KTOR_PR_REPO_DIR/io/ktor"
                plat_published() {
                    local suf
                    for suf in "$@"; do
                        compgen -G "${'$'}KROOT/*${'$'}suf" >/dev/null 2>&1 && return 0
                    done
                    return 1
                }
                plat_published '-jvm'                 && PR_PUBLISHED_PLATFORMS="${'$'}PR_PUBLISHED_PLATFORMS jvm"
                plat_published '-js' '-wasm-js'        && PR_PUBLISHED_PLATFORMS="${'$'}PR_PUBLISHED_PLATFORMS web"
                plat_published '-wasm-wasi'            && PR_PUBLISHED_PLATFORMS="${'$'}PR_PUBLISHED_PLATFORMS wasmWasi"
                plat_published '-android'             && PR_PUBLISHED_PLATFORMS="${'$'}PR_PUBLISHED_PLATFORMS android"
                plat_published '-linuxx64' '-linuxarm64' '-macosx64' '-macosarm64' '-mingwx64' \
                               '-iosx64' '-iosarm64' '-iossimulatorarm64' '-watchosarm64' '-watchosx64' \
                               '-watchossimulatorarm64' '-tvosarm64' '-tvosx64' '-tvossimulatorarm64' \
                                                      && PR_PUBLISHED_PLATFORMS="${'$'}PR_PUBLISHED_PLATFORMS native"
                PR_PUBLISHED_PLATFORMS=$(echo "${'$'}PR_PUBLISHED_PLATFORMS" | tr ' ' '\n' | grep -v '^${'$'}' | sort -u | tr '\n' ' ')
                echo "PR repo published platforms: ${'$'}{PR_PUBLISHED_PLATFORMS:-<none detected>}"
            fi

            sample_buildable_in_pr() {
                local dir="${'$'}1"
                [ -z "${'$'}{KTOR_PR_REPO_DIR:-}" ] && return 0     # not a PR run — no restriction
                [ -z "${'$'}PR_PUBLISHED_PLATFORMS" ] && return 0   # detection failed — don't over-skip
                local sp p
                sp="$(detect_sample_platforms "${'$'}dir")"
                for p in ${'$'}sp; do
                    if ! echo "${'$'}PR_PUBLISHED_PLATFORMS" | grep -qw "${'$'}p"; then
                        echo "⏭️  Skipping $(basename "${'$'}dir"): needs '${'$'}p' but PR repo only published [${'$'}PR_PUBLISHED_PLATFORMS]"
                        return 1
                    fi
                done
                return 0
            }

            # Print the root-cause section of a failed build log. 
            print_failure_excerpt() {
                local log="${'$'}1" start
                [ -f "${'$'}log" ] || return 0
                start=$(grep -nE "FAILURE: Build failed with an exception|BUILD FAILURE" "${'$'}log" | tail -1 | cut -d: -f1)
                if [ -n "${'$'}start" ]; then
                    echo "----- build log from failure banner (capped 150 lines) -----"
                    tail -n +"${'$'}start" "${'$'}log" | head -n 150
                else
                    echo "----- build log (last 80 lines) -----"
                    tail -n 80 "${'$'}log"
                fi
            }

            # Serve the PR file repo over HTTPS (self-signed cert, trusted via a combined truststore)
            PR_REPO_SERVER_PID=""
            PR_REPO_TLS_TMP=""
            stop_pr_repo_server() {
                [ -n "${'$'}PR_REPO_SERVER_PID" ] && kill "${'$'}PR_REPO_SERVER_PID" 2>/dev/null || true
                [ -n "${'$'}PR_REPO_TLS_TMP" ] && rm -rf "${'$'}PR_REPO_TLS_TMP" 2>/dev/null || true
            }
            trap stop_pr_repo_server EXIT
            if [ -n "${'$'}{KTOR_PR_REPO:-}" ] && [ -d "${'$'}{KTOR_PR_REPO_DIR:-}" ]; then
                PR_PORT=$(printf '%s' "${'$'}KTOR_PR_REPO" | sed -E 's#.*://[^:/]+:([0-9]+).*#\1#')
                [ -n "${'$'}PR_PORT" ] || PR_PORT=8347
                PR_REPO_TLS_TMP=$(mktemp -d)
                PR_CACERTS="${'$'}JAVA_HOME/lib/security/cacerts"
                [ -f "${'$'}PR_CACERTS" ] || PR_CACERTS="${'$'}JAVA_HOME/jre/lib/security/cacerts"
                "${'$'}JAVA_HOME/bin/keytool" -genkeypair -keyalg RSA -keysize 2048 -alias srv -keystore "${'$'}PR_REPO_TLS_TMP/server.p12" \
                    -storetype PKCS12 -storepass changeit -validity 3650 -dname "CN=localhost" -ext "SAN=DNS:localhost,IP:127.0.0.1" >/dev/null 2>&1
                openssl pkcs12 -in "${'$'}PR_REPO_TLS_TMP/server.p12" -out "${'$'}PR_REPO_TLS_TMP/server.pem" -nodes -passin pass:changeit >/dev/null 2>&1
                "${'$'}JAVA_HOME/bin/keytool" -exportcert -alias srv -keystore "${'$'}PR_REPO_TLS_TMP/server.p12" -storepass changeit -rfc -file "${'$'}PR_REPO_TLS_TMP/cert.pem" >/dev/null 2>&1
                cp "${'$'}PR_CACERTS" "${'$'}PR_REPO_TLS_TMP/truststore.jks"
                "${'$'}JAVA_HOME/bin/keytool" -importcert -noprompt -alias ktor-pr-repo -file "${'$'}PR_REPO_TLS_TMP/cert.pem" -keystore "${'$'}PR_REPO_TLS_TMP/truststore.jks" -storepass changeit >/dev/null 2>&1
                printf '%s\n' \
                    'import http.server, ssl, sys, functools' \
                    'ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); ctx.load_cert_chain(sys.argv[3])' \
                    'h = functools.partial(http.server.SimpleHTTPRequestHandler, directory=sys.argv[2])' \
                    'srv = http.server.ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), h)' \
                    'srv.socket = ctx.wrap_socket(srv.socket, server_side=True)' \
                    'srv.serve_forever()' \
                    > "${'$'}PR_REPO_TLS_TMP/serve.py"
                python3 "${'$'}PR_REPO_TLS_TMP/serve.py" "${'$'}PR_PORT" "${'$'}KTOR_PR_REPO_DIR" "${'$'}PR_REPO_TLS_TMP/server.pem" > pr-repo-https.log 2>&1 &
                PR_REPO_SERVER_PID=$!
                export JAVA_TOOL_OPTIONS="${'$'}{JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=${'$'}PR_REPO_TLS_TMP/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
                for i in $(seq 1 30); do curl -sfk "${'$'}KTOR_PR_REPO/io/ktor/ktor-bom/" >/dev/null 2>&1 && break; sleep 1; done
                echo "🔒 Serving PR repo ${'$'}KTOR_PR_REPO_DIR at ${'$'}KTOR_PR_REPO (pid ${'$'}PR_REPO_SERVER_PID)"
            fi

            WORK_DIR=$(pwd)
            REPORTS_DIR="${'$'}WORK_DIR/external-validation-reports"
            SAMPLES_DIR="${'$'}WORK_DIR/external-samples"

            # Resolve latest KSP
            LATEST_KSP=$(curl -sSfL --max-time 15 "https://plugins.gradle.org/m2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml" 2>/dev/null \
                | grep -oE "<latest>[^<]+</latest>" | sed -E 's#</?latest>##g' | head -1 || true)
            echo "Latest KSP plugin version: ${'$'}{LATEST_KSP:-<lookup-failed>}"

            # Create necessary directories
            mkdir -p "${'$'}REPORTS_DIR"
            mkdir -p "${'$'}SAMPLES_DIR"

            mkdir -p "${'$'}HOME/.m2"

            PR_MAVEN_PROFILE=""
            if [ -n "${'$'}{KTOR_PR_REPO:-}" ]; then
                PR_MAVEN_PROFILE="<profiles><profile><id>ktor-pr-local</id><repositories><repository><id>ktor-pr-local</id><url>${'$'}KTOR_PR_REPO</url><releases><enabled>true</enabled></releases><snapshots><enabled>true</enabled></snapshots></repository></repositories><pluginRepositories><pluginRepository><id>ktor-pr-local-plugins</id><url>${'$'}KTOR_PR_REPO</url></pluginRepository></pluginRepositories></profile></profiles><activeProfiles><activeProfile>ktor-pr-local</activeProfile></activeProfiles>"
            fi

            cat > "${'$'}HOME/.m2/settings.xml" <<SETTINGS_EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <mirrors>
        <mirror>
            <id>jetbrains-cache-central</id>
            <name>JetBrains cache-redirector mirror of Maven Central</name>
            <url>https://cache-redirector.jetbrains.com/repo1.maven.org/maven2</url>
            <mirrorOf>central</mirrorOf>
        </mirror>
    </mirrors>
    ${'$'}PR_MAVEN_PROFILE
</settings>
SETTINGS_EOF
            echo "Wrote ~/.m2/settings.xml with JetBrains cache-redirector mirror"

            mkdir -p "${'$'}HOME/.gradle/init.d"
            cat > "${'$'}HOME/.gradle/init.d/cache-redirector.gradle" <<'INIT_EOF'
def cacheRedirector = "https://cache-redirector.jetbrains.com"

def ktorPrRepo = System.getenv("KTOR_PR_REPO")?.trim()

def kotlinV = System.getenv("KOTLIN_VERSION")
def kotlinIsDev = kotlinV != null && (kotlinV.contains("-dev") || kotlinV.contains("-Beta") || kotlinV.contains("-RC") || kotlinV.contains("-M") || kotlinV.toLowerCase().contains("eap") || kotlinV.contains("SNAPSHOT"))

def replaceUrl = { repo ->
    if (repo instanceof org.gradle.api.artifacts.repositories.MavenArtifactRepository) {
        def u = repo.url?.toString()
        if (u == null) return
        if (u.startsWith("https://repo.maven.apache.org/maven2") ||
            u.startsWith("https://repo1.maven.org/maven2")) {
            repo.url = uri("${'$'}{cacheRedirector}/repo1.maven.org/maven2/")
        } else if (u.startsWith("https://plugins.gradle.org/m2")) {
            repo.url = uri("${'$'}{cacheRedirector}/plugins.gradle.org/m2/")
        } else if (u.startsWith("https://maven.google.com")) {
            repo.url = uri("${'$'}{cacheRedirector}/maven.google.com/")
        } else if (u.startsWith("https://dl.google.com/dl/android/maven2")) {
            repo.url = uri("${'$'}{cacheRedirector}/dl.google.com/dl/android/maven2/")
        }
    }
}

beforeSettings { settings ->
    settings.pluginManagement.repositories.all(replaceUrl)
    settings.pluginManagement.repositories.gradlePluginPortal()
    settings.pluginManagement.repositories.mavenCentral()
    if (ktorPrRepo) settings.pluginManagement.repositories.maven { url = uri(ktorPrRepo); allowInsecureProtocol = true }
    settings.pluginManagement.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/ktor-eap") }
    if (kotlinIsDev) settings.pluginManagement.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/dev") }

    settings.pluginManagement.resolutionStrategy.eachPlugin { details ->
        if (details.requested.id.id == "io.ktor.plugin") {
            def v = System.getenv("KTOR_COMPILER_PLUGIN_VERSION")
            if (v != null && !v.isEmpty()) {
                details.useVersion(v)
            }
        }
    }
}

settingsEvaluated { settings ->
    if (settings.dependencyResolutionManagement) {
        settings.dependencyResolutionManagement.repositories.all(replaceUrl)
        if (ktorPrRepo) settings.dependencyResolutionManagement.repositories.maven { url = uri(ktorPrRepo); allowInsecureProtocol = true }
        settings.dependencyResolutionManagement.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/ktor-eap") }
        if (kotlinIsDev) settings.dependencyResolutionManagement.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/dev") }
    }
}

allprojects {
    buildscript.repositories.all(replaceUrl)
    repositories.all(replaceUrl)
    if (ktorPrRepo) buildscript.repositories.maven { url = uri(ktorPrRepo); allowInsecureProtocol = true }
    if (ktorPrRepo) repositories.maven { url = uri(ktorPrRepo); allowInsecureProtocol = true }
    buildscript.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/ktor-eap") }
    if (kotlinIsDev) buildscript.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/dev") }
    buildscript.repositories.maven { url = uri("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/") }
    repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/ktor-eap") }
    if (kotlinIsDev) repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/dev") }
    repositories.maven { url = uri("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/") }
    repositories.maven { url = uri("https://cache-redirector.jetbrains.com/maven.google.com/") }

    if (rootProject.name == "buildSrc") {
        configurations.all {
            resolutionStrategy.eachDependency { details ->
                if (details.requested.group == "org.jetbrains.kotlin" &&
                    details.requested.name in ["kotlin-stdlib", "kotlin-stdlib-jdk8", "kotlin-stdlib-jdk7", "kotlin-stdlib-common", "kotlin-reflect"]) {
                    def v = System.getenv("KOTLIN_VERSION")
                    if (v != null && !v.isEmpty()) details.useVersion(v)
                }
            }
        }
    }

    tasks.matching { it.name == "wasmJsBrowserProductionWebpack" }.configureEach {
        enabled = false
    }
}
INIT_EOF
            echo "Wrote ~/.gradle/init.d/cache-redirector.gradle"

                # Define external sample repositories
                declare -A EXTERNAL_SAMPLES=(
                    ["ktor-ai-server"]="https://github.com/nomisRev/ktor-ai-server.git"
                    ["ktor-native-server"]="https://github.com/nomisRev/ktor-native-server.git"
                    ["ktor-config-example"]="https://github.com/nomisRev/ktor-config-example.git"
                    ["ktor-workshop-2025"]="https://github.com/nomisRev/ktor-workshop-2025.git"
                    ["amper-ktor-sample"]="https://github.com/nomisRev/amper-ktor-sample.git"
                    ["ktor-full-stack-real-world"]="https://github.com/nomisRev/ktor-full-stack-real-world.git"
                    ["foodies"]="https://github.com/nomisRev/foodies"
                    ["ktkit"]="https://github.com/smyrgeorge/ktkit"
                )

            echo "Cloning external sample repositories..."
            clone_pids=()
            for sample_name in "${'$'}{!EXTERNAL_SAMPLES[@]}"; do
                sample_url="${'$'}{EXTERNAL_SAMPLES[${'$'}sample_name]}"
                target_dir="${'$'}SAMPLES_DIR/${'$'}sample_name"
                
                (
                    echo "Cloning ${'$'}sample_name from ${'$'}sample_url to ${'$'}target_dir"
                    if [ -d "${'$'}target_dir" ]; then
                        rm -rf "${'$'}target_dir"
                    fi
                    
                    if git clone --depth 1 "${'$'}sample_url" "${'$'}target_dir" >/dev/null 2>&1; then
                        echo "✅ Successfully cloned ${'$'}sample_name"
                    else
                        echo "❌ Failed to clone ${'$'}sample_name"
                    fi
                ) &
                clone_pids+=($!)
            done
            
            # Wait for all clones to complete
            for clone_pid in "${'$'}{clone_pids[@]}"; do
                wait "${'$'}clone_pid"
            done
            echo "All repositories cloned"

            # Define external sample project directories using absolute paths
            EXTERNAL_SAMPLE_DIRS=(
                "${'$'}SAMPLES_DIR/ktor-ai-server"
                "${'$'}SAMPLES_DIR/ktor-native-server"
                "${'$'}SAMPLES_DIR/ktor-config-example"
                "${'$'}SAMPLES_DIR/ktor-workshop-2025"
                "${'$'}SAMPLES_DIR/amper-ktor-sample"
                "${'$'}SAMPLES_DIR/ktor-full-stack-real-world"
                "${'$'}SAMPLES_DIR/foodies"
                "${'$'}SAMPLES_DIR/ktkit"
            )

            echo "External sample projects to validate:"
            for project_dir in "${'$'}{EXTERNAL_SAMPLE_DIRS[@]}"; do
                echo "- ${'$'}project_dir"
            done

            # Create gradle.properties with EAP versions for Gradle projects
            cat > "${'$'}WORK_DIR/gradle.properties.eap" <<EOF
kotlin_version=${'$'}KOTLIN_VERSION
ktor_version=${'$'}KTOR_VERSION
logback_version=1.4.14
kotlin.mpp.stability.nowarn=true
org.gradle.jvmargs=-Xmx4g
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.caching=false
kotlin.compiler.execution.strategy=in-process
kotlin.incremental=true
EOF

            # Counts only the samples this OS owns
            TOTAL_SAMPLES=0
            SUCCESSFUL_SAMPLES=0
            FAILED_SAMPLES=0
            SKIPPED_SAMPLES=0

            echo "Starting validation of ${'$'}TOTAL_SAMPLES projects..."

            # JDK selection: every sample builds on the pinned JDK by default. Capture the pinned JDK here so
            # each iteration can restore it before applying any per-sample override.
            ORIGINAL_JAVA_HOME="${'$'}{JAVA_HOME:-}"
            ORIGINAL_PATH="${'$'}PATH"
            KTKIT_JAVA_HOME="${Env.JDK_21}"

            for project_dir in "${'$'}{EXTERNAL_SAMPLE_DIRS[@]}"; do
                project_name=$(basename "${'$'}project_dir")
                echo "---------------------------------------------------"
                echo "Validating ${'$'}project_name..."

                if ! sample_routes_here "${'$'}project_dir"; then
                    continue
                fi
                TOTAL_SAMPLES=$((TOTAL_SAMPLES + 1))

                # Reset to the pinned JDK, then override for the samples that need a specific one.
                if [ -n "${'$'}ORIGINAL_JAVA_HOME" ]; then export JAVA_HOME="${'$'}ORIGINAL_JAVA_HOME"; else unset JAVA_HOME; fi
                export PATH="${'$'}ORIGINAL_PATH"
                if [ "${'$'}project_name" = "ktkit" ] && [ -n "${'$'}KTKIT_JAVA_HOME" ] && [ -d "${'$'}KTKIT_JAVA_HOME" ]; then
                    export JAVA_HOME="${'$'}KTKIT_JAVA_HOME"
                    export PATH="${'$'}KTKIT_JAVA_HOME/bin:${'$'}ORIGINAL_PATH"
                    echo "  [jdk] Building ${'$'}project_name on JDK 21 (${'$'}JAVA_HOME)"
                fi

                if [ ! -d "${'$'}project_dir" ]; then
                    echo "⚠️  Project directory not found: ${'$'}project_dir"
                    SKIPPED_SAMPLES=$((SKIPPED_SAMPLES + 1))
                    continue
                fi

                if ! sample_in_scope "${'$'}project_dir"; then
                    SKIPPED_SAMPLES=$((SKIPPED_SAMPLES + 1))
                    continue
                fi

                if ! sample_buildable_in_pr "${'$'}project_dir"; then
                    SKIPPED_SAMPLES=$((SKIPPED_SAMPLES + 1))
                    continue
                fi

                cd "${'$'}project_dir"

                # Pick the Kotlin version for this sample
                SAMPLE_KOTLIN="${'$'}KOTLIN_VERSION"
                DETECTED_KOTLIN=$( {
                    grep -hoE '^[[:space:]]*kotlin[[:space:]]*=[[:space:]]*"[^"]+"' gradle/libs.versions.toml 2>/dev/null | sed -E 's/.*"([^"]+)".*/\1/'
                    grep -hoE '^[[:space:]]*kotlin_version[[:space:]]*=[[:space:]]*[^[:space:]]+' gradle.properties 2>/dev/null | sed -E 's/.*=[[:space:]]*//'
                } | grep -E '^[0-9]' | sort -V | tail -1 )
                if [ -n "${'$'}DETECTED_KOTLIN" ]; then
                    SAMPLE_KOTLIN=$(printf '%s\n%s\n' "${'$'}DETECTED_KOTLIN" "${'$'}KOTLIN_VERSION" | sort -V | tail -1)
                fi
                [ "${'$'}SAMPLE_KOTLIN" != "${'$'}KOTLIN_VERSION" ] && \
                    echo "  [patcher] Building ${'$'}project_name with its own Kotlin ${'$'}SAMPLE_KOTLIN (>= Ktor-build ${'$'}KOTLIN_VERSION)"

                # Apply EAP versions
                if [ -f "gradle.properties" ]; then
                    cp "${'$'}WORK_DIR/gradle.properties.eap" gradle.properties
                    sed -i -E "s@^([[:space:]]*kotlin_version[[:space:]]*=).*@\1${'$'}SAMPLE_KOTLIN@" gradle.properties
                    
                    # Enable AndroidX if it seems necessary
                    if grep -rnE "androidx|android" . > /dev/null 2>&1; then
                        echo "android.useAndroidX=true" >> gradle.properties
                        echo "Enabled AndroidX for ${'$'}project_name"
                    fi
                    
                    echo "Applied EAP versions to gradle.properties"
                fi

                WRAPPER_FILE="gradle/wrapper/gradle-wrapper.properties"
                if [ -f "${'$'}WRAPPER_FILE" ]; then
                    CURRENT=$(grep -oE 'gradle-[0-9]+\.[0-9]+(\.[0-9]+)?' "${'$'}WRAPPER_FILE" | head -1 | sed 's/gradle-//')
                    if [ -n "${'$'}CURRENT" ]; then
                        MAJOR=$(echo "${'$'}CURRENT" | cut -d. -f1)
                        MINOR=$(echo "${'$'}CURRENT" | cut -d. -f2)
                        if [ "${'$'}MAJOR" -lt 8 ] || { [ "${'$'}MAJOR" -eq 8 ] && [ "${'$'}MINOR" -lt 11 ]; }; then
                            echo "  [patcher] Bumping Gradle wrapper ${'$'}CURRENT → 8.11.1 (Shadow needs 8.11+)"
                            sed -i -E 's|gradle-[0-9]+\.[0-9]+(\.[0-9]+)?|gradle-8.11.1|g' "${'$'}WRAPPER_FILE"
                        fi
                    fi
                fi

                find . -name "build.gradle.kts" -type f -not -path "*/build/*" -exec \
                    sed -i -E 's|^([[:space:]]*)(moduleName[[:space:]]*=)|\1// \2|' {} +

                while IFS= read -r -d '' toml; do
                    echo "  [patcher] Patching version catalog: ${'$'}toml"
                    sed -i -E "s@^([[:space:]]*(ktor|ktor-version|ktor_version|ktorVersion)[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}KTOR_VERSION\3@g" "${'$'}toml"
                    sed -i -E "s@^([[:space:]]*kotlin[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}SAMPLE_KOTLIN\2@g" "${'$'}toml"
                    if [ -n "${'$'}KTOR_COMPILER_PLUGIN_VERSION" ]; then
                        # Match BOTH version.ref = "X" AND version = "X" (literal) — some catalogs
                        # like ktor-workshop-2025 use a direct version string for io.ktor.plugin.
                        sed -i -E "s@([\"']io\.ktor\.plugin[\"'][[:space:]]*,[[:space:]]*version)(\.ref)?[[:space:]]*=[[:space:]]*[\"'][^\"']+[\"']@\1 = \"${'$'}KTOR_COMPILER_PLUGIN_VERSION\"@g" "${'$'}toml"
                    fi
                    sed -i -E "s@^([[:space:]]*(ktor-bom|ktor_bom)[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}KTOR_VERSION\3@g" "${'$'}toml"

                    if [ -n "${'$'}LATEST_KSP" ]; then
                        sed -i -E "s@^([[:space:]]*ksp[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}LATEST_KSP\2@g" "${'$'}toml"
                    fi
                done < <(find . -name "libs.versions.toml" -type f -not -path "*/build/*" -not -path "*/.gradle/*" -print0)

                # Run validation (build/test)
                BUILD_SUCCESS=false
                
                GRADLE_ARGS="assemble --no-daemon"

                if [ -f "module.yaml" ] || [ -d ".amper" ] || [ -f "amper" ]; then
                    echo "Amper project detected. Updating versions in module.yaml or equivalent..."
                    find . -name "*.yaml" -type f -exec sed -i "s/ktor: .*/ktor: ${'$'}KTOR_VERSION/g" {} +
                    find . -name "*.yaml" -type f -exec sed -i "s/kotlin: .*/kotlin: ${'$'}SAMPLE_KOTLIN/g" {} +
                    find . -name "*.yaml" -type f -exec sed -i 's|\${'$'}kotlin-test-junit|\${'$'}libs.kotlin.test|g' {} +

                    REPOS_TO_INJECT="$(printf '  - %s\n' \
                        ${'$'}{KTOR_PR_REPO:+"${'$'}KTOR_PR_REPO"} \
                        "https://redirector.kotlinlang.org/maven/ktor-eap" \
                        "https://redirector.kotlinlang.org/maven/dev" \
                        "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")"
                    while IFS= read -r -d '' yaml; do
                        if grep -q "redirector.kotlinlang.org/maven/ktor-eap" "${'$'}yaml"; then
                            continue
                        fi
                        if grep -q "^repositories:" "${'$'}yaml"; then
                            awk -v reps="${'$'}REPOS_TO_INJECT" '
                                /^repositories:/ && !done { print; print reps; done=1; next }
                                { print }
                            ' "${'$'}yaml" > "${'$'}yaml.tmp" && mv "${'$'}yaml.tmp" "${'$'}yaml"
                        else
                            { printf 'repositories:\n%s\n' "${'$'}REPOS_TO_INJECT"; cat "${'$'}yaml"; } > "${'$'}yaml.tmp" \
                                && mv "${'$'}yaml.tmp" "${'$'}yaml"
                        fi
                        echo "  ✅ Injected EAP repos into ${'$'}yaml"
                    done < <(find . \( -name "module.yaml" -o -name "project.yaml" -o -name "template.yaml" \) -type f -print0)
                    
                    if [ -f "amper" ]; then
                        chmod +x amper

                        AMPER_META_URL="https://packages.jetbrains.team/maven/p/amper/amper/org/jetbrains/amper/cli/maven-metadata.xml"
                        LATEST_AMPER_VERSION=$(curl -sSfL --max-time 15 "${'$'}AMPER_META_URL" 2>/dev/null \
                            | grep -oE "<latest>[^<]+</latest>" | sed -E 's#</?latest>##g' | head -1 || true)
                        if [ -n "${'$'}LATEST_AMPER_VERSION" ]; then
                            AMPER_SHA_URL="https://packages.jetbrains.team/maven/p/amper/amper/org/jetbrains/amper/cli/${'$'}LATEST_AMPER_VERSION/cli-${'$'}LATEST_AMPER_VERSION-dist.tgz.sha256"
                            LATEST_AMPER_SHA256=$(curl -sSfL --max-time 15 "${'$'}AMPER_SHA_URL" 2>/dev/null | tr -d '[:space:]' || true)
                            if [ -n "${'$'}LATEST_AMPER_SHA256" ]; then
                                OLD_AMPER_VERSION=$(grep -E '^amper_version=' amper | head -1 | cut -d= -f2)
                                if [ "${'$'}OLD_AMPER_VERSION" != "${'$'}LATEST_AMPER_VERSION" ]; then
                                    echo "Upgrading amper wrapper: ${'$'}OLD_AMPER_VERSION → ${'$'}LATEST_AMPER_VERSION"
                                    sed -i -E "s|^(amper_version=).*$|\1${'$'}LATEST_AMPER_VERSION|" amper
                                    sed -i -E "s|^(amper_sha256=).*$|\1${'$'}LATEST_AMPER_SHA256|" amper
                                fi
                            else
                                echo "⚠️  Could not fetch sha256 for Amper ${'$'}LATEST_AMPER_VERSION — keeping pinned wrapper version"
                            fi
                        else
                            echo "⚠️  Could not resolve latest Amper version — keeping pinned wrapper version"
                        fi

                        AMPER_M2_PATHS=(
                            "${'$'}HOME/.cache/Amper/.m2.cache"
                            "${'$'}HOME/.cache/JetBrains/Amper/.m2.cache"
                        )
                        for p in "${'$'}{AMPER_M2_PATHS[@]}"; do
                            if [ -d "${'$'}p" ]; then
                                echo "Pruning empty entries in Amper m2 cache at ${'$'}p"
                                find "${'$'}p" -type d -empty -delete 2>/dev/null || true
                            fi
                        done

                        run_amper_build() {
                            ./amper build > "${'$'}REPORTS_DIR/${'$'}project_name-build.log" 2>&1
                        }

                        attempt=1
                        max_attempts=5
                        while [ ${'$'}attempt -le ${'$'}max_attempts ]; do
                            echo "Running: ./amper build (attempt ${'$'}attempt/${'$'}max_attempts)"
                            if run_amper_build; then
                                BUILD_SUCCESS=true
                                break
                            fi

                            if [ ${'$'}attempt -eq ${'$'}max_attempts ]; then
                                break
                            fi

                            if grep -qE "actual: 429|HTTP/[0-9.]+ 429|response code.*429" "${'$'}REPORTS_DIR/${'$'}project_name-build.log"; then
                                delay=$((attempt * 60))
                                echo "⚠️  Detected Maven Central rate-limit (HTTP 429) — waiting ${'$'}{delay}s before retry"
                                sleep ${'$'}delay
                            elif grep -q "missing on disk" "${'$'}REPORTS_DIR/${'$'}project_name-build.log"; then
                                echo "⚠️  Detected stale Amper cache — clearing and retrying"
                                for p in "${'$'}{AMPER_M2_PATHS[@]}"; do
                                    [ -d "${'$'}p" ] && rm -rf "${'$'}p"
                                done
                            else
                                break
                            fi

                            attempt=$((attempt + 1))
                        done

                        if [ "${'$'}BUILD_SUCCESS" = true ]; then
                            echo "Build successful, now running tests: ./amper test"
                            if ./amper test >> "${'$'}REPORTS_DIR/${'$'}project_name-build.log" 2>&1; then
                                echo "✅ ${'$'}project_name: Tests passed"
                            else
                                echo "⚠️  ${'$'}project_name: Tests failed (but build passed)"
                            fi
                        fi
                    fi
                fi

                # Maven sample
                if [ "${'$'}BUILD_SUCCESS" = false ] && [ -f "pom.xml" ]; then
                    echo "Maven sample detected. Running: mvn compile -B"
                    if mvn compile -B -Dktor.version="${'$'}KTOR_VERSION" -Dkotlin.version="${'$'}KOTLIN_VERSION" > "${'$'}REPORTS_DIR/${'$'}project_name-build.log" 2>&1; then
                        BUILD_SUCCESS=true
                        echo "Build successful, now running tests: mvn test -B"
                        if mvn test -B -Dktor.version="${'$'}KTOR_VERSION" -Dkotlin.version="${'$'}KOTLIN_VERSION" >> "${'$'}REPORTS_DIR/${'$'}project_name-build.log" 2>&1; then
                             echo "✅ ${'$'}project_name: Tests passed"
                        else
                             echo "⚠️  ${'$'}project_name: Tests failed (but build passed)"
                        fi
                    fi
                fi

                if [ "${'$'}BUILD_SUCCESS" = false ]; then
                    run_gradle_with_429_retry() {
                        local cmd="${'$'}1"
                        local g_attempt=1
                        local g_max=3
                        while [ ${'$'}g_attempt -le ${'$'}g_max ]; do
                            echo "Running: ${'$'}cmd (attempt ${'$'}g_attempt/${'$'}g_max)"
                            if eval "${'$'}cmd" > "${'$'}REPORTS_DIR/${'$'}project_name-build.log" 2>&1; then
                                return 0
                            fi
                            if [ ${'$'}g_attempt -eq ${'$'}g_max ]; then
                                return 1
                            fi
                            if grep -qE "Received status code 429|Too Many Requests|HTTP/[0-9.]+ 429|could not resolve plugin artifact" "${'$'}REPORTS_DIR/${'$'}project_name-build.log"; then
                                local delay=$((g_attempt * 60))
                                echo "⚠️  Detected transient Gradle resolution failure (likely HTTP 429) — waiting ${'$'}{delay}s before retry"
                                sleep ${'$'}delay
                            else
                                return 1
                            fi
                            g_attempt=$((g_attempt + 1))
                        done
                        return 1
                    }

                    if [ -f "gradlew" ]; then
                        chmod +x gradlew
                        if run_gradle_with_429_retry "./gradlew ${'$'}GRADLE_ARGS"; then
                            BUILD_SUCCESS=true
                            
                            # Optionally run tests if build succeeded and test task exists
                            if ./gradlew tasks --all | grep -q "[:[:alnum:]]*test"; then
                                echo "Build successful, now running tests: ./gradlew test --no-daemon"
                                if ./gradlew test --no-daemon >> "${'$'}REPORTS_DIR/${'$'}project_name-build.log" 2>&1; then
                                    echo "✅ ${'$'}project_name: Tests passed"
                                else
                                    echo "⚠️  ${'$'}project_name: Tests failed (but build passed)"
                                fi
                            fi
                        fi
                    elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
                        if run_gradle_with_429_retry "gradle ${'$'}GRADLE_ARGS"; then
                            BUILD_SUCCESS=true
                            
                            # Optionally run tests if build succeeded and test task exists
                            if gradle tasks --all | grep -q "[:[:alnum:]]*test"; then
                                echo "Build successful, now running tests: gradle test --no-daemon"
                                if gradle test --no-daemon >> "${'$'}REPORTS_DIR/${'$'}project_name-build.log" 2>&1; then
                                    echo "✅ ${'$'}project_name: Tests passed"
                                else
                                    echo "⚠️  ${'$'}project_name: Tests failed (but build passed)"
                                fi
                            fi
                        fi
                    elif [ ! -f "amper" ] && [ ! -f "pom.xml" ]; then
                        echo "⚠️  No Gradle wrapper, build file, Maven pom, or Amper config found in ${'$'}project_name"
                        SKIPPED_SAMPLES=$((SKIPPED_SAMPLES + 1))
                        cd "${'$'}WORK_DIR"
                        continue
                    fi
                fi

                if [ "${'$'}BUILD_SUCCESS" = true ]; then
                    echo "✅ ${'$'}project_name: VALIDATION PASSED"
                    SUCCESSFUL_SAMPLES=$((SUCCESSFUL_SAMPLES + 1))
                    touch "${'$'}REPORTS_DIR/${'$'}project_name.passed"
                else
                    echo "❌ ${'$'}project_name: VALIDATION FAILED"
                    print_failure_excerpt "${'$'}REPORTS_DIR/${'$'}project_name-build.log"
                    FAILED_SAMPLES=$((FAILED_SAMPLES + 1))
                    touch "${'$'}REPORTS_DIR/${'$'}project_name.failed"
                    
                    # Package failed sample as a reproducer artifact
                    echo "📦 Packaging ${'$'}project_name as reproducer artifact..."
                    mkdir -p "${'$'}WORK_DIR/failed-samples"
                    (cd "${'$'}SAMPLES_DIR" && zip -r "${'$'}WORK_DIR/failed-samples/${'$'}project_name.zip" "${'$'}project_name" \
                        -x "${'$'}project_name/.gradle/*" \
                        -x "${'$'}project_name/build/*" \
                        -x "${'$'}project_name/*/build/*" \
                        -x "${'$'}project_name/.kotlin/*" \
                    ) || echo "⚠️  Failed to package ${'$'}project_name reproducer"
                fi
                
                cd "${'$'}WORK_DIR"
            done

            echo "---------------------------------------------------"
            echo "External Validation Summary:"
            echo "Total: ${'$'}TOTAL_SAMPLES"
            echo "Successful: ${'$'}SUCCESSFUL_SAMPLES"
            echo "Failed: ${'$'}FAILED_SAMPLES"
            echo "Skipped: ${'$'}SKIPPED_SAMPLES"

            # Calculate success rate
            SUCCESS_RATE=0
            if [[ -n "${'$'}TOTAL_SAMPLES" && "${'$'}TOTAL_SAMPLES" -gt 0 ]]; then
                SUCCESS_RATE=$(echo "${'$'}SUCCESSFUL_SAMPLES ${'$'}TOTAL_SAMPLES" | awk '{printf "%.1f", $1 * 100 / $2}')
            fi

            # Report results to TeamCity
            echo "##teamcity[setParameter name='external.validation.total.samples' value='${'$'}TOTAL_SAMPLES']"
            echo "##teamcity[setParameter name='external.validation.successful.samples' value='${'$'}SUCCESSFUL_SAMPLES']"
            echo "##teamcity[setParameter name='external.validation.failed.samples' value='${'$'}FAILED_SAMPLES']"
            echo "##teamcity[setParameter name='external.validation.skipped.samples' value='${'$'}SKIPPED_SAMPLES']"
            echo "##teamcity[setParameter name='external.validation.success.rate' value='${'$'}SUCCESS_RATE']"

            # Persist this OS's counts for the aggregator build to sum.
            mkdir -p os-results
            cat > "os-results/${'$'}{EAP_TARGET_OS}-external.properties" <<EOF
external_total=${'$'}TOTAL_SAMPLES
external_successful=${'$'}SUCCESSFUL_SAMPLES
external_failed=${'$'}FAILED_SAMPLES
external_skipped=${'$'}SKIPPED_SAMPLES
EOF
            echo "Wrote os-results/${'$'}{EAP_TARGET_OS}-external.properties"

            echo "=== Step 2: External Samples Validation Completed ==="
        """.trimIndent()
        }
    }
}
