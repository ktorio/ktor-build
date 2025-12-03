package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.build.*

object VCSKtorArrowExample : KtorVcsRoot({
    name = "Ktor Arrow Example"
    url = "https://github.com/nomisRev/ktor-arrow-example.git"
})

object VCSKtorAiServer : KtorVcsRoot({
    name = "Ktor AI Server"
    url = "https://github.com/nomisRev/ktor-ai-server.git"
})

object VCSKtorNativeServer : KtorVcsRoot({
    name = "Ktor Native Server"
    url = "https://github.com/nomisRev/ktor-native-server.git"
})

object VCSKtorKoogExample : KtorVcsRoot({
    name = "Ktor Koog Example"
    url = "https://github.com/nomisRev/ktor-koog-example.git"
})

object VCSFullStackKtorTalk : KtorVcsRoot({
    name = "Full Stack Ktor Talk"
    url = "https://github.com/nomisRev/full-stack-ktor-talk.git"
})

object VCSKtorConfigExample : KtorVcsRoot({
    name = "Ktor Config Example"
    url = "https://github.com/nomisRev/ktor-config-example.git"
})

object VCSKtorWorkshop2025 : KtorVcsRoot({
    name = "Ktor Workshop 2025"
    url = "https://github.com/nomisRev/ktor-workshop-2025.git"
})

object VCSAmperKtorSample : KtorVcsRoot({
    name = "Amper Ktor Sample"
    url = "https://github.com/nomisRev/amper-ktor-sample.git"
})

object VCSKtorDIOverview : KtorVcsRoot({
    name = "Ktor DI Overview"
    url = "https://github.com/nomisRev/Ktor-DI-Overview.git"
})

object VCSKtorFullStackRealWorld : KtorVcsRoot({
    name = "Ktor Full Stack Real World"
    url = "https://github.com/nomisRev/ktor-full-stack-real-world.git"
})

object ExternalSampleScripts {
    fun backupConfigFiles() = """
        echo "Backing up configuration files..."
        [ -f "gradle.properties" ] && cp gradle.properties gradle.properties.backup
        [ -f "gradle/libs.versions.toml" ] && cp gradle/libs.versions.toml gradle/libs.versions.toml.backup
        [ -f "settings.gradle.kts" ] && cp settings.gradle.kts settings.gradle.kts.backup
        [ -f "settings.gradle" ] && cp settings.gradle settings.gradle.backup
        [ -f "pom.xml" ] && cp pom.xml pom.xml.backup
        [ -f "module.yaml" ] && cp module.yaml module.yaml.backup
        [ -f "settings.yaml" ] && cp settings.yaml settings.yaml.backup
    """.trimIndent()

    fun cleanupBackups() = """
    echo "Cleaning up backup files..."
    rm -f *.backup gradle/libs.versions.toml.backup gradle-eap-init.gradle gradle-amper-eap-init.gradle
""".trimIndent()

    fun updateGradleProperties() = """
        update_gradle_properties() {
            if [ -f "gradle.properties" ]; then
                echo "Updating existing gradle.properties..."
                grep -v "^ktor.*Version\s*=" gradle.properties > gradle.properties.tmp || touch gradle.properties.tmp
                echo "ktorVersion=%env.KTOR_VERSION%" >> gradle.properties.tmp
                echo "ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%" >> gradle.properties.tmp
                mv gradle.properties.tmp gradle.properties
            else
                echo "Creating new gradle.properties..."
                cat > gradle.properties << EOF
ktorVersion=%env.KTOR_VERSION%
ktorCompilerPluginVersion=%env.KTOR_COMPILER_PLUGIN_VERSION%
org.gradle.daemon=false
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m
EOF
            fi
        }
        update_gradle_properties
    """.trimIndent()

    fun updateVersionCatalog() = """
        update_version_catalog() {
            if [ -f "gradle/libs.versions.toml" ]; then
                echo "Updating version catalog..."
                if grep -q "^\s*ktor\s*=" gradle/libs.versions.toml; then
                    sed -i 's/^\s*ktor\s*=.*/ktor = "%env.KTOR_VERSION%"/' gradle/libs.versions.toml
                else
                    if grep -q "^\[versions\]" gradle/libs.versions.toml; then
                        sed -i '/^\[versions\]/a ktor = "%env.KTOR_VERSION%"' gradle/libs.versions.toml
                    else
                        echo -e "\n[versions]\nktor = \"%env.KTOR_VERSION%\"" >> gradle/libs.versions.toml
                    fi
                fi
                echo "✓ Updated libs.versions.toml"
            fi
        }
        update_version_catalog
    """.trimIndent()

    fun setupGradleRepositories() = """
        setup_gradle_repositories() {
            cat > gradle-eap-init.gradle << EOF
allprojects {
    repositories {
        maven { url "${EapRepositoryConfig.KTOR_EAP_URL}" }
        maven { url "${EapRepositoryConfig.COMPOSE_DEV_URL}" }
        mavenCentral()
        gradlePluginPortal()
    }
}
EOF
        }
        setup_gradle_repositories
    """.trimIndent()

