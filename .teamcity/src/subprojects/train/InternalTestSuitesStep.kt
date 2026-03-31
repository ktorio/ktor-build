package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import dsl.*
import subprojects.*
import subprojects.build.*

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
            KOTLIN_VERSION=$(echo "%env.KOTLIN_VERSION%" | sed 's/^%env\.KOTLIN_VERSION%$/2.3.10/' || echo "2.3.10")

            echo "Ktor Version: ${'$'}KTOR_VERSION"
            echo "Ktor Compiler Plugin Version: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

            # Validate Kotlin version format and fix if needed
            if [[ "${'$'}KOTLIN_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-[0-9]+$ ]]; then
                echo "⚠️  Invalid Kotlin version format: ${'$'}KOTLIN_VERSION (looks like build number)"
                KOTLIN_VERSION=$(echo "${'$'}KOTLIN_VERSION" | sed 's/-[0-9]*$//')
                echo "🔧 Using corrected Kotlin version: ${'$'}KOTLIN_VERSION"
            fi

            # Create reports directory with absolute path
            REPORTS_DIR="${'$'}PWD/internal-validation-reports"
            mkdir -p "${'$'}REPORTS_DIR"
            
            JDK_VERSION="${JDKEntry.JavaLTS.version}"

            # Store the absolute path in environment
            echo "REPORTS_DIR=\"${'$'}REPORTS_DIR\"" > build-env.properties
            echo "KOTLIN_VERSION=\"${'$'}KOTLIN_VERSION\"" >> build-env.properties
            echo "JDK_VERSION=\"${'$'}JDK_VERSION\"" >> build-env.properties

            # Create EAP Gradle init script with correct Groovy syntax
            echo "Creating EAP Gradle init script..."
            mkdir -p samples
            if [ ! -d "samples/.git" ]; then
                echo "Cloning Ktor Samples repository..."
                git clone https://github.com/ktorio/ktor-samples.git samples --depth 1
            fi
            cat > samples/gradle-eap-init.gradle <<EOF
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.resolve.RepositoriesMode
import java.net.URL
import java.net.HttpURLConnection

beforeSettings { settings ->
    settings.pluginManagement {
        resolutionStrategy {
            eachPlugin {
                if (requested.id.id == "io.ktor.plugin") {
                    def v = System.getProperty("ktor_version")
                    if (v != null) {
                        try {
                            def urlStr = "https://redirector.kotlinlang.org/maven/ktor-eap/io/ktor/plugin/io.ktor.plugin.gradle.plugin/" + v + "/io.ktor.plugin.gradle.plugin-" + v + ".pom"
                            def url = new URL(urlStr)
                            def conn = (HttpURLConnection) url.openConnection()
                            conn.requestMethod = "HEAD"
                            if (conn.responseCode == 200 || conn.responseCode == 301 || conn.responseCode == 302 || conn.responseCode == 307) {
                                useVersion(v)
                            } else {
                                println("⚠️ Ktor plugin version " + v + " not found in EAP repo (HTTP " + conn.responseCode + "), using project version")
                            }
                        } catch (Exception e) {
                            println("⚠️ Failed to check Ktor plugin version " + v + " availability: " + e.message)
                        }
                    }
                }
                if (requested.id.id.startsWith("org.jetbrains.kotlin.")) {
                    def v = System.getProperty("kotlin_version")
                    if (v != null && requested.version == null) {
                        useVersion(v)
                    }
                }
            }
        }
        repositories {
            maven {
                url = "https://redirector.kotlinlang.org/maven/ktor-eap"
            }
            maven {
                url = "https://redirector.kotlinlang.org/maven/dev"
            }
            maven {
                url = "https://redirector.kotlinlang.org/maven/compose-dev"
            }
            maven {
                url = "https://packages.jetbrains.team/maven/p/kt/wasm-experimental/"
            }
            mavenCentral()
            gradlePluginPortal()
            google()
        }
    }
}

settingsEvaluated { settings ->
    settings.dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
        repositories {
            maven {
                url = "https://redirector.kotlinlang.org/maven/ktor-eap"
            }
            maven {
                url = "https://redirector.kotlinlang.org/maven/dev"
            }
            maven {
                url = "https://redirector.kotlinlang.org/maven/compose-dev"
            }
            maven {
                url = "https://packages.jetbrains.team/maven/p/kt/wasm-experimental/"
            }
            maven {
                url = "https://maven.google.com/"
            }
            maven {
                url = "https://plugins.gradle.org/m2/"
            }
            mavenCentral()
            google()
        }
    }
}

allprojects {
    repositories {
        maven {
            url = "https://redirector.kotlinlang.org/maven/ktor-eap"
        }
        maven {
            url = "https://redirector.kotlinlang.org/maven/dev"
        }
        maven {
            url = "https://redirector.kotlinlang.org/maven/compose-dev"
        }
        maven {
            url = "https://packages.jetbrains.team/maven/p/kt/wasm-experimental/"
        }
        mavenCentral()
        google()
    }
    configurations.all {
        resolutionStrategy.eachDependency { details ->
            if (details.requested.group == "io.ktor") {
                def v = System.getProperty("ktor_version")
                if (v != null) details.useVersion(v)
            }
        }
    }
    afterProject { p ->
        p.extensions.extraProperties.set("mainClassName", "io.ktor.server.netty.EngineMain")
        def jdkVersion = System.getProperty("jdk_version")
        if (jdkVersion != null) {
            def v = Integer.parseInt(jdkVersion)
            p.plugins.withId("org.jetbrains.kotlin.jvm") {
                def kotlin = p.extensions.findByName("kotlin")
                if (kotlin != null && kotlin.hasProperty("jvmToolchain")) {
                    kotlin.jvmToolchain(v)
                }
            }
            if (p.hasProperty("java") && p.java.hasProperty("toolchain")) {
                p.java.toolchain.languageVersion = JavaLanguageVersion.of(v)
            }
            p.tasks.withType(JavaCompile).configureEach {
                it.javaCompiler = p.javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(v)
                }
            }
        }
        p.afterEvaluate {
            p.tasks.matching { it.name == "shadowJar" }.configureEach {
                try {
                    def mainClassAttr = it.manifest.attributes.get("Main-Class")
                    if (mainClassAttr == null) {
                        def application = p.extensions.findByType(org.gradle.api.plugins.JavaApplication)
                        if (application != null && application.mainClass.isPresent()) {
                            it.manifest.attributes("Main-Class": application.mainClass.get())
                        } else {
                            it.enabled = false
                        }
                    }
                } catch (Exception ignored) {
                    it.enabled = false
                }
            }
        }
        p.tasks.all { task ->
            if (task.name == "startShadowScripts") {
                task.enabled = false
                try {
                    task.extensions.add("mainClassName", "ignored")
                } catch (Exception ignored) {}
            }
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
jdk_version=${'$'}JDK_VERSION
ktor_version=${'$'}KTOR_VERSION
kotlin_version=${'$'}KOTLIN_VERSION
ktor_compiler_plugin_version=${'$'}KTOR_COMPILER_PLUGIN_VERSION
kotlin.mpp.stability.nowarn=true
org.gradle.jvmargs=-Xmx4g
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.caching=false
org.gradle.warning.mode=all
org.gradle.java.installations.auto-download=true
org.gradle.java.installations.auto-detect=true
org.gradle.java.installations.fromEnv=true
org.gradle.java.installations.paths=${Env.JDK_LTS}
systemProp.org.gradle.java.installations.auto-download=true
systemProp.org.gradle.java.installations.auto-detect=true
EOF

            GRADLE_ARGS=""
            SYSTEM_PROPS=""
            while IFS= read -r line; do
                if [[ -n "${'$'}line" && ! "${'$'}line" =~ ^# ]]; then
                    GRADLE_ARGS="${'$'}GRADLE_ARGS -P${'$'}line"
                    # Add as system property as well for init script access
                    SYSTEM_PROPS="${'$'}SYSTEM_PROPS -D${'$'}line"
                    
                    key=$(echo "${'$'}line" | cut -d'=' -f1)
                    value=$(echo "${'$'}line" | cut -d'=' -f2)
                    export "${'$'}key"="${'$'}value"
                fi
            done < samples/gradle.properties.eap
            
            echo "GRADLE_ARGS=\"${'$'}GRADLE_ARGS\"" >> build-env.properties
            echo "SYSTEM_PROPS=\"${'$'}SYSTEM_PROPS\"" >> build-env.properties
            
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
            echo "Ktor Plugin Version: %env.KTOR_COMPILER_PLUGIN_VERSION%"
            echo "Kotlin Version: ${'$'}KOTLIN_VERSION"

            # Create absolute path for init script
            WORK_DIR=$(pwd)
            INIT_SCRIPT="${'$'}WORK_DIR/samples/gradle-eap-init.gradle"
            SAMPLES_ROOT="${'$'}WORK_DIR/samples"

            cd "${'$'}SAMPLES_ROOT"
        
            # Create a summary report file
            REPORT_FILE="${'$'}REPORTS_DIR/samples-summary.txt"
            echo "Ktor EAP Sample Validation Report" > "${'$'}REPORT_FILE"
            echo "Timestamp: $(date)" >> "${'$'}REPORT_FILE"
            echo "Ktor Version: %env.KTOR_VERSION%" >> "${'$'}REPORT_FILE"
            echo "Ktor Plugin Version: %env.KTOR_COMPILER_PLUGIN_VERSION%" >> "${'$'}REPORT_FILE"
            echo "-----------------------------------" >> "${'$'}REPORT_FILE"

            TOTAL_COUNT=0
            SUCCESSFUL_COUNT=0
            FAILED_COUNT=0
            SKIPPED_COUNT=0

            IGNORE_REGEX='^(\.git|\.github|\.idea|\.gradle|gradle|buildSrc|docs?|scripts?|out|build|tmp|node_modules)$'

            is_stable_kotlin_version() {
                local ver="${'$'}1"
                if [[ "${'$'}ver" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
                    return 0
                fi
                return 1
            }

           patch_maven_pom() {
                local pom_file="${'$'}1"
                local ktor_ver="${'$'}2"
                local kotlin_ver="${'$'}3"

                [ -f "${'$'}pom_file" ] || return 0

                echo "  [patcher] Patching Maven POM: ${'$'}pom_file"
                local before_hash
                before_hash=$(md5sum "${'$'}pom_file" | awk '{print $1}')

                local patch_kotlin=false
                if is_stable_kotlin_version "${'$'}kotlin_ver"; then
                    patch_kotlin=true
                else
                    if grep -q "kotlin-maven-plugin" "${'$'}pom_file"; then
                        echo "  [patcher]   ⏭️ Skipping Kotlin version patch in Maven POM (dev version: ${'$'}kotlin_ver, POM uses kotlin-maven-plugin)"
                    else
                        patch_kotlin=true
                    fi
                fi

                # --- 1. Rewrite io.ktor BOM version in dependencyManagement ---
                perl -0777 -i -pe '
                    s{
                        (<dependency>\s*
                            <groupId>io\.ktor</groupId>\s*
                            <artifactId>ktor-bom</artifactId>\s*
                            <version>)[^<]+(</version>)
                    }{${'$'}{1}'"${'$'}ktor_ver"'${'$'}{2}}gsx;
                ' "${'$'}pom_file"

                # --- 2. Rewrite any explicit io.ktor dependency versions ---
                perl -0777 -i -pe '
                    s{
                        (<dependency>\s*
                            <groupId>io\.ktor</groupId>\s*
                            <artifactId>[^<]+</artifactId>\s*
                            <version>)[^<]+(</version>)
                    }{${'$'}{1}'"${'$'}ktor_ver"'${'$'}{2}}gsx;
                ' "${'$'}pom_file"

                # --- 3. Rewrite io.ktor plugin versions in <build><plugins> ---
                perl -0777 -i -pe '
                    s{
                        (<plugin>\s*
                            <groupId>io\.ktor</groupId>\s*
                            <artifactId>[^<]+</artifactId>\s*
                            <version>)[^<]+(</version>)
                    }{${'$'}{1}'"${'$'}ktor_ver"'${'$'}{2}}gsx;
                ' "${'$'}pom_file"

                # --- 4. Rewrite kotlin-maven-plugin version (only if safe) ---
                if [ "${'$'}patch_kotlin" = true ]; then
                    perl -0777 -i -pe '
                        s{
                            (<(?:plugin|dependency)>\s*
                                (?:<groupId>org\.jetbrains\.kotlin</groupId>\s*)?
                                <artifactId>kotlin-maven-plugin</artifactId>\s*
                                (?:<groupId>org\.jetbrains\.kotlin</groupId>\s*)?
                                <version>)[^<]+(</version>)
                        }{${'$'}{1}'"${'$'}kotlin_ver"'${'$'}{2}}gsx;
                    ' "${'$'}pom_file"
                fi

                # --- 5. Rewrite <properties> entries for ktor versions (always) ---
                sed -i -E "s@(<ktor\.version>)[^<]+(</ktor\.version>)@\1${'$'}ktor_ver\2@g" "${'$'}pom_file"
                sed -i -E "s@(<ktor_version>)[^<]+(</ktor_version>)@\1${'$'}ktor_ver\2@g" "${'$'}pom_file"

                if [ "${'$'}patch_kotlin" = true ]; then
                    sed -i -E "s@(<kotlin\.version>)[^<]+(</kotlin\.version>)@\1${'$'}kotlin_ver\2@g" "${'$'}pom_file"
                    sed -i -E "s@(<kotlin_version>)[^<]+(</kotlin_version>)@\1${'$'}kotlin_ver\2@g" "${'$'}pom_file"
                fi

                # --- 6. Inject EAP repository if not already present ---
                if ! grep -q "ktor-eap" "${'$'}pom_file"; then
                    if grep -q "<repositories>" "${'$'}pom_file"; then
                        sed -i '/<repositories>/a \
        <repository>\
            <id>ktor-eap-patched</id>\
            <url>https://redirector.kotlinlang.org/maven/ktor-eap</url>\
            <releases><enabled>true</enabled></releases>\
            <snapshots><enabled>true</enabled></snapshots>\
        </repository>' "${'$'}pom_file"
                    else
                        sed -i '/<\/project>/i \
    <repositories>\
        <repository>\
            <id>ktor-eap-patched</id>\
            <url>https://redirector.kotlinlang.org/maven/ktor-eap</url>\
            <releases><enabled>true</enabled></releases>\
            <snapshots><enabled>true</enabled></snapshots>\
        </repository>\
        <repository>\
            <id>kotlin-eap-patched</id>\
            <url>https://redirector.kotlinlang.org/maven/dev</url>\
            <releases><enabled>true</enabled></releases>\
            <snapshots><enabled>true</enabled></snapshots>\
        </repository>\
    </repositories>' "${'$'}pom_file"
                    fi
                    echo "  [patcher]   Injected EAP repositories"
                fi

                local after_hash
                after_hash=$(md5sum "${'$'}pom_file" | awk '{print $1}')
                if [ "${'$'}before_hash" != "${'$'}after_hash" ]; then
                    echo "  [patcher]   ✅ Patched ${'$'}pom_file"
                else
                    echo "  [patcher]   ℹ️  No changes needed in ${'$'}pom_file"
                fi
            }

            patch_gradle_file() {
                local gradle_file="${'$'}1"
                local ktor_ver="${'$'}2"
                local kotlin_ver="${'$'}3"

                [ -f "${'$'}gradle_file" ] || return 0

                echo "  [patcher] Patching Gradle file: ${'$'}gradle_file"
                local before_hash
                before_hash=$(md5sum "${'$'}gradle_file" | awk '{print $1}')

                # Rewrite "io.ktor:ARTIFACT:VERSION" -> "io.ktor:ARTIFACT:KTOR_VERSION"
                sed -i -E "s@([\"'])io\.ktor:([a-zA-Z0-9_-]+):[^\"']+\1@\1io.ktor:\2:${'$'}ktor_ver\1@g" "${'$'}gradle_file"

                # Rewrite id("io.ktor.plugin") version "VERSION"
                sed -i -E "s@(id[[:space:]]*[(]?[\"']io\.ktor\.plugin[\"'][)]?[[:space:]]+version[[:space:]]+[\"'])[^\"']+([\"'])@\1${'$'}ktor_ver\2@g" "${'$'}gradle_file"

                # Rewrite val ktor_version = "VERSION" / val ktorVersion = "VERSION"
                sed -i -E "s@(val[[:space:]]+(ktor_version|ktorVersion|KTOR_VERSION)[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}ktor_ver\3@g" "${'$'}gradle_file"

                # Rewrite def ktor_version = "VERSION" / ext.ktor_version = "VERSION" (Groovy)
                sed -i -E "s@((def|ext\.)[[:space:]]*(ktor_version|ktorVersion)[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}ktor_ver\4@g" "${'$'}gradle_file"

                # Rewrite kotlin plugin versions: kotlin("jvm") version "VERSION"
                sed -i -E "s@(kotlin[(][[:space:]]*[\"'][^\"']+[\"'][[:space:]]*[)][[:space:]]+version[[:space:]]+[\"'])[^\"']+([\"'])@\1${'$'}kotlin_ver\2@g" "${'$'}gradle_file"

                local after_hash
                after_hash=$(md5sum "${'$'}gradle_file" | awk '{print $1}')
                if [ "${'$'}before_hash" != "${'$'}after_hash" ]; then
                    echo "  [patcher]   ✅ Patched ${'$'}gradle_file"
                else
                    echo "  [patcher]   ℹ️  No changes needed in $(basename ${'$'}gradle_file)"
                fi
            }

            patch_version_catalog() {
                local toml_file="${'$'}1"
                local ktor_ver="${'$'}2"
                local kotlin_ver="${'$'}3"
                local ktor_plugin_ver="${'$'}4"

                [ -f "${'$'}toml_file" ] || return 0

                echo "  [patcher] Patching version catalog: ${'$'}toml_file"
                local before_hash
                before_hash=$(md5sum "${'$'}toml_file" | awk '{print $1}')

                # Rewrite ktor = "VERSION" or ktor-version = "VERSION" (framework version)
                sed -i -E "s@^([[:space:]]*(ktor|ktor-version|ktor_version|ktorVersion)[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}ktor_ver\3@g" "${'$'}toml_file"

                # Rewrite ktor-compiler-plugin versions — only if plugin version is known
                if [ -n "${'$'}ktor_plugin_ver" ]; then
                    sed -i -E "s@^([[:space:]]*(ktor-plugin|ktor_plugin|ktor-compiler-plugin|ktor_compiler_plugin|ktor-compiler-plugin-version)[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}ktor_plugin_ver\3@g" "${'$'}toml_file"
                fi

                # Rewrite kotlin = "VERSION"
                sed -i -E "s@^([[:space:]]*kotlin[[:space:]]*=[[:space:]]*[\"'])[^\"']+([\"'])@\1${'$'}kotlin_ver\2@g" "${'$'}toml_file"

                local after_hash
                after_hash=$(md5sum "${'$'}toml_file" | awk '{print $1}')
                if [ "${'$'}before_hash" != "${'$'}after_hash" ]; then
                    echo "  [patcher]   ✅ Patched ${'$'}toml_file"
                else
                    echo "  [patcher]   ℹ️  No changes needed in libs.versions.toml"
                fi
            }

            patch_gradle_properties() {
                local props_file="${'$'}1"
                local ktor_ver="${'$'}2"
                local kotlin_ver="${'$'}3"
                local ktor_plugin_ver="${'$'}4"

                [ -f "${'$'}props_file" ] || return 0

                echo "  [patcher] Patching gradle.properties: ${'$'}props_file"
                local before_hash
                before_hash=$(md5sum "${'$'}props_file" | awk '{print $1}')

                 # Ktor framework version
                sed -i -E "s@^([[:space:]]*(ktor_version|ktorVersion|ktor\.version|ktor_Version|KTOR_VERSION)[[:space:]]*=[[:space:]]*).*@\1${'$'}ktor_ver@g" "${'$'}props_file"

                # Ktor compiler plugin version — only if provided
                if [ -n "${'$'}ktor_plugin_ver" ]; then
                    sed -i -E "s@^([[:space:]]*(ktor_compiler_plugin_version|ktor_plugin_version|ktorPluginVersion|ktor\.plugin\.version)[[:space:]]*=[[:space:]]*).*@\1${'$'}ktor_plugin_ver@g" "${'$'}props_file"
                fi

                # Kotlin version
                sed -i -E "s@^([[:space:]]*(kotlin_version|kotlinVersion|kotlin\.version|kotlin_Version|KOTLIN_VERSION)[[:space:]]*=[[:space:]]*).*@\1${'$'}kotlin_ver@g" "${'$'}props_file"

                local after_hash
                after_hash=$(md5sum "${'$'}props_file" | awk '{print $1}')
                if [ "${'$'}before_hash" != "${'$'}after_hash" ]; then
                    echo "  [patcher]   ✅ Patched ${'$'}props_file"
                else
                    echo "  [patcher]   ℹ️  No changes needed in gradle.properties"
                fi
            }

            patch_sample_build_files() {
                local sample_path="${'$'}1"
                local ktor_ver="${'$'}2"
                local kotlin_ver="${'$'}3"
                local ktor_plugin_ver="${'$'}4"

                echo "🔧 Patching build files in ${'$'}sample_path for EAP versions..."
                echo "   Ktor framework: ${'$'}ktor_ver"
                echo "   Ktor plugin:    ${'$'}ktor_plugin_ver"
                echo "   Kotlin:         ${'$'}kotlin_ver"

                while IFS= read -r -d '' file; do
                    local filename
                    filename=$(basename "${'$'}file")

                    case "${'$'}filename" in
                        pom.xml)
                            patch_maven_pom "${'$'}file" "${'$'}ktor_ver" "${'$'}kotlin_ver"
                            ;;
                        *.gradle|*.gradle.kts)
                            patch_gradle_file "${'$'}file" "${'$'}ktor_ver" "${'$'}kotlin_ver" "${'$'}ktor_plugin_ver"
                            ;;
                        libs.versions.toml)
                            patch_version_catalog "${'$'}file" "${'$'}ktor_ver" "${'$'}kotlin_ver" "${'$'}ktor_plugin_ver"
                            ;;
                        gradle.properties)
                            patch_gradle_properties "${'$'}file" "${'$'}ktor_ver" "${'$'}kotlin_ver" "${'$'}ktor_plugin_ver"
                            ;;
                    esac
                done < <(find "${'$'}sample_path" \
                    -not -path "*/target/*" \
                    -not -path "*/build/*" \
                    -not -path "*/.gradle/*" \
                    -not -path "*/.idea/*" \
                    -not -path "*/.git/*" \
                    -not -path "*/.kotlin/*" \
                    -not -path "*/node_modules/*" \
                    \( -name "pom.xml" \
                       -o -name "*.gradle" \
                       -o -name "*.gradle.kts" \
                       -o -name "libs.versions.toml" \
                       -o -name "gradle.properties" \
                    \) -print0)

                echo "🔧 Patching complete for ${'$'}sample_path"
            }

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

            if [ "${'$'}{#SAMPLE_PROJECTS[@]}" -eq 0 ]; then
                echo "⚠️  No buildable sample directories discovered"
                echo "No buildable sample directories discovered" >> "${'$'}REPORT_FILE"
            else
                echo "Discovered ${'$'}{#SAMPLE_PROJECTS[@]} buildable samples:"
                printf '  - %s\n' "${'$'}{SAMPLE_PROJECTS[@]}" | sort
            fi

            for sample_dir in "${'$'}{SAMPLE_PROJECTS[@]}"; do
                [ -d "${'$'}sample_dir" ] || continue

                TOTAL_COUNT=$((TOTAL_COUNT + 1))
                echo "Validating sample: ${'$'}sample_dir..."
                patch_sample_build_files "${'$'}sample_dir" "%env.KTOR_VERSION%" "${'$'}KOTLIN_VERSION" "%env.KTOR_COMPILER_PLUGIN_VERSION%"

                # Maven sample
                if [ -f "${'$'}sample_dir/pom.xml" ]; then
                    echo "Maven sample detected. Running: mvn compile -B"
                    if mvn -f "${'$'}sample_dir/pom.xml" compile -B > "${'$'}REPORTS_DIR/${'$'}sample_dir.log" 2>&1; then
                        echo "✅ ${'$'}sample_dir: SUCCESS (Build)"
                        SUCCESSFUL_COUNT=$((SUCCESSFUL_COUNT + 1))
                        echo "${'$'}sample_dir: PASSED (Maven build)" >> "${'$'}REPORT_FILE"
                        
                        echo "Build successful, now running tests: mvn test -B"
                        if mvn -f "${'$'}sample_dir/pom.xml" test -B >> "${'$'}REPORTS_DIR/${'$'}sample_dir.log" 2>&1; then
                             echo "✅ ${'$'}sample_dir: Tests passed"
                        else
                             echo "⚠️  ${'$'}sample_dir: Tests failed (but build passed)"
                        fi
                    else
                        echo "❌ ${'$'}sample_dir: BUILD FAILED"
                        FAILED_COUNT=$((FAILED_COUNT + 1))
                        echo "${'$'}sample_dir: FAILED (Maven build)" >> "${'$'}REPORT_FILE"
                        tail -n 10 "${'$'}REPORTS_DIR/${'$'}sample_dir.log" || true
                        
                        # Package failed sample as a reproducer artifact
                        echo "📦 Packaging ${'$'}sample_dir as reproducer artifact..."
                        mkdir -p "${'$'}WORK_DIR/failed-samples"
                        (cd "${'$'}SAMPLES_ROOT" && zip -r "${'$'}WORK_DIR/failed-samples/${'$'}sample_dir.zip" "${'$'}sample_dir" \
                            -x "${'$'}sample_dir/target/*" \
                        ) || echo "⚠️  Failed to package ${'$'}sample_dir reproducer"
                    fi
                    continue
                fi

                # Gradle sample
                if [ -f "${'$'}sample_dir/gradlew" ]; then
                    chmod +x "${'$'}sample_dir/gradlew"
                    echo "Running: cd ${'$'}sample_dir && ./gradlew assemble --init-script ${'$'}INIT_SCRIPT ${'$'}SYSTEM_PROPS ${'$'}GRADLE_ARGS --no-daemon"
                    if (cd "${'$'}sample_dir" && ./gradlew assemble --init-script "${'$'}INIT_SCRIPT" ${'$'}SYSTEM_PROPS ${'$'}GRADLE_ARGS --no-daemon) > "${'$'}REPORTS_DIR/${'$'}sample_dir.log" 2>&1; then
                        echo "✅ ${'$'}sample_dir: SUCCESS (Build)"
                        SUCCESSFUL_COUNT=$((SUCCESSFUL_COUNT + 1))
                        echo "${'$'}sample_dir: PASSED (Gradle build)" >> "${'$'}REPORT_FILE"
                        
                        # Optionally run tests if build succeeded and test task exists
                        if (cd "${'$'}sample_dir" && ./gradlew tasks --all --init-script "${'$'}INIT_SCRIPT" ${'$'}SYSTEM_PROPS ${'$'}GRADLE_ARGS --no-daemon 2>/dev/null | grep -q "allTests"); then
                           echo "Build successful, now running aggregated tests: ./gradlew allTests --init-script ${'$'}INIT_SCRIPT ${'$'}SYSTEM_PROPS ${'$'}GRADLE_ARGS --no-daemon"
                           if (cd "${'$'}sample_dir" && ./gradlew allTests --init-script "${'$'}INIT_SCRIPT" ${'$'}SYSTEM_PROPS ${'$'}GRADLE_ARGS --no-daemon) >> "${'$'}REPORTS_DIR/${'$'}sample_dir.log" 2>&1; then
                               echo "✅ ${'$'}sample_dir: Tests passed"
                           else
                               echo "⚠️  ${'$'}sample_dir: Tests failed (but build passed)"
                           fi
                        elif (cd "${'$'}sample_dir" && ./gradlew tasks --all --init-script "${'$'}INIT_SCRIPT" ${'$'}SYSTEM_PROPS ${'$'}GRADLE_ARGS --no-daemon 2>/dev/null | grep -qx "test"); then
                           echo "Build successful, now running tests: ./gradlew test --init-script ${'$'}INIT_SCRIPT ${'$'}SYSTEM_PROPS ${'$'}GRADLE_ARGS --no-daemon"
                           if (cd "${'$'}sample_dir" && ./gradlew test --init-script "${'$'}INIT_SCRIPT" ${'$'}SYSTEM_PROPS ${'$'}GRADLE_ARGS --no-daemon) >> "${'$'}REPORTS_DIR/${'$'}sample_dir.log" 2>&1; then
                               echo "✅ ${'$'}sample_dir: Tests passed"
                           else
                               echo "⚠️  ${'$'}sample_dir: Tests failed (but build passed)"
                           fi
                        else
                           echo "⏭️  ${'$'}sample_dir: No standard test task found, skipping tests"
                        fi
                    else
                        echo "❌ ${'$'}sample_dir: BUILD FAILED"
                        FAILED_COUNT=$((FAILED_COUNT + 1))
                        echo "${'$'}sample_dir: FAILED (Gradle build)" >> "${'$'}REPORT_FILE"
                        tail -n 10 "${'$'}REPORTS_DIR/${'$'}sample_dir.log" || true
                        
                        # Package failed sample as a reproducer artifact (includes init script for EAP resolution)
                        echo "📦 Packaging ${'$'}sample_dir as reproducer artifact..."
                        mkdir -p "${'$'}WORK_DIR/failed-samples"
                        # Copy the init script into the sample
                        cp "${'$'}INIT_SCRIPT" "${'$'}sample_dir/gradle-eap-init.gradle" || true
                        (cd "${'$'}SAMPLES_ROOT" && zip -r "${'$'}WORK_DIR/failed-samples/${'$'}sample_dir.zip" "${'$'}sample_dir" \
                            -x "${'$'}sample_dir/.gradle/*" \
                            -x "${'$'}sample_dir/build/*" \
                            -x "${'$'}sample_dir/*/build/*" \
                            -x "${'$'}sample_dir/.kotlin/*" \
                        ) || echo "⚠️  Failed to package ${'$'}sample_dir reproducer"
                    fi
                else
                    echo "⚠️  No Gradle wrapper found for ${'$'}sample_dir"
                    SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
                    echo "${'$'}sample_dir: SKIPPED (No Gradle wrapper)" >> "${'$'}REPORT_FILE"
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
            if [[ -n "${'$'}TOTAL_COUNT" && "${'$'}TOTAL_COUNT" -gt 0 ]]; then
                SUCCESS_RATE=$(echo "${'$'}SUCCESSFUL_COUNT ${'$'}TOTAL_COUNT" | awk '{printf "%.1f", $1 * 100 / $2}')
            fi

            # Report results to TeamCity
            echo "##teamcity[setParameter name='internal.validation.total.tests' value='${'$'}TOTAL_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.passed.tests' value='${'$'}SUCCESSFUL_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.failed.tests' value='${'$'}FAILED_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.error.tests' value='0']"
            echo "##teamcity[setParameter name='internal.validation.skipped.tests' value='${'$'}SKIPPED_COUNT']"
            echo "##teamcity[setParameter name='internal.validation.success.rate' value='${'$'}SUCCESS_RATE']"
        """.trimIndent()
        }
    }
}
