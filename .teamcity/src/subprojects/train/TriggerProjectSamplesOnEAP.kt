package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.build.*
import subprojects.build.core.TriggerType
import subprojects.build.core.createCompositeBuild
import subprojects.build.samples.BuildPluginSampleProject
import subprojects.build.samples.BuildPluginSampleSettings
import subprojects.build.samples.buildPluginSamples
import subprojects.eap.ProjectPublishEAPToSpace
import subprojects.release.*

object TriggerProjectSamplesOnEAPBuild : BuildType({
    id("KtorSamplesEAPCompositeBuild")
    name = "Samples EAP Composite Build"
    description = "Run all samples against the EAP version of Ktor"
    type = BuildTypeSettings.Type.COMPOSITE


    vcs {
        root(VCSSamples)
        root(VCSCoreEAP)
        root(VCSKtorBuildPluginsEAP)

    }

    params {
        defaultGradleParams()
        param("env.GIT_BRANCH", "%teamcity.build.branch%")
        param("env.VERSION_SUFFIX", "%dep.${ProjectPublishEAPToSpace.id}_PublishEAPToSpace.build.number%")
    }

    triggers {
        finishBuildTrigger {
            buildType = "${ProjectPublishEAPToSpace.id}_PublishEAPToSpace"
            successfulOnly = true
            branchFilter = """
                +:*-eap
                +:eap/*
            """.trimIndent()
        }
    }

    dependencies {
        snapshot(samplesBuild?.id!!) {
            runOnSameAgent = false
            onDependencyCancel = FailureAction.CANCEL
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(docSamplesBuild?.id!!) {
            runOnSameAgent = false
            onDependencyCancel = FailureAction.CANCEL
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(publishAllEAPBuild?.id!!) {
            runOnSameAgent = false
            onDependencyFailure = FailureAction.FAIL_TO_START
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
})

object TriggerProjectSamplesOnEAP : Project({
    id("TriggerProjectSamplesOnEAP")
    name = "EAP Validation"
    description = "Validate samples against EAP versions of Ktor"
    val buildPluginProjects = buildPluginSamples.map { sample ->
        BuildPluginSampleProject(
            BuildPluginSampleSettings(
                sample.projectName,
                VCSKtorBuildPluginsEAP,
                sample.standalone
            )
        )
    }
    buildPluginProjects.forEach(::buildType)
    buildType {
        createCompositeBuild(
            buildId = "KtorBuildPluginSamplesEAPValidate_All",
            buildName = "Validate all build plugin samples with EAP",
            vcsRoot = VCSKtorBuildPluginsEAP,
            builds = buildPluginProjects,
            withTrigger = TriggerType.NONE
        )
    }
    buildType(TriggerProjectSamplesOnEAPBuild)
})
