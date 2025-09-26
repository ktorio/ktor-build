package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import subprojects.*
import subprojects.build.*
import subprojects.build.samples.*

object EapConstants {
    const val PUBLISH_EAP_BUILD_TYPE_ID = "KtorPublish_AllEAP"
}

object TriggerProjectSamplesOnEAP : Project({
    id("TriggerProjectSamplesOnEAP")
    name = "EAP Validation"
    description = "Validate samples against EAP versions of Ktor"

    buildType {
        id("KtorEAPVersionResolver")
        name = "Set EAP Version for Tests"
        description = "Determines the EAP version to use for sample validation"

        vcs {
            root(VCSCoreEAP)
        }

        requirements {
            agent(Agents.OS.Linux, hardwareCapacity = Agents.ANY)
        }

        params {
            defaultGradleParams()
            param("env.KTOR_VERSION", "%dep.KtorPublish_AllEAP.build.number%")
            param("teamcity.build.skipDependencyBuilds", "true")
            param("teamcity.runAsFirstBuild", "true")
        }

        dependencies {
            artifacts(RelativeId(EapConstants.PUBLISH_EAP_BUILD_TYPE_ID)) {
                artifactRules = ""
                buildRule = lastSuccessful()
            }
        }

        failureConditions {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "[ERROR]"
                failureMessage = "Error detected in build log"
                stopBuildOnFailure = true
            }

            executionTimeoutMin = 15
        }
    }

    fun <T> createEAPSample(
        sample: T,
        prefix: String,
        createProject: (T) -> BuildType
    ): BuildType {
        return createProject(sample).apply {
            val projectName = when (sample) {
                is BuildPluginSampleSettings -> sample.projectName
                is SampleProjectSettings -> sample.projectName
                else -> throw IllegalArgumentException("Unsupported sample type")
            }

            id("EAP_${prefix}_${projectName.replace('-', '_')}")
            name = "EAP Validate $projectName sample"

            params {
                param("env.KTOR_VERSION", "%dep.KtorEAPVersionResolver.env.KTOR_VERSION%")
                param("teamcity.build.skipDependencyBuilds", "true")
            }

            dependencies {
                dependency(RelativeId("KtorEAPVersionResolver")) {
                    snapshot {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                        onDependencyCancel = FailureAction.FAIL_TO_START
                        reuseBuilds = ReuseBuilds.SUCCESSFUL
                    }
                }
            }
        }
    }

    fun createBuildPluginEAPSample(sample: BuildPluginSampleSettings): BuildType {
        val eapSample = BuildPluginSampleSettings(
            sample.projectName,
            VCSKtorBuildPluginsEAP,
            sample.standalone
        )

        return createEAPSample(eapSample, "KtorBuildPluginSamplesValidate") {
            BuildPluginSampleProject(it)
        }
    }

    fun createRegularEAPSample(sample: SampleProjectSettings): BuildType {
        val eapSample = SampleProjectSettings(
            sample.projectName,
            VCSSamples,
            sample.buildSystem,
            sample.standalone,
            sample.withAndroidSdk
        )

        return createEAPSample(eapSample, "KtorSamplesValidate") {
            SampleProject(it)
        }
    }

    val buildPluginEAPProjects = buildPluginSamples.map(::createBuildPluginEAPSample)
    val sampleEAPProjects = sampleProjects.map(::createRegularEAPSample)

    buildPluginEAPProjects.forEach(::buildType)
    sampleEAPProjects.forEach(::buildType)

    buildType {
        id("EAP_KtorBuildPluginSamplesValidate_All")
        name = "EAP Validate all build plugin samples"
        type = BuildTypeSettings.Type.COMPOSITE

        params {
            param("env.KTOR_VERSION", "%dep.KtorEAPVersionResolver.env.KTOR_VERSION%")
        }

        requirements {
            agent(Agents.OS.Linux, hardwareCapacity = Agents.MEDIUM)
        }

        dependencies {
            dependency(RelativeId("KtorEAPVersionResolver")) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                }
            }

            buildPluginEAPProjects.forEach { project ->
                project.id?.let { id ->
                    snapshot(id) {
                        onDependencyFailure = FailureAction.ADD_PROBLEM
                        onDependencyCancel = FailureAction.CANCEL
                    }
                }
            }
        }
    }

    buildType {
        id("EAP_KtorSamplesValidate_All")
        name = "EAP Validate all samples"
        type = BuildTypeSettings.Type.COMPOSITE

        params {
            param("env.KTOR_VERSION", "%dep.KtorEAPVersionResolver.env.KTOR_VERSION%")
        }

        requirements {
            agent(Agents.OS.Linux, hardwareCapacity = Agents.MEDIUM)
            equals("env.ANDROID_HOME", "%android-sdk.location%")
        }

        dependencies {
            dependency(RelativeId("KtorEAPVersionResolver")) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                }
            }

            sampleEAPProjects.forEach { project ->
                project.id?.let { id ->
                    snapshot(id) {
                        onDependencyFailure = FailureAction.ADD_PROBLEM
                        onDependencyCancel = FailureAction.CANCEL
                    }
                }
            }
        }
    }

    buildType {
        id("KtorEAPSamplesCompositeBuild")
        name = "Validate All Samples with EAP"
        description = "Run all samples against the EAP version of Ktor"
        type = BuildTypeSettings.Type.COMPOSITE

        params {
            defaultGradleParams()
            param("env.GIT_BRANCH", "%teamcity.build.branch%")
            param("env.KTOR_VERSION", "%dep.KtorEAPVersionResolver.env.KTOR_VERSION%")
            param("teamcity.build.skipDependencyBuilds", "true")
        }

        dependencies {
            dependency(RelativeId("KtorEAPVersionResolver")) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                }
            }

            dependency(RelativeId("EAP_KtorBuildPluginSamplesValidate_All")) {
                snapshot {
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                }
            }

            dependency(RelativeId("EAP_KtorSamplesValidate_All")) {
                snapshot {
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                }
            }
        }

        features {
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
                buildFinishedSuccessfully = true
            }
        }
    }
})
