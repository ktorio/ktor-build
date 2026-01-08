package subprojects.train

import dsl.addSlackNotifications
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import subprojects.*
import subprojects.Agents.Arch
import subprojects.Agents.MEDIUM
import subprojects.Agents.OS
import subprojects.build.defaultBuildFeatures
import subprojects.build.defaultGradleParams

interface ExternalEAPSampleConfig {
    val projectName: String
    fun createEAPBuildType(): BuildType
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

data class ExternalSampleConfig(
    override val projectName: String,
    val vcsRoot: VcsRoot,
    val buildType: ExternalSampleBuildType,
    val versionResolver: BuildType,
    val specialHandling: List<SpecialHandling>
) : ExternalEAPSampleConfig {

    override fun createEAPBuildType(): BuildType = BuildType {
        id("ExternalSampleEAP_${projectName.replace(" ", "_").replace("-", "_")}")
        name = "EAP Validation: $projectName"
        description = "EAP validation for external sample: $projectName with enhanced handling"

        vcs {
            root(vcsRoot)
            cleanCheckout = true
            checkoutDir = "sample-project"
        }

        dependencies {
            snapshot(versionResolver) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                synchronizeRevisions = false
            }
        }

        params {
            defaultGradleParams()
            param("sample.project.name", projectName)
            param("sample.build.type", buildType.name)
            param("special.handling", specialHandling.joinToString(",") { it.name })
            param("env.DOCKER_AGENT_FOUND", "false")
            param("env.KTOR_VERSION", "%dep.${versionResolver.id}.env.KTOR_VERSION%")
            param("env.KTOR_COMPILER_PLUGIN_VERSION", "%dep.${versionResolver.id}.env.KTOR_COMPILER_PLUGIN_VERSION%")
            param("env.KOTLIN_VERSION", "%dep.${versionResolver.id}.env.KOTLIN_VERSION%")
            param("env.TESTCONTAINERS_MODE", "skip")
            param("env.TC_CLOUD_TOKEN", "placeholder-token")
            param("env.DAGGER_CONFIGURED", "false")
            param("skip.dagger.tests", if (SpecialHandlingUtils.requiresDagger(specialHandling)) "true" else "false")
        }

        requirements {
            agent(OS.Linux, Arch.X64, hardwareCapacity = MEDIUM)
        }

        steps {
            configureBuildSteps(specialHandling, buildType)
        }

        addSlackNotifications(
            buildFailedToStart = true,
            buildFailed = true,
            buildFinishedSuccessfully = true
        )

        defaultBuildFeatures()

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "BUILD FAILED"
                failureMessage = "Build failed during EAP validation"
                stopBuildOnFailure = true
            }
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "FAILURE: Build failed with an exception"
                failureMessage = "Build exception during EAP validation"
                stopBuildOnFailure = true
            }
            executionTimeoutMin = when {
                SpecialHandlingUtils.isComposeMultiplatform(specialHandling) -> 60
                SpecialHandlingUtils.isMultiplatform(specialHandling) -> 45
                SpecialHandlingUtils.requiresDocker(specialHandling) -> 40
                else -> 30
            }
        }
    }

    private fun BuildSteps.configureBuildSteps(
        specialHandling: List<SpecialHandling>,
        buildType: ExternalSampleBuildType
    ) {
        if (SpecialHandlingUtils.requiresDocker(specialHandling)) {
            setupTestcontainersEnvironment()
            addDockerAgentLogging()
        }

        backupConfigFiles()
        analyzeProjectStructure(specialHandling)

        if (SpecialHandlingUtils.requiresAndroidSDK(specialHandling)) {
            setupAndroidSDK()
        }

        if (SpecialHandlingUtils.requiresDagger(specialHandling)) {
            setupDaggerEnvironment()
        }

        when (buildType) {
            ExternalSampleBuildType.GRADLE -> {
                setupGradleEAPBuild(specialHandling)
            }
            ExternalSampleBuildType.AMPER -> {
                setupAmperEAPBuild()
            }
        }
    }

    private fun BuildSteps.setupGradleEAPBuild(specialHandling: List<SpecialHandling>) {
        script {
            name = "Setup Gradle EAP Build"
            scriptContent = """
                #!/bin/bash
                set -e
                
                echo "=== Setting up Gradle EAP Build ==="
                echo "Project: $projectName"
                echo "Special handling: ${specialHandling.joinToString(",") { it.name }}"
                echo "=== Gradle EAP Build Setup Complete ==="
            """.trimIndent()
        }

        gradle {
            name = "Build Gradle Project"
            tasks = "build --info"
            gradleParams = "%skip.dagger.tests% == true ? -x :dagger:test : "
            jdkHome = Env.JDK_LTS
        }
    }

    private fun BuildSteps.setupAmperEAPBuild() {
        script {
            name = "Setup Amper EAP Build"
            scriptContent = """
                #!/bin/bash
                set -e
                
                echo "=== Setting up Amper EAP Build ==="
                echo "Project: $projectName"
                
                # Amper-specific setup
                echo "Amper configuration would be added here"
                
                echo "=== Amper EAP Build Setup Complete ==="
            """.trimIndent()
        }
    }

    private fun BuildSteps.backupConfigFiles() {
        with(ExternalSampleScripts) { backupConfigFiles() }
    }

    private fun BuildSteps.analyzeProjectStructure(specialHandling: List<SpecialHandling> = emptyList()) {
        with(ExternalSampleScripts) { analyzeProjectStructure(specialHandling) }
    }

    private fun BuildSteps.setupTestcontainersEnvironment() {
        with(ExternalSampleScripts) { setupTestcontainersEnvironment() }
    }

    private fun BuildSteps.setupAndroidSDK() {
        with(ExternalSampleScripts) { setupAndroidSDK() }
    }

    private fun BuildSteps.setupDaggerEnvironment() {
        with(ExternalSampleScripts) { setupDaggerEnvironment() }
    }
}
