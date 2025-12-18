package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.Agents.Arch
import subprojects.Agents.OS
import subprojects.build.defaultBuildFeatures
import subprojects.build.defaultGradleParams

enum class SpecialHandling {
    KOTLIN_MULTIPLATFORM,
    AMPER_GRADLE_HYBRID,
    DOCKER_TESTCONTAINERS,
    DAGGER_ANNOTATION_PROCESSING,
    ANDROID_SDK_REQUIRED
}

enum class ExternalSampleBuildType {
    GRADLE, AMPER
}

interface ExternalEAPSampleConfig : EAPSampleConfig {
    val vcsRoot: VcsRoot
    val buildType: ExternalSampleBuildType
    val versionResolver: BuildType
    val specialHandling: List<SpecialHandling>
}

object SpecialHandlingUtils {
    fun requiresDocker(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DOCKER_TESTCONTAINERS)

    fun requiresAndroidSDK(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.ANDROID_SDK_REQUIRED)

    fun requiresDagger(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)
}

abstract class BaseExternalEAPSample : EAPSampleConfig {
    protected fun BuildType.addCommonExternalEAPConfiguration(sampleName: String, specialHandling: List<SpecialHandling>) {
        addExternalEAPSampleFailureConditions(sampleName, specialHandling)
        defaultBuildFeatures()

        features {
            with(EAPBuildFeatures) {
                addEAPSlackNotifications()
            }
        }
    }
}

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

fun BuildType.addExternalEAPSampleFailureConditions(sampleName: String, specialHandling: List<SpecialHandling>) {
    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "BUILD FAILED"
            failureMessage = "Build failed for $sampleName sample"
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "FAILURE:"
            failureMessage = "Build failure detected in $sampleName sample"
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "OutOfMemoryError"
            failureMessage = "Out of memory error in $sampleName"
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Gradle build daemon disappeared"
            failureMessage = "Gradle daemon crashed for $sampleName"
            stopBuildOnFailure = true
        }

        if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "docker: command not found"
                failureMessage = "Docker not available for $sampleName"
                stopBuildOnFailure = true
            }

            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "Cannot connect to the Docker daemon"
                failureMessage = "Docker daemon not accessible for $sampleName"
                stopBuildOnFailure = true
            }
        }

        if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "ANDROID_HOME"
                failureMessage = "Android SDK not configured for $sampleName"
                stopBuildOnFailure = true
            }
        }

        executionTimeoutMin = 20
    }
}

data class EAPSampleBuilder(
    val projectName: String,
    val vcsRoot: VcsRoot,
    val versionResolver: BuildType
) {
    private var buildType: ExternalSampleBuildType = ExternalSampleBuildType.GRADLE
    private var specialHandling: List<SpecialHandling> = emptyList()

    fun withBuildType(type: ExternalSampleBuildType) = apply { buildType = type }

    fun withSpecialHandling(vararg handling: SpecialHandling) = apply {
        specialHandling = handling.toList()
    }

    fun build(): ExternalSampleConfig = ExternalSampleConfig(
        projectName = projectName,
        vcsRoot = vcsRoot,
        buildType = buildType,
        versionResolver = versionResolver,
        specialHandling = specialHandling
    )
}

object EAPBuildFeatures {
    fun BuildFeatures.addEAPSlackNotifications(
        includeSuccess: Boolean = false,
        includeBuildStart: Boolean = false
    ) {
        notifications {
            notifierSettings = slackNotifier {
                connection = "PROJECT_EXT_5"
                sendTo = "#ktor-eap-validation"
                messageFormat = verboseMessageFormat {
                    addStatusText = true
                }
            }
            if (includeBuildStart) buildStarted = true
            buildFailed = true
            if (includeSuccess) buildFinishedSuccessfully = true
            buildFailedToStart = true
        }
    }
}

object EAPScriptTemplates {
    fun buildCommonSetup() = """
        #!/bin/bash
        set -e
        echo "=== EAP Build Common Setup ==="
        echo "Build Agent: %teamcity.agent.name%"
        echo "Build ID: %teamcity.build.id%"
        echo "================================"
    """.trimIndent()
}

object EAPBuildSteps {
    fun BuildSteps.standardEAPSetup() {
        script {
            name = "EAP Environment Setup"
            scriptContent = EAPScriptTemplates.buildCommonSetup()
        }
    }

    fun BuildSteps.setupDockerEnvironment() {
        script {
            name = "Setup Docker Environment"
            scriptContent = ExternalSampleScripts.setupDockerEnvironment()
        }
    }

    fun BuildSteps.setupDaggerEnvironment() {
        script {
            name = "Setup Dagger Environment"
            scriptContent = ExternalSampleScripts.setupDaggerEnvironment()
        }
    }