    fun buildGradleProject() = """
        build_gradle_project() {
            if [ -f "./gradlew" ]; then
                chmod +x ./gradlew
                GRADLE_CMD="./gradlew"
            else
                GRADLE_CMD="gradle"
            fi

            echo "Building with: ${'$'}GRADLE_CMD"
            ${'$'}GRADLE_CMD clean build \
                --init-script gradle-eap-init.gradle \
                --no-daemon --stacktrace --refresh-dependencies
        }
        build_gradle_project
    """.trimIndent()

    fun setupMavenRepositories() = """
        setup_maven_repositories() {
            if ! [ -f "pom.xml" ]; then
                echo "ERROR: No pom.xml found"
                exit 1
            fi

            if grep -q "<repositories>" pom.xml; then
                sed -i '/<\/repositories>/i\
        <repository><id>ktor-eap</id><url>${EapRepositoryConfig.KTOR_EAP_URL}</url></repository>' pom.xml
            else
                sed -i '/<modelVersion>.*<\/modelVersion>/a\
    <repositories>\
        <repository><id>ktor-eap</id><url>${EapRepositoryConfig.KTOR_EAP_URL}</url></repository>\
    </repositories>' pom.xml
            fi
        }
        setup_maven_repositories
    """.trimIndent()

    fun buildMavenProject() = """
        build_maven_project() {
            mvn clean compile test \
                -Dktor.version=%env.KTOR_VERSION% \
                -Dktor-compiler-plugin.version=%env.KTOR_COMPILER_PLUGIN_VERSION% \
                -U
        }
        build_maven_project
    """.trimIndent()
}

fun BuildSteps.buildEAPExternalGradleSample() {
    script {
        name = "Setup EAP Configuration"
        scriptContent = """
            #!/bin/bash
            set -e
            ${ExternalSampleScripts.backupConfigFiles()}
            ${ExternalSampleScripts.updateGradleProperties()}
            ${ExternalSampleScripts.updateVersionCatalog()}
            ${ExternalSampleScripts.setupGradleRepositories()}
        """.trimIndent()
    }

    script {
        name = "Build Gradle Sample"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Building External Gradle Sample with Ktor EAP %env.KTOR_VERSION% ==="
            ${ExternalSampleScripts.buildGradleProject()}
            echo "✓ Build completed successfully"
        """.trimIndent()
    }
}

fun BuildSteps.buildEAPExternalMavenSample() {
    script {
        name = "Setup Maven EAP Configuration"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Setting up Maven EAP Configuration ==="
            ${ExternalSampleScripts.backupConfigFiles()}
            ${ExternalSampleScripts.setupMavenRepositories()}
        """.trimIndent()
    }

    script {
        name = "Build Maven Sample"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Building External Maven Sample with Ktor EAP %env.KTOR_VERSION% ==="
            ${ExternalSampleScripts.buildMavenProject()}
            echo "✓ Build completed successfully"
        """.trimIndent()
    }
}

fun BuildSteps.buildEAPExternalAmperSample() {
    script {
        name = "Setup Amper EAP Configuration"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Setting up Amper EAP Configuration ==="
            ${ExternalSampleScripts.backupConfigFiles()}

            # Update Amper files if they exist
            if [ -f "module.yaml" ] && grep -q "ktor" module.yaml; then
                sed -i 's/:[0-9]\+\.[0-9]\+\.[0-9][^[:space:]]*/:%env.KTOR_VERSION%/g' module.yaml
            fi

            ${ExternalSampleScripts.updateGradleProperties()}
        """.trimIndent()
    }

    script {
        name = "Build Amper Sample"
        scriptContent = """
            #!/bin/bash
            set -e
            echo "=== Building External Amper Sample with Ktor EAP %env.KTOR_VERSION% ==="
            ${ExternalSampleScripts.setupGradleRepositories()}
            ${ExternalSampleScripts.buildGradleProject()}
            echo "✓ Build completed successfully"
        """.trimIndent()
    }
}

fun BuildSteps.addCleanupStep() {
    script {
        name = "Cleanup"
        scriptContent = """
            #!/bin/bash
            ${ExternalSampleScripts.cleanupBackups()}
        """.trimIndent()
        executionMode = BuildStep.ExecutionMode.ALWAYS
    }
}

data class ExternalSampleConfig(
    override val projectName: String,
    val vcsRoot: VcsRoot,
    val buildType: ExternalSampleBuildType = ExternalSampleBuildType.GRADLE,
    val versionResolver: BuildType
) : EAPSampleConfig {

    override fun createEAPBuildType(): BuildType {
        return BuildType {
            id("ExternalEAP_${projectName.replace("-", "_").replace(" ", "_")}")
            name = "EAP: $projectName"
            description = "Validate $projectName against EAP version of Ktor"

            configureEAPSampleBuild(projectName, vcsRoot, versionResolver)

            steps {
                debugEnvironmentVariables()
                createEAPGradleInitScript()

                when (buildType) {
                    ExternalSampleBuildType.GRADLE -> buildEAPExternalGradleSample()
                    ExternalSampleBuildType.MAVEN -> buildEAPExternalMavenSample()
                    ExternalSampleBuildType.AMPER -> buildEAPExternalAmperSample()
                }

                addCleanupStep()
            }

            failureConditions {
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "BUILD FAILED"
                    failureMessage = "Build failed for $projectName"
                    stopBuildOnFailure = true
                }
                failOnText {
                    conditionType = BuildFailureOnText.ConditionType.CONTAINS
                    pattern = "CRITICAL ERROR:"
                    failureMessage = "Critical error in $projectName build"
                    stopBuildOnFailure = true
                }
                executionTimeoutMin = 25
            }
        }
    }
}

