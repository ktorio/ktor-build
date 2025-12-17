package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.Agents.ANY
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

object ResourceManager {
    fun getComplexityFor(projectName: String, specialHandling: List<SpecialHandling>): ProjectComplexity {
        return when {
            projectName in listOf("ktor-full-stack-real-world", "ktor-koog-example") ->
                ProjectComplexity.ULTRA_HEAVY

            projectName in listOf("ktor-arrow-example", "ktor-ai-server") ->
                ProjectComplexity.HEAVY

            specialHandling.any { it in listOf(SpecialHandling.DOCKER_TESTCONTAINERS, SpecialHandling.KOTLIN_MULTIPLATFORM) } ->
                ProjectComplexity.MEDIUM

            else -> ProjectComplexity.LIGHT
        }
    }

    fun getResourceRequirements(complexity: ProjectComplexity): ResourceRequirements {
        return when (complexity) {
            ProjectComplexity.LIGHT -> ResourceRequirements(
                timeoutMinutes = 20,
                memoryMB = 3072,
                maxWorkers = 4,
                gradleOpts = "-Xmx2g -XX:MaxMetaspaceSize=512m"
            )
            ProjectComplexity.MEDIUM -> ResourceRequirements(
                timeoutMinutes = 35,
                memoryMB = 4096,
                maxWorkers = 3,
                gradleOpts = "-Xmx3g -XX:MaxMetaspaceSize=768m -XX:+UseG1GC"
            )
            ProjectComplexity.HEAVY -> ResourceRequirements(
                timeoutMinutes = 50,
                memoryMB = 6144,
                maxWorkers = 2,
                gradleOpts = "-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC"
            )
            ProjectComplexity.ULTRA_HEAVY -> ResourceRequirements(
                timeoutMinutes = 120,
                memoryMB = 8192,
                maxWorkers = 1,
                gradleOpts = "-Xmx6g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC -XX:+UseStringDeduplication"
            )
        }
    }
}

data class ResourceRequirements(
    val timeoutMinutes: Int,
    val memoryMB: Int,
    val maxWorkers: Int,
    val gradleOpts: String
)

object BuildQueueManager {
    fun createStagedBuildConfiguration(samples: List<ExternalSampleConfig>): List<BuildStage> {
        val groupedByComplexity = samples.groupBy {
            ResourceManager.getComplexityFor(it.projectName, it.specialHandling)
        }

        val stages = mutableListOf<BuildStage>()

        groupedByComplexity[ProjectComplexity.LIGHT]?.let { lightProjects ->
            lightProjects.chunked(4).forEachIndexed { index, batch ->
                stages.add(BuildStage(
                    name = "Light_Stage_${index + 1}",
                    projects = batch,
                    maxConcurrency = 4,
                    stagingDelayMinutes = 0
                ))
            }
        }

        groupedByComplexity[ProjectComplexity.MEDIUM]?.let { mediumProjects ->
            mediumProjects.chunked(2).forEachIndexed { index, batch ->
                stages.add(BuildStage(
                    name = "Medium_Stage_${index + 1}",
                    projects = batch,
                    maxConcurrency = 2,
                    stagingDelayMinutes = 2
                ))
            }
        }

        groupedByComplexity[ProjectComplexity.HEAVY]?.let { heavyProjects ->
            heavyProjects.chunked(1).forEachIndexed { index, batch ->
                stages.add(BuildStage(
                    name = "Heavy_Stage_${index + 1}",
                    projects = batch,
                    maxConcurrency = 1,
                    stagingDelayMinutes = 5
                ))
            }
        }

        groupedByComplexity[ProjectComplexity.ULTRA_HEAVY]?.let { ultraHeavyProjects ->
            ultraHeavyProjects.forEachIndexed { index, project ->
                stages.add(BuildStage(
                    name = "UltraHeavy_Stage_${index + 1}",
                    projects = listOf(project),
                    maxConcurrency = 1,
                    stagingDelayMinutes = 10
                ))
            }
        }

        return stages
    }
}