    fun BuildSteps.setupAndroidEnvironment() {
        script {
            name = "Setup Android Environment"
            scriptContent = ExternalSampleScripts.setupAndroidSDK()
        }
    }

    fun BuildSteps.gradleEAPBuildWithConditionalTests(projectName: String) {
        gradle {
            name = "Build EAP Sample"
            tasks = if (projectName == "ktor-ai-server") {
                "assemble"
            } else {
                "build"
            }
            jdkHome = Env.JDK_LTS
            gradleParams = "--no-scan --build-cache --parallel --no-daemon"
            jvmArgs = "-Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseG1GC"
            useGradleWrapper = true
            enableStacktrace = true
        }
    }

    fun BuildSteps.amperEAPBuild() {
        script {
            name = "Build Amper Project"
            scriptContent = ExternalSampleScripts.buildAmperProject()
        }
    }
}

object ExternalSampleScripts {

    fun setupDockerEnvironment() = """
    #!/bin/bash
    set -e
    echo "Setting up Docker environment for EAP testing..."
    
    docker --version
    
    DOCKER_API_VERSION=$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null || echo "unknown")
    echo "Docker API Version: ${'$'}DOCKER_API_VERSION"
    
    if [ "${'$'}DOCKER_API_VERSION" != "unknown" ]; then
        MAJOR_VERSION=$(echo ${'$'}DOCKER_API_VERSION | cut -d. -f1)
        MINOR_VERSION=$(echo ${'$'}DOCKER_API_VERSION | cut -d. -f2)
        
        if [ ${'$'}MAJOR_VERSION -eq 1 ] && [ ${'$'}MINOR_VERSION -lt 44 ]; then
            echo "WARNING: Docker API version ${'$'}DOCKER_API_VERSION is too old. Minimum required is 1.44"
            echo "Attempting to use newer Docker client..."
            
            export DOCKER_API_VERSION=1.44
            echo "Set DOCKER_API_VERSION to 1.44"
        fi
    fi
    
    docker info
    echo "Docker setup completed successfully"
    """.trimIndent()

    fun setupDaggerEnvironment() = """
        #!/bin/bash
        set -e
        echo "Setting up Dagger annotation processing environment..."
        echo "Dagger environment setup completed"
    """.trimIndent()

    fun setupAndroidSDK() = """
        #!/bin/bash
        set -e
        echo "Setting up Android SDK environment..."
        if [ -z "${'$'}ANDROID_HOME" ]; then
            echo "ERROR: ANDROID_HOME not set"
            exit 1
        fi
        echo "Android SDK found at: ${'$'}ANDROID_HOME"
        echo "Android SDK setup completed"
    """.trimIndent()

    fun buildAmperProject() = """
    #!/bin/bash
    set -e
    echo "Building Amper project with EAP dependencies..."
    
    if [ -f "module.yaml" ]; then
        echo "Found module.yaml - this is an Amper project"
        
        if [ -f "./amperw" ]; then
            echo "Using Amper wrapper"
            ./amperw build
        elif [ -f "gradlew" ]; then
            echo "Amper project with Gradle wrapper - using Gradle"
            echo "org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseG1GC" >> gradle.properties
            echo "org.gradle.daemon=true" >> gradle.properties  
            echo "org.gradle.parallel=true" >> gradle.properties
            ./gradlew build --info --build-cache --no-scan
        else
            echo "No build wrapper found. Checking for Gradle installation..."
            if command -v gradle &> /dev/null; then
                echo "Using system Gradle"
                gradle build --info --build-cache --no-scan
            else
                echo "ERROR: No Gradle wrapper or system Gradle found for Amper project"
                echo "Please ensure the project has either gradlew, amperw, or system Gradle available"
                exit 1
            fi
        fi
    else
        echo "No module.yaml found - treating as regular Gradle project"
        if [ ! -f "./gradlew" ]; then
            echo "ERROR: No gradlew found in project root"
            echo "Contents of current directory:"
            ls -la
            exit 1
        fi
        
        echo "org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseG1GC" >> gradle.properties
        echo "org.gradle.daemon=true" >> gradle.properties
        echo "org.gradle.parallel=true" >> gradle.properties
        ./gradlew build --info --build-cache --no-scan
    fi
    
    echo "Amper project build completed"
""".trimIndent()
}

