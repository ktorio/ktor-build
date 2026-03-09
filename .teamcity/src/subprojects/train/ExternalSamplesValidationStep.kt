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
            """.trimIndent()
        }

        steps.script {
            name = "Step 2: External Samples Validation"
            scriptContent = """
            #!/bin/bash
            
            echo "=== Step 2: External Samples Validation ==="
            
            # Get current parameter values or use fallback defaults
            KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
            KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.1.21/' || echo "2.1.21")
            
            echo "Validating external GitHub samples against EAP versions"
            echo "Ktor Version: ${'$'}KTOR_VERSION"
            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

            WORK_DIR=$(pwd)
            REPORTS_DIR="${'$'}WORK_DIR/external-validation-reports"
            SAMPLES_DIR="${'$'}WORK_DIR/external-samples"

            # Create necessary directories
            mkdir -p "${'$'}REPORTS_DIR"
            mkdir -p "${'$'}SAMPLES_DIR"

                # Define external sample repositories
                declare -A EXTERNAL_SAMPLES=(
                    ["ktor-ai-server"]="https://github.com/nomisRev/ktor-ai-server.git"
                    ["ktor-native-server"]="https://github.com/nomisRev/ktor-native-server.git"
                    ["ktor-config-example"]="https://github.com/nomisRev/ktor-config-example.git"
                    ["ktor-workshop-2025"]="https://github.com/nomisRev/ktor-workshop-2025.git"
                    ["amper-ktor-sample"]="https://github.com/nomisRev/amper-ktor-sample.git"
                    ["ktor-di-overview"]="https://github.com/nomisRev/Ktor-DI-Overview.git"
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
                "${'$'}SAMPLES_DIR/ktor-di-overview"
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
org.gradle.jvmargs=-Xmx2g
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
                    echo "Applied EAP versions to gradle.properties"
                fi
                
                # Special handling for Amper projects
                if [ -f "module.yaml" ] || [ -d ".amper" ]; then
                    echo "Amper project detected. Updating versions in module.yaml or equivalent..."
                    # Simple sed replacement for versions if they exist in common locations
                    find . -name "*.yaml" -type f -exec sed -i "s/ktor: .*/ktor: ${'$'}KTOR_VERSION/g" {} +
                    find . -name "*.yaml" -type f -exec sed -i "s/kotlin: .*/kotlin: ${'$'}KOTLIN_VERSION/g" {} +
                fi

                # Run validation (build/test)
                BUILD_SUCCESS=false
                if [ -f "gradlew" ]; then
                    chmod +x gradlew
                    echo "Running: ./gradlew build --no-daemon"
                    if ./gradlew build --no-daemon > "${'$'}REPORTS_DIR/${'$'}project_name-build.log" 2>&1; then
                        BUILD_SUCCESS=true
                    fi
                elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
                    echo "Running: gradle build --no-daemon"
                    if gradle build --no-daemon > "${'$'}REPORTS_DIR/${'$'}project_name-build.log" 2>&1; then
                        BUILD_SUCCESS=true
                    fi
                else
                    echo "⚠️  No Gradle wrapper or build file found in ${'$'}project_name"
                    SKIPPED_SAMPLES=$((SKIPPED_SAMPLES + 1))
                    cd "${'$'}WORK_DIR"
                    continue
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
            if [ "${'$'}TOTAL_SAMPLES" -gt 0 ]; then
                SUCCESS_RATE=$((SUCCESSFUL_SAMPLES * 100 / TOTAL_SAMPLES))
            fi

            # Report results to TeamCity
            echo "##teamcity[setParameter name='external.validation.total.samples' value='${'$'}TOTAL_SAMPLES']"
            echo "##teamcity[setParameter name='external.validation.successful.samples' value='${'$'}SUCCESSFUL_SAMPLES']"
            echo "##teamcity[setParameter name='external.validation.failed.samples' value='${'$'}FAILED_SAMPLES']"
            echo "##teamcity[setParameter name='external.validation.skipped.samples' value='${'$'}SKIPPED_SAMPLES']"
            echo "##teamcity[setParameter name='external.validation.success.rate' value='${'$'}SUCCESS_RATE.0']"
            
            echo "=== Step 2: External Samples Validation Completed ==="
        """.trimIndent()
        }
    }
}
