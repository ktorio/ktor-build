
package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.build.*
import subprojects.build.samples.*

object EapConstants {
    const val PUBLISH_EAP_BUILD_TYPE_ID = "KtorPublish_AllEAP"
    const val PUBLISH_BUILD_PLUGIN_TYPE_ID = "KtorGradleBuildPlugin_Publish"
}

object TriggerProjectSamplesOnEAP : Project({
    id("TriggerProjectSamplesOnEAP")
    name = "EAP Validation"
    description = "Validate samples against EAP versions of Ktor"

    params {
        param("ktor.eap.version", "KTOR_VERSION")
    }


    buildType {
        id("KtorEAPVersionResolver")
        name = "Set EAP Version for Tests"
        description = "Determines the EAP version to use for sample validation"

        vcs {
            root(VCSCoreEAP)
        }

        requirements {
            contains("teamcity.agent.jvm.os.name", "Linux")
        }

        params {
            defaultGradleParams()
            param("teamcity.build.skipDependencyBuilds", "true")
            param("teamcity.runAsFirstBuild", "true")
        }

        steps {
            script {
                name = "Get latest EAP version from Maven metadata"
                scriptContent = """
            #!/bin/bash
            set -e
            
            # Fetch the latest EAP version from the Ktor BOM metadata
            METADATA_URL="https://maven.pkg.jetbrains.space/public/p/ktor/eap/io/ktor/ktor-bom/maven-metadata.xml"
            echo "Fetching metadata from ${'$'}METADATA_URL"
            
            # Create a temporary file for the metadata
            TEMP_FILE=$(mktemp)
            
            # Download the metadata file
            if ! curl -s "${'$'}METADATA_URL" -o "${'$'}TEMP_FILE"; then
                echo "Failed to download metadata from ${'$'}METADATA_URL"
                rm -f "${'$'}TEMP_FILE"
                exit 1
            fi
            
            # Extract the latest version using grep and sed
            # This pattern looks for a <latest>version</latest> tag
            LATEST_VERSION=$(grep -o '<latest>[^<]*</latest>' "${'$'}TEMP_FILE" | sed 's/<latest>\(.*\)<\/latest>/\1/')
            
            # Clean up temp file
            rm -f "${'$'}TEMP_FILE"
            
            if [ -z "${'$'}LATEST_VERSION" ]; then
                echo "Failed to extract latest version from metadata"
                exit 1
            fi
            
            echo "Latest Ktor EAP version: ${'$'}LATEST_VERSION"
            
            # Set build parameter directly (will be propagated to dependent builds)
            echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}LATEST_VERSION']"
            
            # Also set project-level parameter for reference
            echo "##teamcity[setParameter name='ktor.eap.version' value='${'$'}LATEST_VERSION' level='project']"
            echo "##teamcity[buildStatus text='Using Ktor EAP version: ${'$'}LATEST_VERSION']"
            """.trimIndent()
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

            requirements {
                when (sample) {
                    is SampleProjectSettings -> {
                        contains("teamcity.agent.jvm.os.name", "Linux")

                        if (sample.withAndroidSdk) {
                            equals("env.ANDROID_HOME", "%android-sdk.location%")
                        }
                    }
                    is BuildPluginSampleSettings -> {
                        contains("teamcity.agent.jvm.os.name", "Linux")
                    }
                }
            }

            params {
                param("teamcity.build.skipDependencyBuilds", "true")
            }

            dependencies {
                dependency(RelativeId("KtorEAPVersionResolver")) {
                    snapshot {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                        onDependencyCancel = FailureAction.FAIL_TO_START
                        reuseBuilds = ReuseBuilds.NO
                        runOnSameAgent = false
                        synchronizeRevisions = true
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
            param("env.USE_LATEST_KTOR_GRADLE_PLUGIN", "true")
        }

        triggers {
            finishBuildTrigger {
                buildType = EapConstants.PUBLISH_BUILD_PLUGIN_TYPE_ID
                successfulOnly = true
                branchFilter = "+:*"
            }
        }

        dependencies {
            dependency(RelativeId("KtorEAPVersionResolver")) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                    reuseBuilds = ReuseBuilds.NO
                    synchronizeRevisions = true
                }
            }

            buildPluginEAPProjects.forEach { project ->
                project.id?.let { id ->
                    snapshot(id) {
                        onDependencyFailure = FailureAction.FAIL_TO_START
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

        triggers {
            finishBuildTrigger {
                buildType = EapConstants.PUBLISH_EAP_BUILD_TYPE_ID
                successfulOnly = true
                branchFilter = "+:*"
            }
        }

        dependencies {
            dependency(RelativeId("KtorEAPVersionResolver")) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                    reuseBuilds = ReuseBuilds.NO
                    synchronizeRevisions = true
                }
            }

            sampleEAPProjects.forEach { project ->
                project.id?.let { id ->
                    snapshot(id) {
                        onDependencyFailure = FailureAction.FAIL_TO_START
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
            param("teamcity.build.skipDependencyBuilds", "true")
        }

        dependencies {
            dependency(RelativeId("KtorEAPVersionResolver")) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                    reuseBuilds = ReuseBuilds.NO
                    synchronizeRevisions = true
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
