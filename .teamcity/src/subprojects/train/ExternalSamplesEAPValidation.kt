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

enum class ProjectComplexity {
    LIGHT,
    MEDIUM,
    HEAVY,
    ULTRA_HEAVY
}

enum class AgentTier {
    STANDARD,
    HIGH_MEMORY,
    PREMIUM
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

object AgentPoolManager {
    fun getRequiredAgentTier(complexity: ProjectComplexity): AgentTier {
        return when (complexity) {
            ProjectComplexity.LIGHT -> AgentTier.STANDARD
            ProjectComplexity.MEDIUM -> AgentTier.HIGH_MEMORY
            ProjectComplexity.HEAVY -> AgentTier.HIGH_MEMORY
            ProjectComplexity.ULTRA_HEAVY -> AgentTier.PREMIUM
        }
    }

    fun configureAgentRequirements(requirements: Requirements) {
        requirements.apply { agent(OS.Linux, Arch.X64) }
    }
}

object ResourceManager {
    fun getComplexityFor(projectName: String, specialHandling: List<SpecialHandling>): ProjectComplexity {
        return when {
            projectName in listOf("ktor-full-stack-real-world", "ktor-koog-example") ->
                ProjectComplexity.ULTRA_HEAVY

            projectName in listOf("ktor-arrow-example", "ktor-ai-server", "ktor-di-overview") ->
                ProjectComplexity.HEAVY

            specialHandling.any { it in listOf(SpecialHandling.DOCKER_TESTCONTAINERS, SpecialHandling.KOTLIN_MULTIPLATFORM) } ->
                ProjectComplexity.MEDIUM

            else -> ProjectComplexity.LIGHT
        }
    }