enum class ExternalSampleBuildType {
    GRADLE, MAVEN, AMPER
}

object ExternalSamplesEAPValidation : Project({
    id("ExternalSamplesEAPValidation")
    name = "External Samples EAP Validation"
    description = "Validate external GitHub samples against EAP versions of Ktor"

    vcsRoot(VCSKtorArrowExample)
    vcsRoot(VCSKtorAiServer)
    vcsRoot(VCSKtorNativeServer)
    vcsRoot(VCSKtorKoogExample)
    vcsRoot(VCSFullStackKtorTalk)
    vcsRoot(VCSKtorConfigExample)
    vcsRoot(VCSKtorWorkshop2025)
    vcsRoot(VCSAmperKtorSample)
    vcsRoot(VCSKtorDIOverview)
    vcsRoot(VCSKtorFullStackRealWorld)

    params {
        param("ktor.eap.version", "KTOR_VERSION")
    }

    val versionResolver = EAPVersionResolver.createVersionResolver(
        id = "ExternalKtorEAPVersionResolver",
        name = "Set EAP Version for External Samples",
        description = "Determines the EAP version to use for external sample validation"
    )

    buildType(versionResolver)

    val externalSamples = listOf(
        ExternalSampleConfig("ktor-arrow-example", VCSKtorArrowExample, ExternalSampleBuildType.GRADLE, versionResolver),
        ExternalSampleConfig("ktor-ai-server", VCSKtorAiServer, ExternalSampleBuildType.GRADLE, versionResolver),
        ExternalSampleConfig("ktor-native-server", VCSKtorNativeServer, ExternalSampleBuildType.GRADLE, versionResolver),
        ExternalSampleConfig("ktor-koog-example", VCSKtorKoogExample, ExternalSampleBuildType.GRADLE, versionResolver),
        ExternalSampleConfig("full-stack-ktor-talk", VCSFullStackKtorTalk, ExternalSampleBuildType.GRADLE, versionResolver),
        ExternalSampleConfig("ktor-config-example", VCSKtorConfigExample, ExternalSampleBuildType.GRADLE, versionResolver),
        ExternalSampleConfig("ktor-workshop-2025", VCSKtorWorkshop2025, ExternalSampleBuildType.GRADLE, versionResolver),
        ExternalSampleConfig("amper-ktor-sample", VCSAmperKtorSample, ExternalSampleBuildType.AMPER, versionResolver),
        ExternalSampleConfig("Ktor-DI-Overview", VCSKtorDIOverview, ExternalSampleBuildType.GRADLE, versionResolver),
        ExternalSampleConfig("ktor-full-stack-real-world", VCSKtorFullStackRealWorld, ExternalSampleBuildType.GRADLE, versionResolver)
    )

    val allExternalSampleBuilds = externalSamples.map { it.createEAPBuildType() }

    allExternalSampleBuilds.forEach { buildType(it) }

    buildType {
        id("ExternalKtorEAPSamplesCompositeBuild")
        name = "Validate All External Samples with EAP"
        description = "Run all external GitHub samples against the EAP version of Ktor"
        type = BuildTypeSettings.Type.COMPOSITE

        params {
            defaultGradleParams()
            param("env.GIT_BRANCH", "%teamcity.build.branch%")
            param("teamcity.build.skipDependencyBuilds", "true")
        }

        features {
            notifications {
                notifierSettings = slackNotifier {
                    connection = "PROJECT_EXT_5"
                    sendTo = "#ktor-external-samples-eap"
                    messageFormat = verboseMessageFormat {
                        addStatusText = true
                    }
                }
                buildFailedToStart = true
                buildFailed = true
                buildFinishedSuccessfully = true
            }
        }

        triggers {
            finishBuildTrigger {
                buildType = EapConstants.PUBLISH_EAP_BUILD_TYPE_ID
                successfulOnly = true
                branchFilter = "+:*"
            }
        }

        dependencies {
            dependency(versionResolver) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                }
            }

            allExternalSampleBuilds.forEach { sampleBuild ->
                dependency(sampleBuild) {
                    snapshot {
                        onDependencyFailure = FailureAction.ADD_PROBLEM
                        onDependencyCancel = FailureAction.CANCEL
                    }
                }
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "No agents available to run"
                failureMessage = "No compatible agents found for external samples composite"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = 45
        }
    }
})
