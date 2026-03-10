package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import dsl.*
import subprojects.*

/**
 * Step 3: Internal Test Suites
 * Validates internal Ktor samples against the EAP versions
 * Runs tests and processes results regardless of failures
 */
object InternalTestSuitesStep {
    fun apply(steps: BuildSteps) {
        steps.script {
            name = "Prerequisites: Accept Android SDK licenses"
            scriptContent = "yes | JAVA_HOME=${Env.JDK_LTS} %env.ANDROID_SDKMANAGER_PATH% --licenses"
        }

        steps.script {
            name = "Prerequisites: Warm up Docker images"
            scriptContent = """
                #!/bin/bash
                echo "Pulling common Docker images used by internal samples..."
                docker pull mongo:6.0 || true
                docker pull mongodb/mongodb-community-server:8.2-ubi8 || true
                docker pull jaegertracing/all-in-one:latest || true
                docker pull postgres:18.0-alpine || true
            """.trimIndent()
        }

        steps.script {
            name = "Prerequisites: Install pulseaudio for WebRTC tests"
            scriptFile("install_pulseaudio.sh")
        }

        steps.script {
            name = "Step 3: Internal Test Suites - Setup EAP Environment"
            scriptContent = """
            #!/bin/bash
            
            echo "=== Step 3: Internal Test Suites - EAP Sample Validation ==="
            echo "Setting up EAP environment for internal sample validation"

            # Get current parameter values or use fallback defaults
            KTOR_VERSION=$(echo "%env.KTOR_VERSION%" | sed 's/^%env\.KTOR_VERSION%$//' || echo "")
            KTOR_COMPILER_PLUGIN_VERSION=$(echo "%env.KTOR_COMPILER_PLUGIN_VERSION%" | sed 's/^%env\.KTOR_COMPILER_PLUGIN_VERSION%$//' || echo "")
            KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.1.21/' || echo "2.1.21")

            echo "Ktor Version: ${'$'}KTOR_VERSION"
            echo "Ktor Compiler Plugin Version: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

            # Validate Kotlin version format and fix if needed
            if [[ "${'$'}KOTLIN_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-[0-9]+$ ]]; then
                echo "⚠️  Invalid Kotlin version format: ${'$'}KOTLIN_VERSION (looks like build number)"
                # Extract base version (e.g., 2.1.22 from 2.1.22-332)
                KOTLIN_VERSION=$(echo "${'$'}KOTLIN_VERSION" | sed 's/-[0-9]*$//')
                echo "🔧 Using corrected Kotlin version: ${'$'}KOTLIN_VERSION"
            fi

            # Create reports directory with absolute path
            REPORTS_DIR="${'$'}PWD/internal-validation-reports"
            mkdir -p "${'$'}REPORTS_DIR"
            
            # Store the absolute path in environment
            echo "REPORTS_DIR=${'$'}REPORTS_DIR" > build-env.properties
            echo "KOTLIN_VERSION=${'$'}KOTLIN_VERSION" >> build-env.properties

            # Create EAP Gradle init script with correct Groovy syntax
            echo "Creating EAP Gradle init script..."
            mkdir -p samples
            cat > samples/gradle-eap-init.gradle <<EOF
beforeSettings { settings ->
    settings.pluginManagement {
        repositories {
            maven {
                url "https://redirector.kotlinlang.org/maven/ktor-eap"
            }
            maven {
                url "https://redirector.kotlinlang.org/maven/compose-dev"
            }
            maven {
                url "https://redirector.kotlinlang.org/maven/dev"
            }
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
settingsEvaluated { settings ->
    settings.dependencyResolutionManagement {
        repositories {
            maven {
                url "https://redirector.kotlinlang.org/maven/ktor-eap"
            }
            maven {
                url "https://redirector.kotlinlang.org/maven/compose-dev"
            }
            maven {
                url "https://redirector.kotlinlang.org/maven/dev"
            }
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
EOF

            # Create Maven settings with EAP repositories
            echo "Creating Maven settings for EAP repositories..."
            mkdir -p ~/.m2
            cat > ~/.m2/settings.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <profiles>
        <profile>
            <id>ktor-eap</id>
            <repositories>
                <repository>
                    <id>ktor-eap-repo</id>
                    <url>https://redirector.kotlinlang.org/maven/ktor-eap</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </repository>
                <repository>
                    <id>compose-dev-repo</id>
                    <url>https://redirector.kotlinlang.org/maven/compose-dev</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </repository>
                <repository>
                    <id>kotlin-eap-repo</id>
                    <url>https://redirector.kotlinlang.org/maven/dev</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>ktor-eap-plugins</id>
                    <url>https://redirector.kotlinlang.org/maven/ktor-eap</url>
                </pluginRepository>
                <pluginRepository>
                    <id>kotlin-eap-plugins</id>
                    <url>https://redirector.kotlinlang.org/maven/dev</url>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>
    <activeProfiles>
        <activeProfile>ktor-eap</activeProfile>
    </activeProfiles>
</settings>
EOF
            
            # Setup Gradle properties for EAP
            cat > samples/gradle.properties.eap <<EOF
ktor_version=${'$'}KTOR_VERSION
kotlin_version=${'$'}KOTLIN_VERSION
ktor_compiler_plugin_version=${'$'}KTOR_COMPILER_PLUGIN_VERSION
logback_version=1.4.14
kotlin.mpp.stability.nowarn=true
org.gradle.jvmargs=-Xmx4g
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.caching=false
EOF
            
            echo "Setup completed successfully"
        """.trimIndent()
        }

        steps.script {
            name = "Step 3: Internal Test Suites - Run Samples"
            scriptContent = """
            #!/bin/bash
        
            echo "=== Step 3: Internal Test Suites - Run Samples ==="
        
            # Source EAP environment variables
            if [ -f build-env.properties ]; then
                source build-env.properties
            else
                echo "❌ Environment file not found - setup step failed"
                exit 1
            fi
        
            echo "Validating internal Ktor samples against EAP versions"
            echo "Ktor Version: %env.KTOR_VERSION%"
            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

            # Create absolute path for init script
            WORK_DIR=$(pwd)
            INIT_SCRIPT="${'$'}WORK_DIR/samples/gradle-eap-init.gradle"
            PROPERTIES_EAP="${'$'}WORK_DIR/samples/gradle.properties.eap"

            cd samples
        
            # Create a summary report file
            REPORT_FILE="${'$'}REPORTS_DIR/samples-summary.txt"
            echo "Ktor EAP Sample Validation Report" > "${'$'}REPORT_FILE"
            echo "Timestamp: $(date)" >> "${'$'}REPORT_FILE"
            echo "Ktor Version: %env.KTOR_VERSION%" >> "${'$'}REPORT_FILE"
            echo "-----------------------------------" >> "${'$'}REPORT_FILE"

            TOTAL_COUNT=0
            SUCCESSFUL_COUNT=0
            FAILED_COUNT=0
            SKIPPED_COUNT=0

            IGNORE_REGEX='^(\.git|\.github|\.idea|\.gradle|gradle|buildSrc|docs?|scripts?|out|build|tmp|node_modules)$'

            is_buildable_dir() {
                local dir="${'$'}1"
                [ -d "${'$'}dir" ] || return 1
                [ -f "${'$'}dir/pom.xml" ] && return 0
                [ -f "${'$'}dir/build.gradle.kts" ] && return 0
                [ -f "${'$'}dir/build.gradle" ] && return 0
                [ -f "${'$'}dir/settings.gradle.kts" ] && return 0
                [ -f "${'$'}dir/settings.gradle" ] && return 0
                return 1
            }

            SAMPLE_PROJECTS=()
            for d in ./*; do
                [ -d "${'$'}d" ] || continue
                sample_name="$(basename "${'$'}d")"

                if [[ "${'$'}sample_name" =~ ${'$'}IGNORE_REGEX ]]; then
                    continue
                fi

                if is_buildable_dir "${'$'}d"; then
                    SAMPLE_PROJECTS+=("${'$'}sample_name")
                fi
            done

                if [ ${'$'}{'$'}{#SAMPLE_PROJECTS[@]} -eq 0 ]; then
                    echo "⚠️  No buildable sample directories discovered"
                    echo "No buildable sample directories discovered" >> "${'$'}{'$'}REPORT_FILE"
                else
                    echo "Discovered ${'$'}{'$'}{#SAMPLE_PROJECTS[@]} buildable samples:"
                    printf '  - %s\n' "${'$'}{'$'}{SAMPLE_PROJECTS[@]}" | sort
                fi

            for sample_dir in "${'$'}{SAMPLE_PROJECTS[@]}"; do
                [ -d "${'$'}sample_dir" ] || continue

                TOTAL_COUNT=$((TOTAL_COUNT + 1))
                echo "Validating sample: ${'$'}sample_dir..."

                # Maven sample
                if [ -f "${'$'}sample_dir/pom.xml" ]; then
                    echo "Maven sample detected. Running: mvn test -B"
                    if mvn -f "${'$'}sample_dir/pom.xml" test -B -Dktor.version=%env.KTOR_VERSION% -Dkotlin.version=${'$'}KOTLIN_VERSION > "${'$'}REPORTS_DIR/${'$'}sample_dir.log" 2>&1; then
                        echo "✅ ${'$'}sample_dir: SUCCESS"
                        SUCCESSFUL_COUNT=$((SUCCESSFUL_COUNT + 1))
                        echo "${'$'}sample_dir: PASSED (Maven)" >> "${'$'}REPORT_FILE"
                    else
                        echo "❌ ${'$'}sample_dir: FAILED"
                        FAILED_COUNT=$((FAILED_COUNT + 1))
                        echo "${'$'}sample_dir: FAILED (Maven)" >> "${'$'}REPORT_FILE"
                        tail -n 10 "${'$'}REPORTS_DIR/${'$'}sample_dir.log" || true
                    fi
                    continue
                fi

                # Gradle sample (buildable marker present)
                if [ -f "${'$'}sample_dir/gradle.properties" ]; then
                    cp "${'$'}PROPERTIES_EAP" "${'$'}sample_dir/gradle.properties"
                fi

                if [ -x "./gradlew" ]; then
                    echo "Running: ./gradlew -p ${'$'}sample_dir test --init-script ${'$'}INIT_SCRIPT --no-daemon"
                    if ./gradlew -p "${'$'}sample_dir" test --init-script "${'$'}INIT_SCRIPT" --no-daemon > "${'$'}REPORTS_DIR/${'$'}sample_dir.log" 2>&1; then
                        echo "✅ ${'$'}sample_dir: SUCCESS"
                        SUCCESSFUL_COUNT=$((SUCCESSFUL_COUNT + 1))
                        echo "${'$'}sample_dir: PASSED (Gradle)" >> "${'$'}REPORT_FILE"
                    else
                        echo "❌ ${'$'}sample_dir: FAILED"
                        FAILED_COUNT=$((FAILED_COUNT + 1))
                        echo "${'$'}sample_dir: FAILED (Gradle)" >> "${'$'}REPORT_FILE"
                        tail -n 10 "${'$'}REPORTS_DIR/${'$'}sample_dir.log" || true
                    fi
                elif [ -x "${'$'}sample_dir/gradlew" ]; then
                    chmod +x "${'$'}sample_dir/gradlew"
                    echo "Running: ${'$'}sample_dir/gradlew test --init-script ${'$'}INIT_SCRIPT --no-daemon"
                    if "${'$'}sample_dir/gradlew" test --init-script "${'$'}INIT_SCRIPT" --no-daemon > "${'$'}REPORTS_DIR/${'$'}sample_dir.log" 2>&1; then
                        echo "✅ ${'$'}sample_dir: SUCCESS"
                        SUCCESSFUL_COUNT=$((SUCCESSFUL_COUNT + 1))
                        echo "${'$'}sample_dir: PASSED (Gradle wrapper)" >> "${'$'}REPORT_FILE"
                    else
                        echo "❌ ${'$'}sample_dir: FAILED"
                        FAILED_COUNT=$((FAILED_COUNT + 1))
                        echo "${'$'}sample_dir: FAILED (Gradle wrapper)" >> "${'$'}REPORT_FILE"
                        tail -n 10 "${'$'}REPORTS_DIR/${'$'}sample_dir.log" || true
                    fi
                else
                    echo "⚠️  No executable Gradle wrapper found for ${'$'}sample_dir"
                    SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
                    echo "${'$'}sample_dir: SKIPPED (No executable Gradle wrapper)" >> "${'$'}REPORT_FILE"
                fi
            done

            echo "-----------------------------------"
            echo "Internal Validation Summary:"
            echo "Total: ${'$'}TOTAL_COUNT"
            echo "Successful: ${'$'}SUCCESSFUL_COUNT"
            echo "Failed: ${'$'}FAILED_COUNT"
            echo "Skipped: ${'$'}SKIPPED_COUNT"

            # Calculate success rate
            SUCCESS_RATE=0
            if [ "${'$'}TOTAL_COUNT" -gt 0 ]; then
                SUCCESS_RATE=$((SUCCESSFUL_COUNT * 100 / TOTAL_COUNT))
            fi

            # Report results to TeamCity
            echo "##teamcity[setParameter name='internal.validation.total.tests' value='${'$'}TOTAL_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.passed.tests' value='${'$'}SUCCESSFUL_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.failed.tests' value='${'$'}FAILED_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.error.tests' value='0']"
            echo "##teamcity[setParameter name='internal.validation.skipped.tests' value='${'$'}SKIPPED_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.success.rate' value='${'$'}SUCCESS_RATE.0']"
        """.trimIndent()
        }
    }
}
