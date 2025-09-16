
package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.build.*
import subprojects.build.core.TriggerType
import subprojects.build.core.createCompositeBuild
import subprojects.build.samples.*
import subprojects.eap.ProjectPublishEAPToSpace

object TriggerProjectSamplesOnEAP : Project({
    id("TriggerProjectSamplesOnEAP")
    name = "EAP Validation"
    description = "Validate samples against EAP versions of Ktor"

    val publishEAPId = "${ProjectPublishEAPToSpace.id}_PublishEAPToSpace"
    val eapVersionParam = "%dep.${publishEAPId}.build.number%"

    fun BuildType.configureForEap() {
        artifactRules = "lib/** => ."
        params {
            param("env.KTOR_VERSION", eapVersionParam)
        }

        dependencies {
            dependency(RelativeId(publishEAPId)) {
                snapshot {}
                artifacts {
                    artifactRules = "ktor-*.jar => lib/"
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

        return BuildPluginSampleProject(eapSample).apply {
            id("EAP_KtorBuildPluginSamplesValidate_${sample.projectName.replace('-', '_')}")
            name = "EAP Validate ${sample.projectName} sample"
            configureForEap()
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

        return SampleProject(eapSample).apply {
            id("EAP_KtorSamplesValidate_${sample.projectName.replace('-', '_')}")
            name = "EAP Validate ${sample.projectName} sample"
            configureForEap()
        }
    }

    val buildPluginEAPProjects = buildPluginSamples.map(::createBuildPluginEAPSample)
    val sampleEAPProjects = sampleProjects.map(::createRegularEAPSample)

    buildPluginEAPProjects.forEach(::buildType)
    sampleEAPProjects.forEach(::buildType)

    buildType {
        createCompositeBuild(
            "EAP_KtorBuildPluginSamplesValidate_All",
            "EAP Validate all build plugin samples",
            VCSKtorBuildPluginsEAP,
            buildPluginEAPProjects,
            withTrigger = TriggerType.NONE
        )
        configureForEap()
    }

    buildType {
        createCompositeBuild(
            "EAP_KtorSamplesValidate_All",
            "EAP Validate all samples",
            VCSSamples,
            sampleEAPProjects,
            withTrigger = TriggerType.NONE
        )
        configureForEap()
    }

    buildType {
        id("KtorEAPSamplesCompositeBuild")
        name = "Validate All Samples with EAP"
        description = "Run all samples against the EAP version of Ktor"
        type = BuildTypeSettings.Type.COMPOSITE

        vcs {
            root(VCSCoreEAP)
        }

        params {
            defaultGradleParams()
            param("env.GIT_BRANCH", "%teamcity.build.branch%")
            param("env.VERSION_SUFFIX", eapVersionParam)
        }

        triggers {
            finishBuildTrigger {
                buildType = publishEAPId
                successfulOnly = true
                branchFilter = """
                    +:*-eap
                    +:eap/*
                """.trimIndent()
            }
        }

        artifactRules = "lib/** => ."

        dependencies {
            dependency(RelativeId(publishEAPId)) {
                snapshot {}
                artifacts {
                    artifactRules = "ktor-*.jar => lib/"
                }
            }

            dependency(RelativeId("EAP_KtorBuildPluginSamplesValidate_All")) {
                snapshot {}
            }

            dependency(RelativeId("EAP_KtorSamplesValidate_All")) {
                snapshot {}
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