data class ExternalSampleConfig(
    override val projectName: String,
    override val vcsRoot: VcsRoot,
    override val buildType: ExternalSampleBuildType,
    override val versionResolver: BuildType,
    override val specialHandling: List<SpecialHandling>
) : BaseExternalEAPSample(), ExternalEAPSampleConfig {

    override fun createEAPBuildType(): BuildType = BuildType {
        id("ExternalSample_${projectName.replace('-', '_')}")
        name = "EAP Sample: $projectName"

        vcs {
            root(vcsRoot)
        }

        requirements {
            agent(OS.Linux, Arch.X64)

            if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                contains("teamcity.agent.jvm.os.name", "Linux")
                exists("docker.server.version")
                matches("docker.server.version", ".*")
                doesNotContain("docker.server.version", "1.3")
                doesNotContain("docker.server.version", "1.4[0-3]")
            }

            if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                exists("android.sdk.root")
            }
        }

        params {
            defaultGradleParams()
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")

            if (projectName == "full-stack-ktor-talk") {
                param("env.GOOGLE_CLIENT_ID", "placeholder-google-client-id")
                param("env.API_BASE_URL", "http://localhost:8080")
            }
        }

        steps {
            with(EAPBuildSteps) {
                standardEAPSetup()

                if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
                    setupDockerEnvironment()
                }

                if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
                    setupAndroidEnvironment()
                }

                if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
                    setupDaggerEnvironment()
                }

                when (buildType) {
                    ExternalSampleBuildType.GRADLE -> {
                        gradleEAPBuildWithConditionalTests(projectName)
                    }
                    ExternalSampleBuildType.AMPER -> {
                        amperEAPBuild()
                    }
                }
            }
        }

        addCommonExternalEAPConfiguration(projectName, specialHandling)

        dependencies {
            dependency(versionResolver) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }
}

object ExternalSamplesEAPValidation : Project({
    id("ExternalSamplesEAPValidation")
    name = "External Samples EAP Validation"
    description = "Simplified validation using standard agent configuration"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
    }

    val versionResolver = createVersionResolver()
    buildType(versionResolver)

    val samples = createSampleConfigurations(versionResolver)
    val allBuildTypes = samples.map { it.createEAPBuildType() }
    allBuildTypes.forEach { buildType(it) }

    buildType(createMasterCompositeBuild(versionResolver, allBuildTypes))
})

private fun Project.registerVCSRoots() {
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
}

private fun createVersionResolver(): BuildType =
    EAPVersionResolver.createVersionResolver(
        id = "KtorEAPVersionResolver_External",
        name = "EAP Version Resolver (External Samples)",
        description = "Resolves the latest EAP version for external sample validation"
    )

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalSampleConfig> {
    return listOf(
        EAPSampleBuilder("ktor-arrow-example", VCSKtorArrowExample, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
            .build(),
        EAPSampleBuilder("ktor-ai-server", VCSKtorAiServer, versionResolver)
            .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
            .build(),
        EAPSampleBuilder("ktor-native-server", VCSKtorNativeServer, versionResolver)
            .build(),
        EAPSampleBuilder("ktor-koog-example", VCSKtorKoogExample, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.DOCKER_TESTCONTAINERS)
            .build(),
        EAPSampleBuilder("full-stack-ktor-talk", VCSFullStackKtorTalk, versionResolver)
            .build(),
        EAPSampleBuilder("ktor-config-example", VCSKtorConfigExample, versionResolver)
            .build(),
        EAPSampleBuilder("ktor-workshop-2025", VCSKtorWorkshop2025, versionResolver)
            .build(),
        EAPSampleBuilder("amper-ktor-sample", VCSAmperKtorSample, versionResolver)
            .withBuildType(ExternalSampleBuildType.AMPER)
            .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID)
            .build(),
        EAPSampleBuilder("ktor-di-overview", VCSKtorDIOverview, versionResolver)
            .withSpecialHandling(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)
            .build(),
        EAPSampleBuilder("ktor-full-stack-real-world", VCSKtorFullStackRealWorld, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.DOCKER_TESTCONTAINERS, SpecialHandling.ANDROID_SDK_REQUIRED)
            .build()
    )
}

private fun createMasterCompositeBuild(
    versionResolver: BuildType,
    allBuildTypes: List<BuildType>
): BuildType = BuildType {
    id("ExternalSamplesMasterBuild")
    name = "EAP External Samples Master Build"
    description = "Master composite build for all external samples"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("env.GIT_BRANCH", "%teamcity.build.branch%")
        param("teamcity.build.skipDependencyBuilds", "true")
        param("teamcity.build.executionTimeoutMin", "30")
    }

    dependencies {
        dependency(versionResolver) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }

        allBuildTypes.forEach { buildType ->
            dependency(buildType) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }

    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "No agents available to run"
            failureMessage = "No compatible agents found for EAP validation"
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Build queue timeout"
            failureMessage = "EAP samples master build timed out"
            stopBuildOnFailure = true
        }

        executionTimeoutMin = 30
    }

    features {
        with(EAPBuildFeatures) {
            addEAPSlackNotifications(includeSuccess = true, includeBuildStart = true)
        }
    }

    triggers {
        finishBuildTrigger {
            buildType = "KtorPublish_AllEAP"
            successfulOnly = true
            branchFilter = "+:refs/heads/*"
        }
    }
}