    fun getResourceRequirements(complexity: ProjectComplexity): ResourceRequirements {
        return when (complexity) {
            ProjectComplexity.LIGHT -> ResourceRequirements(
                timeoutMinutes = 15,
                memoryMB = 3072,
                maxWorkers = 6,
                gradleJvmArgs = "-Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseG1GC -XX:+UseStringDeduplication",
                gradleParams = "--no-scan --build-cache --configuration-cache --parallel",
                teamCityJvmArgs = "-Xmx3g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC"
            )
            ProjectComplexity.MEDIUM -> ResourceRequirements(
                timeoutMinutes = 20,
                memoryMB = 4096,
                maxWorkers = 4,
                gradleJvmArgs = "-Xmx3g -XX:MaxMetaspaceSize=768m -XX:+UseG1GC",
                gradleParams = "--no-scan --build-cache",
                teamCityJvmArgs = "-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"
            )
            ProjectComplexity.HEAVY -> ResourceRequirements(
                timeoutMinutes = 25,
                memoryMB = 6144,
                maxWorkers = 3,
                gradleJvmArgs = "-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC",
                gradleParams = "--no-scan --build-cache",
                teamCityJvmArgs = "-Xmx5g -XX:MaxMetaspaceSize=1500m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"
            )
            ProjectComplexity.ULTRA_HEAVY -> ResourceRequirements(
                timeoutMinutes = 45,
                memoryMB = 8192,
                maxWorkers = 2,
                gradleJvmArgs = "-Xmx6g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC -XX:+UseStringDeduplication",
                gradleParams = "--no-scan --build-cache --no-parallel",
                teamCityJvmArgs = "-Xmx7g -XX:MaxMetaspaceSize=2g -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+HeapDumpOnOutOfMemoryError"
            )
        }
    }
}

data class ResourceRequirements(
    val timeoutMinutes: Int,
    val memoryMB: Int,
    val maxWorkers: Int,
    val gradleJvmArgs: String,
    val gradleParams: String,
    val teamCityJvmArgs: String
)

object BuildQueueManager {
    fun createStagedBuildConfiguration(samples: List<ExternalSampleConfig>): List<BuildStage> {
        val lightProjects = samples.filter { ResourceManager.getComplexityFor(it.projectName, it.specialHandling) == ProjectComplexity.LIGHT }
        val mediumProjects = samples.filter { ResourceManager.getComplexityFor(it.projectName, it.specialHandling) == ProjectComplexity.MEDIUM }
        val heavyProjects = samples.filter { ResourceManager.getComplexityFor(it.projectName, it.specialHandling) == ProjectComplexity.HEAVY }
        val ultraHeavyProjects = samples.filter { ResourceManager.getComplexityFor(it.projectName, it.specialHandling) == ProjectComplexity.ULTRA_HEAVY }

        return listOfNotNull(
            if (lightProjects.isNotEmpty()) BuildStage("Light Projects", lightProjects, 8, 0) else null,
            if (mediumProjects.isNotEmpty()) BuildStage("Medium Projects", mediumProjects, 4, 2) else null,
            if (heavyProjects.isNotEmpty()) BuildStage("Heavy Projects", heavyProjects, 2, 5) else null,
            if (ultraHeavyProjects.isNotEmpty()) BuildStage("Ultra Heavy Projects", ultraHeavyProjects, 1, 10) else null
        )
    }
}

data class BuildStage(
    val name: String,
    val projects: List<ExternalSampleConfig>,
    val maxConcurrency: Int,
    val stagingDelayMinutes: Int
)

fun BuildType.addEAPSampleFailureConditions(
    sampleName: String,
    specialHandling: List<SpecialHandling> = emptyList()
) {
    val complexity = ResourceManager.getComplexityFor(sampleName, specialHandling)
    val agentTier = AgentPoolManager.getRequiredAgentTier(complexity)

    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "No agents available to run"
            failureMessage = "No compatible agents found for $sampleName (${complexity.name} complexity, ${agentTier.name} tier required). Check agent pool availability."
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "There are no idle compatible agents"
            failureMessage = "All compatible agents are busy for $sampleName (${complexity.name} complexity). Consider reducing concurrent builds or adding more ${agentTier.name} tier agents."
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Build queue timeout"
            failureMessage = "Build timed out waiting for compatible agents for $sampleName. Required: ${agentTier.name} tier agents with ${complexity.name} complexity support."
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Agent disconnected"
            failureMessage = "Agent disconnected during $sampleName build. This may indicate resource exhaustion on ${agentTier.name} tier agents."
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Agent became unresponsive"
            failureMessage = "Agent became unresponsive during $sampleName build (${complexity.name} complexity). Check agent health and resource usage."
            stopBuildOnFailure = true
        }

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
            failureMessage = "Out of memory error in $sampleName. Project may need upgrade to higher agent tier (currently ${agentTier.name})."
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Gradle build daemon disappeared"
            failureMessage = "Gradle daemon crashed for $sampleName. Likely cause: insufficient resources on ${agentTier.name} tier agent."
            stopBuildOnFailure = true
        }

        if (DockerSupport.requiresDocker(specialHandling)) {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "docker: command not found"
                failureMessage = "Docker not available for $sampleName. Ensure agents support containerized builds."
                stopBuildOnFailure = true
            }

            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "Cannot connect to the Docker daemon"
                failureMessage = "Docker daemon not accessible for $sampleName. Check Docker service on ${agentTier.name} agents."
                stopBuildOnFailure = true
            }
        }

        if (AndroidSupport.requiresAndroidSDK(specialHandling)) {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "ANDROID_HOME"
                failureMessage = "Android SDK not configured for $sampleName. Ensure ${agentTier.name} agents have Android SDK installed."
                stopBuildOnFailure = true
            }
        }

        executionTimeoutMin = ResourceManager.getResourceRequirements(complexity).timeoutMinutes
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
    fun requiresDocker(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DOCKER_TESTCONTAINERS)
}

object DaggerSupport {
    fun requiresDagger(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)
}

object AndroidSupport {
    fun requiresAndroidSDK(specialHandling: List<SpecialHandling>): Boolean =
        specialHandling.contains(SpecialHandling.ANDROID_SDK_REQUIRED)
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

