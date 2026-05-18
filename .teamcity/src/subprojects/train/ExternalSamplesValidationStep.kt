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
    fun apply(steps: BuildSteps) {
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

        steps.script {
            name = "Step 2: External Samples Validation"
            scriptContent = """
            #!/bin/bash
            
            echo "=== Step 2: External Samples Validation ==="
            
            # Get current parameter values or use fallback defaults
            KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
            KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.3.10/' || echo "2.3.10")
            
            # Option to fallback to compile on failure (enabled by default)
            TRY_COMPILE_ON_FAILURE=$(echo "%env.TRY_COMPILE_ON_FAILURE%" | sed 's/^%env\.TRY_COMPILE_ON_FAILURE%$/true/' || echo "true")
            
            echo "Validating external GitHub samples against EAP versions"
            echo "Ktor Version: ${'$'}KTOR_VERSION"
            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"
            echo "Try Compile on Failure: ${'$'}TRY_COMPILE_ON_FAILURE"

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
            cat > "${'$'}HOME/.m2/settings.xml" <<'SETTINGS_EOF'
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
</settings>
SETTINGS_EOF
            echo "Wrote ~/.m2/settings.xml with JetBrains cache-redirector mirror"

            mkdir -p "${'$'}HOME/.gradle/init.d"
            cat > "${'$'}HOME/.gradle/init.d/cache-redirector.gradle" <<'INIT_EOF'
def cacheRedirector = "https://cache-redirector.jetbrains.com"

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
    settings.pluginManagement.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/ktor-eap") }
    settings.pluginManagement.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/dev") }
    settings.pluginManagement.repositories.gradlePluginPortal()
    settings.pluginManagement.repositories.mavenCentral()

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
        settings.dependencyResolutionManagement.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/ktor-eap") }
        settings.dependencyResolutionManagement.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/dev") }
    }
}

allprojects {
    buildscript.repositories.all(replaceUrl)
    repositories.all(replaceUrl)
    buildscript.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/ktor-eap") }
    buildscript.repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/dev") }
    buildscript.repositories.maven { url = uri("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/") }
    repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/ktor-eap") }
    repositories.maven { url = uri("https://redirector.kotlinlang.org/maven/dev") }
    repositories.maven { url = uri("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/") }
    repositories.maven { url = uri("https://cache-redirector.jetbrains.com/maven.google.com/") }

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

            TOTAL_SAMPLES=${'$'}{#EXTERNAL_SAMPLE_DIRS[@]}
            SUCCESSFUL_SAMPLES=0
            FAILED_SAMPLES=0
            SKIPPED_SAMPLES=0

            echo "Starting validation of ${'$'}TOTAL_SAMPLES projects..."

            for project_dir in "${'$'}{EXTERNAL_SAMPLE_DIRS[@]}"; do
                project_name=$(basename "${'$'}project_dir")
                echo "---------------------------------------------------"
                echo "Validating ${'$'}project_name..."
                
                if [ ! -d "${'$'}project_dir" ]; then
                    echo "⚠️  Project directory not found: ${'$'}project_dir"
                    SKIPPED_SAMPLES=$((SKIPPED_SAMPLES + 1))
                    continue
                fi
                
                cd "${'$'}project_dir"
                
                # Apply EAP versions
                if [ -f "gradle.properties" ]; then
                    cp "${'$'}WORK_DIR/gradle.properties.eap" gradle.properties
                    
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
                    sed -i -E "s@^([[:space:]]*kotlin[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}KOTLIN_VERSION\2@g" "${'$'}toml"
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
                    find . -name "*.yaml" -type f -exec sed -i "s/kotlin: .*/kotlin: ${'$'}KOTLIN_VERSION/g" {} +
                    find . -name "*.yaml" -type f -exec sed -i 's|\${'$'}kotlin-test-junit|\${'$'}libs.kotlin.test|g' {} +

                    REPOS_TO_INJECT="$(printf '  - %s\n' \
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
                    echo "Last 20 lines of build log:"
                    tail -n 20 "${'$'}REPORTS_DIR/${'$'}project_name-build.log"
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
            
            echo "=== Step 2: External Samples Validation Completed ==="
        """.trimIndent()
        }
    }
}
