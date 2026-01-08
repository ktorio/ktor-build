package subprojects.train

import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.buildSteps.*

fun BuildSteps.addDockerAgentLogging() {
    script {
        name = "Log Agent Information"
        scriptContent = """
            #!/bin/bash
            echo "=== Agent Information ==="

            echo "##teamcity[message text='Using agent: ${'$'}TEAMCITY_AGENT_NAME' status='NORMAL']"
            echo "##teamcity[setParameter name='env.DOCKER_AGENT_FOUND' value='false']"

            echo "Agent Name: ${'$'}TEAMCITY_AGENT_NAME"
            echo "Agent OS: ${'$'}TEAMCITY_AGENT_OS_FAMILY"
            echo "Agent Architecture: ${'$'}TEAMCITY_AGENT_CPU_ARCHITECTURE"

            echo "Note: Docker compatibility will be checked at runtime in setupTestcontainersEnvironment step"
            echo "=== Agent Information Complete ==="
        """.trimIndent()
    }
}

object ExternalSampleScripts {
    fun BuildSteps.backupConfigFiles() {
        script {
            name = "Backup Configuration Files"
            scriptContent = """
                #!/bin/bash
                echo "=== Backing up configuration files ==="
                find . -name "gradle.properties" -exec cp {} {}.backup \;
                find . -name "build.gradle.kts" -exec cp {} {}.backup \;
                find . -name "libs.versions.toml" -exec cp {} {}.backup \;
                echo "Configuration files backed up"
            """.trimIndent()
        }
    }

    fun BuildSteps.analyzeProjectStructure(specialHandling: List<SpecialHandling> = emptyList()) {
        script {
            name = "Analyze Project Structure"
            scriptContent = """
                #!/bin/bash
                echo "=== Project Structure Analysis ==="
                echo "Special handling: ${specialHandling.joinToString(",") { it.name }}"
                ls -la
                echo "=== Analysis Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.setupTestcontainersEnvironment() {
        script {
            name = "Setup Testcontainers Environment"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Setting up Testcontainers Environment ==="

                if [ -n "%env.TC_CLOUD_TOKEN%" ] && [ "%env.TC_CLOUD_TOKEN%" != "" ]; then
                    echo "✓ Testcontainers Cloud token found, configuring cloud environment"

                    mkdir -p ${'$'}HOME/.testcontainers
                    cat > ${'$'}HOME/.testcontainers/testcontainers.properties << 'EOF'
testcontainers.reuse.enable=false
ryuk.container.privileged=true
testcontainers.cloud.token=%env.TC_CLOUD_TOKEN%
EOF

                    export TESTCONTAINERS_CLOUD_TOKEN="%env.TC_CLOUD_TOKEN%"
                    export TESTCONTAINERS_RYUK_DISABLED=true

                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_CLOUD_TOKEN' value='%env.TC_CLOUD_TOKEN%']"
                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_RYUK_DISABLED' value='true']"
                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='cloud']"

                    if [ -f "gradle.properties" ]; then
                        echo "Updating existing gradle.properties with Testcontainers Cloud configuration"
                        sed -i '/^testcontainers\./d' gradle.properties
                    else
                        echo "Creating gradle.properties with Testcontainers Cloud configuration"
                        touch gradle.properties
                    fi

                    echo "" >> gradle.properties
                    echo "# Testcontainers Cloud Configuration" >> gradle.properties
                    echo "testcontainers.cloud.token=%env.TC_CLOUD_TOKEN%" >> gradle.properties
                    echo "testcontainers.ryuk.disabled=true" >> gradle.properties
                    echo "systemProp.testcontainers.cloud.token=%env.TC_CLOUD_TOKEN%" >> gradle.properties
                    echo "systemProp.testcontainers.ryuk.disabled=true" >> gradle.properties

                    echo "Contents of gradle.properties:"
                    cat gradle.properties
                    echo "Contents of testcontainers.properties:"
                    cat ${'$'}HOME/.testcontainers/testcontainers.properties

                    echo "✓ Testcontainers Cloud configured successfully"
                else
                    echo "⚠ No Testcontainers Cloud token found"
                    echo "Checking for local Docker availability..."

                    if command -v docker >/dev/null 2>&1; then
                        echo "Docker command found, checking Docker daemon and API version..."

                        if docker info >/dev/null 2>&1; then
                            echo "✓ Docker daemon is accessible"

                            DOCKER_API_VERSION=$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null || echo "unknown")
                            echo "Docker API Version: ${'$'}DOCKER_API_VERSION"

                            if [[ "${'$'}DOCKER_API_VERSION" =~ ^([0-9]+)\.([0-9]+) ]]; then
                                MAJOR=${'$'}{BASH_REMATCH[1]}
                                MINOR=${'$'}{BASH_REMATCH[2]}

                                if [ "${'$'}MAJOR" -gt 1 ] || ([ "${'$'}MAJOR" -eq 1 ] && [ "${'$'}MINOR" -ge 44 ]); then
                                    echo "✓ Docker API version compatible (${'$'}DOCKER_API_VERSION >= 1.44)"
                                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='local']"
                                else
                                    echo "❌ CRITICAL: Docker API version too old (${'$'}DOCKER_API_VERSION < 1.44)"
                                    echo "This will cause Testcontainers to fail with 'client version too old' error"
                                    echo "The minimum required Docker API version is 1.44"
                                    echo "Please upgrade Docker on this agent or use Testcontainers Cloud"
                                    echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='incompatible']"
                                    echo "##teamcity[buildProblem description='Docker API version ${'$'}DOCKER_API_VERSION is incompatible (minimum: 1.44)' identity='docker-api-version-incompatible']"
                                fi
                            else
                                echo "⚠ Could not determine Docker API version"
                                echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='unknown']"
                            fi
                        else
                            echo "⚠ Docker daemon not accessible"
                            echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='skip']"
                        fi
                    else
                        echo "⚠ Docker command not found"
                        echo "##teamcity[setParameter name='env.TESTCONTAINERS_MODE' value='skip']"
                    fi
                fi

                echo "Final Testcontainers mode: %env.TESTCONTAINERS_MODE%"
                echo "=== Testcontainers Environment Setup Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.setupAndroidSDK() {
        script {
            name = "Setup Android SDK"
            scriptContent = """
                #!/bin/bash
                echo "=== Setting up Android SDK ==="
                export ANDROID_HOME=/opt/android-sdk
                export PATH=${'$'}PATH:${'$'}ANDROID_HOME/tools:${'$'}ANDROID_HOME/platform-tools
                echo "Android SDK configured"
                echo "=== Android SDK Setup Complete ==="
            """.trimIndent()
        }
    }

    fun BuildSteps.setupDaggerEnvironment() {
        script {
            name = "Setup Dagger Environment"
            scriptContent = """
                #!/bin/bash
                echo "=== Setting up Dagger Environment ==="
                echo "Configuring annotation processing for Dagger"
                echo "=== Dagger Environment Setup Complete ==="
            """.trimIndent()
        }
    }
}