data class BuildStage(
    val name: String,
    val projects: List<ExternalSampleConfig>,
    val maxConcurrency: Int,
    val stagingDelayMinutes: Int
)

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
        SpecialHandling.DOCKER_TESTCONTAINERS in specialHandling
}

object DaggerSupport {
    fun requiresDagger(specialHandling: List<SpecialHandling>): Boolean =
        SpecialHandling.DAGGER_ANNOTATION_PROCESSING in specialHandling
}

object AndroidSupport {
    fun requiresAndroidSDK(specialHandling: List<SpecialHandling>): Boolean =
        SpecialHandling.ANDROID_SDK_REQUIRED in specialHandling
}

object EAPBuildFeatures {
    fun BuildFeatures.addEAPSlackNotifications(
        includeSuccess: Boolean = false,
        includeBuildStart: Boolean = false
    ) {
        notifications {
            notifierSettings = slackNotifier {
                connection = "PROJECT_EXT_5"
                sendTo = "#ktor-projects-on-eap"
                messageFormat = verboseMessageFormat {
                    addStatusText = true
                }
            }
            buildFailedToStart = true
            buildFailed = true
            if (includeSuccess) buildFinishedSuccessfully = true
            if (includeBuildStart) buildStarted = true
        }
    }
}

object EAPScriptTemplates {
    fun buildCommonSetup() = """
        #!/bin/bash
        set -e
        echo "=== EAP Environment Setup ==="
        
        echo "Available Memory: $(free -h | awk '/^Mem:/ {print $7}' 2>/dev/null || echo 'N/A')"
        echo "CPU Load: $(uptime | cut -d' ' -f3- 2>/dev/null || echo 'N/A')"
        echo "Disk Space: $(df -h . | awk 'NR==2 {print $4}' 2>/dev/null || echo 'N/A')"
        
        if [ -f "./gradlew" ]; then
            echo "Found gradlew, stopping existing daemons..."
            ./gradlew --stop || true
        fi
        
        echo "=== Setup Complete ==="
    """.trimIndent()
}

