
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

abstract class BaseExternalEAPSample : EAPSampleConfig {
    protected fun BuildType.addCommonExternalEAPConfiguration(sampleName: String) {
        addExternalEAPSampleFailureConditions(sampleName)
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

fun BuildType.addExternalEAPSampleFailureConditions(sampleName: String) {
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

        if (DockerSupport.requiresDocker(sampleName)) {
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

        if (AndroidSupport.requiresAndroidSDK(sampleName)) {
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

object DockerSupport {
    fun requiresDocker(projectName: String): Boolean =
        projectName in listOf("ktor-ai-server", "ktor-koog-example", "ktor-full-stack-real-world")
}

object DaggerSupport {
    fun requiresDagger(projectName: String): Boolean =
        projectName in listOf("ktor-di-overview")
}

object AndroidSupport {
    fun requiresAndroidSDK(projectName: String): Boolean =
        projectName in listOf("ktor-full-stack-real-world")
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

    fun BuildSteps.gradleEAPBuild(projectName: String) {
        gradle {
            name = "Build EAP Sample"
            tasks = "build"
            jdkHome = Env.JDK_LTS
            gradleParams = "--no-scan --build-cache --parallel"
            jvmArgs = "-Xmx3g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC"
            useGradleWrapper = true
            enableStacktrace = true
        }

        script {
            name = "Essential Cleanup"
            scriptContent = """
                #!/bin/bash
                ./gradlew --stop || true
                find . -name "*.tmp" -delete 2>/dev/null || true
            """.trimIndent()
            executionMode = BuildStep.ExecutionMode.ALWAYS
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
        docker info
        echo "Docker setup completed"
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
        
        echo "org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseG1GC" >> gradle.properties
        echo "org.gradle.daemon=true" >> gradle.properties
        echo "org.gradle.parallel=true" >> gradle.properties
        
        ./gradlew build --info --build-cache --no-scan
        
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

            if (DockerSupport.requiresDocker(projectName)) {
                contains("teamcity.agent.jvm.os.name", "Linux")
                exists("docker.server.version")
            }

            if (AndroidSupport.requiresAndroidSDK(projectName)) {
                exists("android.sdk.root")
            }
        }

        params {
            defaultGradleParams()
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")

            if (projectName == "full-stack-ktor-talk") {
                param("env.GOOGLE_CLIENT_ID", "placeholder-google-client-id")
            }
        }

        steps {
            with(EAPBuildSteps) {
                standardEAPSetup()

                if (DockerSupport.requiresDocker(projectName)) {
                    setupDockerEnvironment()
                }

                if (AndroidSupport.requiresAndroidSDK(projectName)) {
                    setupAndroidEnvironment()
                }

                if (DaggerSupport.requiresDagger(projectName)) {
                    setupDaggerEnvironment()
                }

                when (buildType) {
                    ExternalSampleBuildType.GRADLE -> {
                        gradleEAPBuild(projectName)
                    }
                    ExternalSampleBuildType.AMPER -> {
                        amperEAPBuild()
                    }
                }
            }
        }

        addCommonExternalEAPConfiguration(projectName)

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
