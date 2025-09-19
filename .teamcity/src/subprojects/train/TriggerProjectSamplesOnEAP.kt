package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.build.*
import subprojects.build.core.TriggerType
import subprojects.build.core.createCompositeBuild
import subprojects.build.samples.*

object EapConstants {
    const val PUBLISH_EAP_BUILD_TYPE_ID = "Ktor_KtorPublish_AllEAP"
    const val EAP_MAVEN_REPOSITORY_URL = "https://maven.pkg.jetbrains.space/public/p/ktor/eap"
    val EAP_BRANCH_FILTER = """
        +:*-eap
        +:eap/*
    """.trimIndent()
}

object TriggerProjectSamplesOnEAP : Project({
    id("TriggerProjectSamplesOnEAP")
    name = "EAP Validation"
    description = "Validate samples against EAP versions of Ktor"

    fun createVersionResolverScript(): String {
        return """
            cat > version-resolver.gradle.kts << EOF
            repositories {
                maven {
                    url = uri("${EapConstants.EAP_MAVEN_REPOSITORY_URL}")
                }
            }
            
            val ktorVersion by configurations.creating {
                isCanBeConsumed = false
                isCanBeResolved = true
            }
            
            dependencies {
                ktorVersion("io.ktor:ktor-server-core:+")
            }
            
            tasks.register("printLatestVersion") {
                doLast {
                    ktorVersion.resolvedConfiguration.resolvedArtifacts.forEach {
                        val version = it.moduleVersion.id.version
                        println("KTOR_VERSION=" + version)
                        file("ktor-version.properties").writeText("KTOR_VERSION=" + version)
                    }
                }
            }
            EOF
        """.trimIndent()
    }

    fun createVersionEnvironmentScript(): String {
        return """
            #!/bin/bash
            set -euo pipefail
            
            # Load version from properties file
            if [ -f "ktor-version.properties" ]; then
                # Use grep instead of source to avoid shell variable problems
                VERSION=$(grep -oP 'KTOR_VERSION=\K.*' ktor-version.properties)
                if [ -z "${'$'}VERSION" ]; then
                    echo "Error: Version found in properties file is empty"
                    exit 1
                fi
                echo "Latest Ktor EAP version from properties: ${'$'}VERSION"
            else
                echo "Warning: ktor-version.properties not found, falling back to log"
                if [ ! -f "build-gradle.log" ]; then
                    echo "Error: build-gradle.log not found either"
                    exit 1
                fi
                
                VERSION=$(grep "KTOR_VERSION=" build-gradle.log | head -1 | cut -d'=' -f2)
                if [ -z "${'$'}VERSION" ]; then
                    echo "Error: Failed to extract version from log"
                    exit 1
                fi
                echo "Latest Ktor EAP version from log: ${'$'}VERSION"
            fi
            
            # Set TeamCity parameter
            echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}VERSION']"
            echo "##teamcity[setParameter name='env.VERSION_SUFFIX' value='${'$'}VERSION']"
            echo "##teamcity[setParameter name='system.ktor.eap.version' value='${'$'}VERSION']"
            
            # Create a metadata file that can be shared as an artifact
            echo "KTOR_VERSION=${'$'}VERSION" > ktor-metadata.properties
        """.trimIndent()
    }

    fun BuildType.addEapVersionResolutionSteps() {
        steps {
            script {
                name = "Create Version Resolver Script"
                scriptContent = createVersionResolverScript()
            }

            gradle {
                name = "Resolve Latest EAP Version"
                tasks = "printLatestVersion"
                buildFile = "version-resolver.gradle.kts"
                gradleParams =
                    "-Dorg.gradle.internal.repository.max.tentatives=10 -Dorg.gradle.internal.repository.initial.backoff=1000"
                jdkHome = "%env.JDK_17%"
            }

            script {
                name = "Set EAP Version Environment Variable"
                scriptContent = createVersionEnvironmentScript()
            }
        }
    }

    fun BuildType.addEapArtifactDependency() {
        dependencies {
            dependency(RelativeId(EapConstants.PUBLISH_EAP_BUILD_TYPE_ID)) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                }
                artifacts {
                    artifactRules = "ktor-*.jar => lib/"
                }
            }
        }
    }

    buildType {
        id("KtorEAPVersionResolver")
        name = "Resolve EAP Version"
        description = "Resolves the latest EAP version and sets it as a TeamCity parameter"

        vcs {
            root(VCSCoreEAP)
        }

        params {
            defaultGradleParams()
        }

        addEapVersionResolutionSteps()
        addEapArtifactDependency()

        artifactRules = """
            ktor-metadata.properties => .
        """.trimIndent()

        triggers {
            finishBuildTrigger {
                buildType = EapConstants.PUBLISH_EAP_BUILD_TYPE_ID
                successfulOnly = true
                branchFilter = EapConstants.EAP_BRANCH_FILTER
            }
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
                param("env.KTOR_VERSION", "%system.ktor.eap.version%")
                param("env.VERSION_SUFFIX", "%system.ktor.eap.version%")
            }

            artifactRules = """
            lib/** => .
            ktor-metadata.properties => .
        """.trimIndent()

            dependencies {
                dependency(RelativeId("KtorEAPVersionResolver")) {
                    snapshot {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                        onDependencyCancel = FailureAction.FAIL_TO_START
                    }
                    artifacts {
                        artifactRules = """
                        ktor-metadata.properties => .
                        lib/ktor-*.jar => lib/
                    """.trimIndent()
                    }
                }
            }

            steps {
                script {
                    name = "Verify EAP Version"
                    scriptContent = """
                    #!/bin/bash
                    set -euo pipefail
                    
                    if [ -z "${'$'}KTOR_VERSION" ]; then
                        echo "Error: KTOR_VERSION is not set"
                        exit 1
                    fi
                    
                    echo "Using Ktor EAP version: ${'$'}KTOR_VERSION"
                """.trimIndent()
                    executionMode = BuildStep.ExecutionMode.RUN_ON_SUCCESS
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

    fun createEapCompositeBuild(id: String, name: String, vcsRoot: KtorVcsRoot, projects: List<BuildType>) {
        buildType {
            createCompositeBuild(
                id,
                name,
                vcsRoot,
                projects,
                withTrigger = TriggerType.NONE
            )

            dependencies {
                dependency(RelativeId("KtorEAPVersionResolver")) {
                    snapshot {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                        onDependencyCancel = FailureAction.FAIL_TO_START
                    }
                }
            }

            params {
                param("env.KTOR_VERSION", "%system.ktor.eap.version%")
                param("env.VERSION_SUFFIX", "%system.ktor.eap.version%")
            }
        }
    }

    val buildPluginEAPProjects = buildPluginSamples.map(::createBuildPluginEAPSample)
    val sampleEAPProjects = sampleProjects.map(::createRegularEAPSample)

    buildPluginEAPProjects.forEach(::buildType)
    sampleEAPProjects.forEach(::buildType)

    createEapCompositeBuild(
        "EAP_KtorBuildPluginSamplesValidate_All",
        "EAP Validate all build plugin samples",
        VCSKtorBuildPluginsEAP,
        buildPluginEAPProjects
    )

    createEapCompositeBuild(
        "EAP_KtorSamplesValidate_All",
        "EAP Validate all samples",
        VCSSamples,
        sampleEAPProjects
    )

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
            param("env.KTOR_VERSION", "%system.ktor.eap.version%")
            param("env.VERSION_SUFFIX", "%system.ktor.eap.version%")
        }

        dependencies {
            dependency(RelativeId("KtorEAPVersionResolver")) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
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