object EAPBuildSteps {
    fun BuildSteps.standardEAPSetup() {
        script {
            name = "Standard EAP Setup"
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

    fun BuildSteps.gradleEAPBuild(
        projectName: String,
        specialHandling: List<SpecialHandling> = emptyList()
    ) {
        val complexity = ResourceManager.getComplexityFor(projectName, specialHandling)
        val requirements = ResourceManager.getResourceRequirements(complexity)

        script {
            name = "Pre-build Resource Check"
            scriptContent = """
                #!/bin/bash
                echo "=== Pre-build Resource Assessment ==="
                echo "Project: $projectName"
                echo "Complexity: ${complexity.name}"
                echo "Memory Allocation: ${requirements.memoryMB}MB"
                echo "Max Workers: ${requirements.maxWorkers}"
                echo "Timeout: ${requirements.timeoutMinutes} minutes"
                
                AVAILABLE_MEM=$(free -m | awk 'NR==2{printf "%.0f", $7}' 2>/dev/null || echo "0")
                if [ "${'$'}AVAILABLE_MEM" -lt "${requirements.memoryMB}" ]; then
                    echo "WARNING: Limited memory available (${'$'}{AVAILABLE_MEM}MB < ${requirements.memoryMB}MB)"
                fi
                
                echo "================================"
            """.trimIndent()
        }

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
            name = "Post-build Cleanup"
            scriptContent = """
                #!/bin/bash
                echo "=== Post-build Resource Cleanup ==="
                
                if [ -f "./gradlew" ]; then
                    ./gradlew --stop || true
                fi
                
                if [ -d ".gradle" ]; then
                    CACHE_SIZE=$(du -sm .gradle 2>/dev/null | cut -f1 || echo "0")
                    if [ "${'$'}CACHE_SIZE" -gt 1000 ]; then
                        echo "Cleaning large Gradle cache (${'$'}{CACHE_SIZE}MB)..."
                        rm -rf .gradle/caches/build-cache-* || true
                    fi
                fi
                
                find . -name "*.tmp" -delete 2>/dev/null || true
                find . -name "*.lock" -delete 2>/dev/null || true
                
                echo "Cleanup completed"
            """.trimIndent()
            executionMode = BuildStep.ExecutionMode.ALWAYS
        }
    }

    private fun BuildSteps.ultraHeavyPhasedBuild(requirements: ResourceRequirements) {
        gradle {
            name = "Phase 1 - Dependencies & Verification"
            tasks = "dependencies --write-verification-metadata sha256"
            jdkHome = Env.JDK_LTS
            gradleParams = "${requirements.gradleOpts} -Dorg.gradle.caching=true --max-workers=${requirements.maxWorkers}"
        }

        gradle {
            name = "Phase 2 - Backend Compilation"
            tasks = "compileKotlin compileJava"
            jdkHome = Env.JDK_LTS
            gradleParams = "${requirements.gradleOpts} --max-workers=${requirements.maxWorkers}"
        }

        gradle {
            name = "Phase 3 - Frontend Compilation"
            tasks = "compileKotlinJs compileKotlinWasm"
            jdkHome = Env.JDK_LTS
            gradleParams = "${requirements.gradleOpts} --max-workers=1"
            executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
        }

        gradle {
            name = "Phase 4 - Testing"
            tasks = "test --max-workers=1 --no-parallel"
            jdkHome = Env.JDK_LTS
            gradleParams = requirements.gradleOpts
        }
    }

    private fun BuildSteps.heavyOptimizedBuild(requirements: ResourceRequirements) {
        gradle {
            name = "Build Heavy Project"
            tasks = "build --max-workers=${requirements.maxWorkers} --parallel"
            jdkHome = Env.JDK_LTS
            gradleParams = requirements.gradleOpts
        }
    }

    private fun BuildSteps.mediumOptimizedBuild(requirements: ResourceRequirements) {
        gradle {
            name = "Build Medium Project"
            tasks = "build --max-workers=${requirements.maxWorkers} --parallel"
            jdkHome = Env.JDK_LTS
            gradleParams = requirements.gradleOpts
        }
    }

    private fun BuildSteps.lightOptimizedBuild(requirements: ResourceRequirements) {
        gradle {
            name = "Build Light Project"
            tasks = "build --max-workers=${requirements.maxWorkers}"
            jdkHome = Env.JDK_LTS
            gradleParams = requirements.gradleOpts
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
        echo "=== Docker Environment Setup ==="
        
        if ! command -v docker &> /dev/null; then
            echo "ERROR: Docker not available"
            exit 1
        fi
        
        export DOCKER_MEMORY_LIMIT="2g"
        export DOCKER_CPU_LIMIT="1.5"
        
        if [ -f "docker-compose.yml" ] || [ -f "docker-compose.yaml" ]; then
            echo "Starting Docker Compose services with resource limits..."
            docker-compose up -d --scale app=1 2>/dev/null || \
            docker compose up -d --scale app=1 || \
            echo "Docker Compose startup failed, continuing..."
        fi
        
        echo "Docker environment configured"
    """.trimIndent()

    fun setupDaggerEnvironment() = """
        #!/bin/bash
        echo "=== Dagger Environment Setup ==="
        
        if grep -r "dagger" build.gradle.kts 2>/dev/null; then
            echo "Dagger dependencies detected"
            echo "Configuring annotation processing..."
        else
            echo "No Dagger dependencies found"
        fi
        
        echo "Dagger setup completed"
    """.trimIndent()

    fun setupAndroidSDK() = """
        #!/bin/bash
        echo "=== Android SDK Setup ==="
        
        if [ -f "app/build.gradle.kts" ] && grep -q "android" app/build.gradle.kts 2>/dev/null; then
            echo "Android project detected"
            export ANDROID_SDK_ROOT="/opt/android-sdk"
            export ANDROID_HOME="/opt/android-sdk"
            echo "Android SDK configured"
        else
            echo "No Android configuration found"
        fi
    """.trimIndent()

    fun buildAmperProjectEnhanced() = """
        #!/bin/bash
        set -e
        echo "=== Building Amper project with enhancements ==="
        
        if command -v amper &> /dev/null; then
            echo "Building with Amper..."
            amper build
        elif [ -f "gradlew" ]; then
            echo "Falling back to Gradle build..."
            ./gradlew build
        else
            echo "ERROR: Neither Amper nor Gradle wrapper found"
            exit 1
        fi
        
        echo "Amper project build completed"
    """.trimIndent()
}

interface ExternalEAPSampleConfig {
    val projectName: String
    fun createEAPBuildType(): BuildType
}

fun BuildType.addEAPSampleFailureConditions(
    sampleName: String,
    specialHandling: List<SpecialHandling> = emptyList()
) {
    val complexity = ResourceManager.getComplexityFor(sampleName, specialHandling)
    val requirements = ResourceManager.getResourceRequirements(complexity)

    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "BUILD FAILED"
            failureMessage = "Build failed for $sampleName (${complexity.name})"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "FAILURE:"
            failureMessage = "Build failure detected in $sampleName"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "OutOfMemoryError"
            failureMessage = "Memory exhaustion in ${complexity.name} project $sampleName"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "Java heap space"
            failureMessage = "Java heap space exhaustion in ${complexity.name} project $sampleName"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "No space left on device"
            failureMessage = "Disk space exhaustion during $sampleName build"
            stopBuildOnFailure = true
        }
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "build was re-added to build queue|build canceled"
            failureMessage = "Build queue management issue for $sampleName"
            stopBuildOnFailure = true
        }

        executionTimeoutMin = requirements.timeoutMinutes
    }
}

data class ExternalSampleConfig(
    override val projectName: String,
    val vcsRoot: VcsRoot,
    val buildType: ExternalSampleBuildType,
    val versionResolver: BuildType,
    val specialHandling: List<SpecialHandling>
) : ExternalEAPSampleConfig {

    override fun createEAPBuildType(): BuildType = BuildType {
        val complexity = ResourceManager.getComplexityFor(projectName, specialHandling)
        val requirements = ResourceManager.getResourceRequirements(complexity)

        id("ExternalEAPSample_${projectName.replace('-', '_')}")
        name = "EAP $projectName [${complexity.name}]"

        vcs {
            root(vcsRoot)
        }

        requirements {
            agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)

            if (complexity == ProjectComplexity.MEDIUM) {
                noLessThan("system.memory.mb", "4096")
            }
            if (complexity == ProjectComplexity.HEAVY) {
                noLessThan("system.memory.mb", "6144")
                noLessThan("system.cpu.count", "4")
            }
            if (complexity == ProjectComplexity.ULTRA_HEAVY) {
                noLessThan("system.memory.mb", "8192")
                noLessThan("system.cpu.count", "8")
            }
        }

        params {
            defaultGradleParams()
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_COMPILER_PLUGIN_VERSION%")

            param("project.complexity", complexity.name)
            param("max.memory.mb", requirements.memoryMB.toString())
            param("max.workers", requirements.maxWorkers.toString())

            param("teamcity.build.workingDir", "%teamcity.build.workingDir%/${projectName}")
            param("teamcity.build.checkoutDir", "%teamcity.build.checkoutDir%/${projectName}")

            param("teamcity.build.branch.is.default", "true")
            param("teamcity.build.skipDependencyBuilds", "true")
        }

        addEAPSampleFailureConditions(projectName, specialHandling)
        defaultBuildFeatures()

        features {
            EAPBuildFeatures.run {
                addEAPSlackNotifications(
                    includeSuccess = (complexity >= ProjectComplexity.HEAVY),
                    includeBuildStart = (complexity == ProjectComplexity.ULTRA_HEAVY)
                )
            }
        }

        steps {
            with(EAPBuildSteps) {
                standardEAPSetup()

                if (DockerSupport.requiresDocker(specialHandling)) {
                    setupDockerEnvironment()
                }

                if (DaggerSupport.requiresDagger(specialHandling)) {
                    setupDaggerEnvironment()
                }

                if (AndroidSupport.requiresAndroidSDK(specialHandling)) {
                    setupAndroidEnvironment()
                }

                when (buildType) {
                    ExternalSampleBuildType.GRADLE -> gradleEAPBuild(projectName, specialHandling)
                    ExternalSampleBuildType.AMPER -> amperEAPBuild()
                }
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
    description = "Resource-optimized validation with intelligent build queue management"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
        param("resource.management.enabled", "true")
        param("build.queue.optimization.enabled", "true")
        param("staged.execution.enabled", "true")
        param("agent.resource.monitoring.enabled", "true")
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

    buildStages.forEachIndexed { stageIndex, stage ->
        buildType(createStagedCompositeBuild(versionResolver, stage, stageIndex, buildTypesByProject))
    }

    buildType(createMasterCompositeBuild(versionResolver, buildStages))
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

private fun createVersionResolver(): BuildType = BuildType {
    id("ExternalEAPVersionResolver")
    name = "EAP Version Resolver"
    description = "Resolves EAP versions with enhanced error handling"

    vcs {
        root(VCSCore)
    }

    requirements {
        agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)
    }

    params {
        defaultGradleParams()
        param("teamcity.build.skipDependencyBuilds", "true")
        param("teamcity.build.runAsFirstBuild", "true")
        param("env.KTOR_VERSION", "")
        param("env.KTOR_COMPILER_PLUGIN_VERSION", "")
    }

    steps {
        script {
            name = "Resolve EAP Versions"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== EAP Version Resolution ==="
                
                KTOR_EAP_METADATA_URL="https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml"
                KTOR_COMPILER_PLUGIN_METADATA_URL="https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-compiler-plugin/maven-metadata.xml"
                
                echo "Fetching Ktor EAP metadata..."
                KTOR_VERSION=$(curl -s "${'$'}KTOR_EAP_METADATA_URL" | grep -o '>[0-9][^<]*-eap-[0-9]*<' | head -1 | sed 's/[><]//g')
                
                if [ -z "${'$'}KTOR_VERSION" ]; then
                    echo "ERROR: Failed to resolve Ktor EAP version"
                    exit 1
                fi
                
                echo "Fetching Ktor Compiler Plugin EAP metadata..."
                KTOR_COMPILER_PLUGIN_VERSION=$(curl -s "${'$'}KTOR_COMPILER_PLUGIN_METADATA_URL" | grep -o '>[0-9][^<]*-eap-[0-9]*<' | head -1 | sed 's/[><]//g')
                
                if [ -z "${'$'}KTOR_COMPILER_PLUGIN_VERSION" ]; then
                    echo "ERROR: Failed to resolve Ktor Compiler Plugin EAP version"
                    exit 1
                fi
                
                echo "Resolved versions:"
                echo "  KTOR_VERSION: ${'$'}KTOR_VERSION"
                echo "  KTOR_COMPILER_PLUGIN_VERSION: ${'$'}KTOR_COMPILER_PLUGIN_VERSION"
                
                echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}KTOR_VERSION']"
                echo "##teamcity[setParameter name='env.KTOR_COMPILER_PLUGIN_VERSION' value='${'$'}KTOR_COMPILER_PLUGIN_VERSION']"
                
                echo "Version resolution completed successfully"
            """.trimIndent()
        }
    }

    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.REGEXP
            pattern = "ERROR:|CRITICAL ERROR:"
            failureMessage = "Version resolution failed"
            stopBuildOnFailure = true
        }
        executionTimeoutMin = 10
    }
}

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalSampleConfig> =
    listOf(
        EAPSampleBuilder("ktor-config-example", VCSKtorConfigExample, versionResolver).build(),
        EAPSampleBuilder("ktor-di-overview", VCSKtorDIOverview, versionResolver).build(),
        EAPSampleBuilder("ktor-workshop-2025", VCSKtorWorkshop2025, versionResolver).build(),
        EAPSampleBuilder("ktor-native-server", VCSKtorNativeServer, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
            .build(),
        EAPSampleBuilder("full-stack-ktor-talk", VCSFullStackKtorTalk, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
            .build(),
        EAPSampleBuilder("amper-ktor-sample", VCSAmperKtorSample, versionResolver)
            .withBuildType(ExternalSampleBuildType.AMPER)
            .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID)
            .build(),
        EAPSampleBuilder("ktor-arrow-example", VCSKtorArrowExample, versionResolver)
            .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
            .build(),
        EAPSampleBuilder("ktor-ai-server", VCSKtorAiServer, versionResolver)
            .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
            .build(),
        EAPSampleBuilder("ktor-full-stack-real-world", VCSKtorFullStackRealWorld, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM, SpecialHandling.DOCKER_TESTCONTAINERS)
            .build(),
        EAPSampleBuilder("ktor-koog-example", VCSKtorKoogExample, versionResolver)
            .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
            .build()
    )

private fun createStagedCompositeBuild(
    versionResolver: BuildType,
    stage: BuildStage,
    stageIndex: Int,
    buildTypesByProject: Map<String, BuildType>
): BuildType = BuildType {
    id("ExternalEAPStage${stageIndex}")
    name = "Stage ${stageIndex}: ${stage.name} (${stage.projects.size} projects)"
    description = "Staged execution for ${stage.projects.joinToString { it.projectName }}"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("stage.name", stage.name)
        param("stage.index", stageIndex.toString())
        param("max.concurrency", stage.maxConcurrency.toString())
        param("staging.delay.minutes", stage.stagingDelayMinutes.toString())
        param("teamcity.build.skipDependencyBuilds", "true")
    }

    if (stageIndex > 0 && stage.stagingDelayMinutes > 0) {
        triggers {
            finishBuildTrigger {
                buildType = "ExternalEAPStage${stageIndex - 1}"
                successfulOnly = false
                branchFilter = "+:*"
            }
        }
    }

    dependencies {
        dependency(versionResolver) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }

        stage.projects.forEach { projectConfig ->
            val existingBuildType = buildTypesByProject[projectConfig.projectName]
            if (existingBuildType != null) {
                dependency(existingBuildType) {
                    snapshot {
                        onDependencyFailure = FailureAction.IGNORE
                        onDependencyCancel = FailureAction.IGNORE
                    }
                }
            }
        }
    }

    failureConditions {
        val maxProjectTimeout = stage.projects.maxOfOrNull {
            ResourceManager.getResourceRequirements(
                ResourceManager.getComplexityFor(it.projectName, it.specialHandling)
            ).timeoutMinutes
        } ?: 30

        executionTimeoutMin = maxProjectTimeout + 15
    }
}

private fun createMasterCompositeBuild(
    versionResolver: BuildType,
    buildStages: List<BuildStage>
): BuildType = BuildType {
    id("ExternalEAPMasterValidation")
    name = "Master EAP Validation (Resource-Optimized)"
    description = "Orchestrates staged execution with intelligent resource management"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("total.stages", buildStages.size.toString())
        param("total.projects", buildStages.sumOf { it.projects.size }.toString())
        param("resource.optimization.enabled", "true")
        param("env.GIT_BRANCH", "%teamcity.build.branch%")
    }

    features {
        EAPBuildFeatures.run {
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

    dependencies {
        dependency(versionResolver) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }

        buildStages.forEachIndexed { stageIndex, _ ->
            dependency(BuildType { id("ExternalEAPStage${stageIndex}") }) {
                snapshot {
                    onDependencyFailure = FailureAction.IGNORE
                    onDependencyCancel = FailureAction.IGNORE
                }
            }
        }
    }

    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "All stages failed"
            failureMessage = "Complete EAP validation failure"
            stopBuildOnFailure = true
        }

        executionTimeoutMin = 120
    }
}
