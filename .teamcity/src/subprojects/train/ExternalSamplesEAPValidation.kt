package subprojects.train

import dsl.addSlackNotifications
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.build.defaultGradleParams

object ExternalSamplesEAPValidation : Project({
    id("ExternalSamplesEAPValidation")
    name = "External Samples EAP Validation"
    description = "Enhanced validation of external GitHub samples against EAP versions of Ktor with Testcontainers Cloud support"

    registerVCSRoots()

    params {
        param("ktor.eap.version", "KTOR_VERSION")
        param("enhanced.validation.enabled", "true")
        param("toml.comprehensive.handling", "true")
        param("configuration.preservation.enabled", "true")
        param("special.handling.enabled", "true")
        param("compose.multiplatform.support", "true")
        param("testcontainers.cloud.enabled", "true")
        password("testcontainers-cloud-token", "credentialsJSON:your-testcontainers-cloud-token-id")

        // Quality Gate Parameters
        param("quality.gate.enabled", "true")
        param("quality.gate.type", "EXTERNAL_VALIDATION")
        param("quality.gate.thresholds.minimum.score", "80")
        param("quality.gate.thresholds.critical.issues", "0")
        param("quality.gate.thresholds.warning.issues", "5")
        param("quality.gate.notification.enhanced", "true")
        param("quality.gate.notification.channel.main", "#ktor-projects-on-eap")
        param("quality.gate.notification.channel.alerts", "#ktor-projects-on-eap")
        param("quality.gate.execution.timeout.minutes", "60")
    }

    val versionResolver = createVersionResolver()
    buildType(versionResolver)

    val samples = createSampleConfigurations(versionResolver)
    val buildTypes = samples.map { it.createEAPBuildType() }

    buildTypes.forEach { buildType(it) }
    val compositeBuild = createCompositeBuild(versionResolver, buildTypes)
    buildType(compositeBuild)

    // Add Quality Gate Orchestrator
    val qualityGateOrchestrator = QualityGateOrchestrator.createQualityGateOrchestrator(
        externalValidationBuild = compositeBuild,
        internalValidationBuild = compositeBuild,
        versionResolver = versionResolver
    )
    buildType(qualityGateOrchestrator)
})

private fun Project.registerVCSRoots() {
    vcsRoot(VCSKtorAiServer)
    vcsRoot(VCSKtorNativeServer)
    vcsRoot(VCSKtorConfigExample)
    vcsRoot(VCSKtorWorkshop2025)
    vcsRoot(VCSAmperKtorSample)
    vcsRoot(VCSKtorDIOverview)
    vcsRoot(VCSKtorFullStackRealWorld)
}

private fun createVersionResolver(): BuildType = EAPVersionResolver.createVersionResolver(
    id = "ExternalSamplesEAPVersionResolver",
    name = "External Samples EAP Version Resolver",
    description = "Resolves EAP versions for external sample validation"
)

private fun createSampleConfigurations(versionResolver: BuildType): List<ExternalEAPSampleConfig> = listOf(
    EAPSampleBuilder("Ktor AI Server", VCSKtorAiServer, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("Ktor Native Server", VCSKtorNativeServer, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.KOTLIN_MULTIPLATFORM)
        .build(),

    EAPSampleBuilder("Ktor Config Example", VCSKtorConfigExample, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("Ktor Workshop 2025", VCSKtorWorkshop2025, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.DOCKER_TESTCONTAINERS)
        .build(),

    EAPSampleBuilder("Amper Ktor Sample", VCSAmperKtorSample, versionResolver)
        .withBuildType(ExternalSampleBuildType.AMPER)
        .withSpecialHandling(SpecialHandling.AMPER_GRADLE_HYBRID)
        .build(),

    EAPSampleBuilder("Ktor DI Overview", VCSKtorDIOverview, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(SpecialHandling.DAGGER_ANNOTATION_PROCESSING)
        .build(),

    EAPSampleBuilder("Ktor Full Stack Real World", VCSKtorFullStackRealWorld, versionResolver)
        .withBuildType(ExternalSampleBuildType.GRADLE)
        .withSpecialHandling(
            SpecialHandling.KOTLIN_MULTIPLATFORM,
            SpecialHandling.DOCKER_TESTCONTAINERS,
            SpecialHandling.COMPOSE_MULTIPLATFORM,
            SpecialHandling.DAGGER_ANNOTATION_PROCESSING
        )
        .build()
)

private fun createCompositeBuild(versionResolver: BuildType, buildTypes: List<BuildType>): BuildType = BuildType {
    id("ExternalSamplesEAPCompositeBuild")
    name = "External Samples EAP Validation - All Samples"
    description = "Composite build that runs all external sample validations"
    type = BuildTypeSettings.Type.COMPOSITE

    params {
        defaultGradleParams()
        param("teamcity.build.skipDependencyBuilds", "true")
    }

    addSlackNotifications()

    triggers {
        finishBuildTrigger {
            buildType = "KtorPublish_AllEAP"
            successfulOnly = true
            branchFilter = "+:refs/heads/*"
        }
    }

    dependencies {
        snapshot(versionResolver) {
            onDependencyFailure = FailureAction.FAIL_TO_START
            synchronizeRevisions = false
        }

        buildTypes.forEach { buildType ->
            snapshot(buildType) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                synchronizeRevisions = false
            }
        }
    }

    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "No agents available"
            failureMessage = "No compatible agents found for external samples EAP validation"
            stopBuildOnFailure = true
        }
        executionTimeoutMin = 60
    }
}