    fun BuildSteps.configureGradleProperties(requirements: ResourceRequirements) {
        script {
            name = "Configure Gradle Properties"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "Configuring Gradle JVM properties..."
                
                echo "org.gradle.jvmargs=${requirements.gradleJvmArgs}" >> gradle.properties
                echo "org.gradle.daemon=true" >> gradle.properties
                echo "org.gradle.parallel=true" >> gradle.properties
                echo "org.gradle.workers.max=${requirements.maxWorkers}" >> gradle.properties
                echo "org.gradle.configureondemand=true" >> gradle.properties
                
                echo "Final gradle.properties configuration:"
                grep -E "^org\.gradle\." gradle.properties || echo "No Gradle properties set"
                
                echo "Gradle properties configured successfully"
            """.trimIndent()
        }
    }

    fun BuildSteps.gradleEAPBuild(
        projectName: String,
        specialHandling: List<SpecialHandling> = emptyList()
    ) {
        val complexity = ResourceManager.getComplexityFor(projectName, specialHandling)
        val requirements = ResourceManager.getResourceRequirements(complexity)

        configureGradleProperties(requirements)

        when (complexity) {
            ProjectComplexity.ULTRA_HEAVY -> {
                ultraHeavyPhasedBuild(requirements)
            }
            ProjectComplexity.HEAVY -> {
                heavyOptimizedBuild(requirements)
            }
            ProjectComplexity.MEDIUM -> {
                mediumOptimizedBuild(requirements)
            }
            ProjectComplexity.LIGHT -> {
                lightOptimizedBuild(requirements)
            }
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

    private fun BuildSteps.ultraHeavyPhasedBuild(requirements: ResourceRequirements) {
        gradle {
            name = "Phase 1: Compile and Process Resources"
            tasks = "compileKotlin processResources --max-workers=1"
            jdkHome = Env.JDK_LTS
            gradleParams = "${requirements.gradleParams} --info"
            jvmArgs = requirements.teamCityJvmArgs
            useGradleWrapper = true
            enableStacktrace = true
        }

        gradle {
            name = "Phase 2: Test and Build"
            tasks = "test build --max-workers=1"
            jdkHome = Env.JDK_LTS
            gradleParams = "${requirements.gradleParams} --info"
            jvmArgs = requirements.teamCityJvmArgs
            useGradleWrapper = true
            enableStacktrace = true
        }
    }

    private fun BuildSteps.heavyOptimizedBuild(requirements: ResourceRequirements) {
        gradle {
            name = "Build Heavy Project"
            tasks = "build --max-workers=${requirements.maxWorkers}"
            jdkHome = Env.JDK_LTS
            gradleParams = "${requirements.gradleParams} --info"
            jvmArgs = requirements.teamCityJvmArgs
            useGradleWrapper = true
            enableStacktrace = true
        }
    }

    private fun BuildSteps.mediumOptimizedBuild(requirements: ResourceRequirements) {
        gradle {
            name = "Build Medium Project"
            tasks = "build --max-workers=${requirements.maxWorkers}"
            jdkHome = Env.JDK_LTS
            gradleParams = "${requirements.gradleParams} --info"
            jvmArgs = requirements.teamCityJvmArgs
            useGradleWrapper = true
            enableStacktrace = true
        }
    }

    private fun BuildSteps.lightOptimizedBuild(requirements: ResourceRequirements) {
        gradle {
            name = "Build Light Project"
            tasks = "build --max-workers=${requirements.maxWorkers}"
            jdkHome = Env.JDK_LTS
            gradleParams = "${requirements.gradleParams} --info"
            jvmArgs = requirements.teamCityJvmArgs
            useGradleWrapper = true
            enableStacktrace = true
        }
    }

    fun BuildSteps.amperEAPBuild() {
        script {
            name = "Build Amper Project"
            scriptContent = ExternalSampleScripts.buildAmperProjectEnhanced()
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

    fun buildAmperProjectEnhanced() = """
        #!/bin/bash
        set -e
        echo "Building Amper project with EAP dependencies..."
        
        echo "org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseG1GC" >> gradle.properties
        echo "org.gradle.daemon=true" >> gradle.properties
        echo "org.gradle.parallel=true" >> gradle.properties
        echo "org.gradle.workers.max=4" >> gradle.properties
        
        ./gradlew build --info --build-cache --no-scan
        
        echo "Amper project build completed"
    """.trimIndent()
}

interface ExternalEAPSampleConfig {
    val projectName: String
    fun createEAPBuildType(): BuildType
}

data class ExternalSampleConfig(
    override val projectName: String,
    val vcsRoot: VcsRoot,
    val buildType: ExternalSampleBuildType,
    val versionResolver: BuildType,
    val specialHandling: List<SpecialHandling>
) : ExternalEAPSampleConfig {

    override fun createEAPBuildType(): BuildType = BuildType {
        id("ExternalSample_${projectName.replace('-', '_')}")
        name = "EAP Sample: $projectName"

        val complexity = ResourceManager.getComplexityFor(projectName, specialHandling)
        val requirements = ResourceManager.getResourceRequirements(complexity)
        val agentTier = AgentPoolManager.getRequiredAgentTier(complexity)

        vcs {
            root(vcsRoot)
        }

        requirements {
            AgentPoolManager.configureAgentRequirements(this)

            if (DockerSupport.requiresDocker(specialHandling)) {
                contains("teamcity.agent.jvm.os.name", "Linux")
                exists("docker.server.version")
            }

            if (AndroidSupport.requiresAndroidSDK(specialHandling)) {
                exists("android.sdk.root")
            }
        }

        params {
            defaultGradleParams()
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            param("env.PROJECT_COMPLEXITY", complexity.name)
            param("env.AGENT_TIER", agentTier.name)
            param("env.MAX_WORKERS", requirements.maxWorkers.toString())
            param("env.GRADLE_JVM_ARGS", requirements.gradleJvmArgs)

            if (projectName == "full-stack-ktor-talk") {
                param("env.GOOGLE_CLIENT_ID", "placeholder-google-client-id")
            }
        }

        steps {
            with(EAPBuildSteps) {
                standardEAPSetup()

                if (DockerSupport.requiresDocker(specialHandling)) {
                    setupDockerEnvironment()
                }

                if (AndroidSupport.requiresAndroidSDK(specialHandling)) {
                    setupAndroidEnvironment()
                }

                if (DaggerSupport.requiresDagger(specialHandling)) {
                    setupDaggerEnvironment()
                }

                when (buildType) {
                    ExternalSampleBuildType.GRADLE -> {
                        gradleEAPBuild(projectName, specialHandling)
                    }
                    ExternalSampleBuildType.AMPER -> {
                        amperEAPBuild()
                    }
                }
            }
        }

        addEAPSampleFailureConditions(projectName, specialHandling)

        defaultBuildFeatures()

        features {
            with(EAPBuildFeatures) {
                addEAPSlackNotifications()
            }
        }

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
    description = "Resource-optimized validation with intelligent build queue management and flexible agent requirements"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
        param("resource.management.enabled", "true")
        param("build.queue.optimization.enabled", "true")
        param("staged.execution.enabled", "true")
        param("agent.resource.monitoring.enabled", "true")
        param("flexible.agent.assignment", "true")
    }

    val versionResolver = createVersionResolver()
    buildType(versionResolver)

    val samples = createSampleConfigurations(versionResolver)
    val buildStages = BuildQueueManager.createStagedBuildConfiguration(samples)

    val allBuildTypes = samples.map { it.createEAPBuildType() }
    allBuildTypes.forEach { buildType(it) }

    val buildTypesByProject = samples.zip(allBuildTypes).associate { (sample, buildType) ->
        sample.projectName to buildType
    }

    val stagesList = mutableListOf<BuildType>()
    buildStages.forEachIndexed { stageIndex, stage ->
        val stageBuildType = createStagedCompositeBuild(versionResolver, stage, stageIndex, buildTypesByProject)
        buildType(stageBuildType)
        stagesList.add(stageBuildType)
    }

    buildType(createMasterCompositeBuild(versionResolver, stagesList))
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

private fun createStagedCompositeBuild(
    versionResolver: BuildType,
    stage: BuildStage,
    stageIndex: Int,
    buildTypesByProject: Map<String, BuildType>
): BuildType = BuildType {
    id("ExternalSamplesStage_${stageIndex}")
    name = "Stage ${stageIndex + 1}: ${stage.name}"
    description = "Staged execution of ${stage.projects.size} projects with ${stage.maxConcurrency} max concurrency"
    type = BuildTypeSettings.Type.COMPOSITE

    val stageBuildTypes = stage.projects.mapNotNull { project ->
        buildTypesByProject[project.projectName]
    }

    dependencies {
        dependency(versionResolver) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }

        stageBuildTypes.forEach { buildType ->
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
            failureMessage = "No compatible agents found for ${stage.name}. Check agent pool configuration for projects requiring different tiers."
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "There are no idle compatible agents"
            failureMessage = "All compatible agents busy for ${stage.name}. Consider adding more agents or reducing concurrency."
            stopBuildOnFailure = true
        }

        executionTimeoutMin = stage.projects.maxOfOrNull { project ->
            val complexity = ResourceManager.getComplexityFor(project.projectName, project.specialHandling)
            ResourceManager.getResourceRequirements(complexity).timeoutMinutes
        }?.plus(10) ?: 30
    }

    features {
        with(EAPBuildFeatures) {
            addEAPSlackNotifications(includeSuccess = true)
        }
    }
}

private fun createMasterCompositeBuild(
    versionResolver: BuildType,
    stageBuildTypes: List<BuildType>
): BuildType = BuildType {
    id("ExternalSamplesMasterBuild")
    name = "EAP External Samples Master Build"
    description = "Master composite build with intelligent staging and agent pool management"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("env.GIT_BRANCH", "%teamcity.build.branch%")
        param("teamcity.build.skipDependencyBuilds", "true")
        param("teamcity.build.executionTimeoutMin", "60")
        param("teamcity.build.queueTimeout", "300")
    }

    dependencies {
        dependency(versionResolver) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }

        stageBuildTypes.forEach { stage ->
            dependency(stage) {
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
            failureMessage = "Critical: No compatible agents found for EAP validation. Check overall agent pool health and tier distribution."
            stopBuildOnFailure = true
        }

        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Build queue timeout"
            failureMessage = "EAP samples master build timed out. This indicates system-wide agent availability issues."
            stopBuildOnFailure = true
        }

        executionTimeoutMin = 60
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
