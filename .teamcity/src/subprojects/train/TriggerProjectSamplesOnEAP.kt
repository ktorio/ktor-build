
package subprojects.train

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import subprojects.*
import subprojects.build.*
import subprojects.build.samples.*

object EapConfig {
    const val PUBLISH_BUILD_ID = "Ktor_KtorPublish_AllEAP"
    const val MAVEN_REPO_URL = "https://maven.pkg.jetbrains.space/public/p/ktor/eap"
    val BRANCH_FILTER = """
        +:*-eap
        +:eap/*
    """.trimIndent()
}

object TriggerProjectSamplesOnEAP : Project({
    id("TriggerProjectSamplesOnEAP")
    name = "EAP Validation"
    description = "Validate samples against EAP versions of Ktor"

    val versionResolver = createVersionResolverBuild()
    buildType(versionResolver)

    val pluginSampleBuilds = buildPluginSamples.map { createPluginSampleBuild(it) }
    val regularSampleBuilds = sampleProjects.map { createRegularSampleBuild(it) }

    pluginSampleBuilds.forEach(::buildType)
    regularSampleBuilds.forEach(::buildType)

    createCompositeBuild(
        id = "EAP_KtorBuildPluginSamplesValidate_All",
        name = "EAP Validate all build plugin samples",
        vcsRoot = VCSKtorBuildPluginsEAP,
        sampleBuilds = pluginSampleBuilds
    )

    createCompositeBuild(
        id = "EAP_KtorSamplesValidate_All",
        name = "EAP Validate all samples",
        vcsRoot = VCSSamples,
        sampleBuilds = regularSampleBuilds
    )

    createMainCompositeBuild()
})

private fun Project.createVersionResolverBuild(): BuildType {
    return buildType {
        id("EAP_VersionResolver")
        name = "Resolve EAP Version"

        vcs { root(VCSCoreEAP) }

        artifactRules = """
            ktor-version.properties => .
            ktor-metadata.properties => .
            lib/** => .
        """.trimIndent()

        steps {
            script {
                name = "Create Version Resolver Script"
                scriptContent = createGradleVersionResolver()
            }

            gradle {
                name = "Resolve Latest EAP Version"
                tasks = "printLatestVersion"
                buildFile = "version-resolver.gradle.kts"
                gradleParams = "-Dorg.gradle.internal.repository.max.tentatives=10 -Dorg.gradle.internal.repository.initial.backoff=1000"
                jdkHome = "%env.JDK_17%"
                param("showStandardStreams", "true")
            }

            script {
                name = "Set EAP Version Environment Variable"
                scriptContent = createVersionEnvironmentScript()
            }
        }

        dependencies {
            dependency(RelativeId(EapConfig.PUBLISH_BUILD_ID)) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                }
                artifacts {
                    artifactRules = "ktor-*.jar => lib/"
                }
            }
        }

        defaultBuildFeatures(VCSCoreEAP.id.toString())
    }
}

private fun BuildType.configureForEap() {
    artifactRules = "lib/** => .\nktor-metadata.properties => ."

    dependencies {
        dependency(RelativeId("EAP_VersionResolver")) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.FAIL_TO_START
            }
            artifacts {
                artifactRules = """
                    ktor-version.properties => .
                    ktor-metadata.properties => .
                    lib/** => lib/
                """.trimIndent()
            }
        }
    }

    params {
        param("env.KTOR_VERSION", "%dep.EAP_VersionResolver.env.KTOR_VERSION%")
        param("env.VERSION_SUFFIX", "%dep.EAP_VersionResolver.env.VERSION_SUFFIX%")
    }
}

private fun createPluginSampleBuild(sample: BuildPluginSampleSettings): BuildType {
    val eapSample = sample.copy(vcsRoot = VCSKtorBuildPluginsEAP)

    return BuildPluginSampleProject(eapSample).apply {
        id("EAP_KtorBuildPluginSamplesValidate_${sample.projectName.replace('-', '_')}")
        name = "EAP Validate ${sample.projectName} sample"
        configureForEap()
    }
}

private fun createRegularSampleBuild(sample: SampleProjectSettings): BuildType {
    val eapSample = sample.copy(vcsRoot = VCSSamples)

    return SampleProject(eapSample).apply {
        id("EAP_KtorSamplesValidate_${sample.projectName.replace('-', '_')}")
        name = "EAP Validate ${sample.projectName} sample"
        configureForEap()
    }
}

private fun Project.createCompositeBuild(
    id: String,
    name: String,
    vcsRoot: KtorVcsRoot,
    sampleBuilds: List<BuildType>
) {
    buildType {
        id(id)
        this.name = name
        type = BuildTypeSettings.Type.COMPOSITE

        vcs { root(vcsRoot) }

        artifactRules = """
            lib/** => .
            ktor-metadata.properties => .
        """.trimIndent()

        dependencies {
            dependency(RelativeId("EAP_VersionResolver")) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                }
                artifacts {
                    artifactRules = """
                        ktor-metadata.properties => .
                        lib/** => lib/
                    """.trimIndent()
                }
            }

            sampleBuilds.forEach { build ->
                build.id?.let { buildId ->
                    snapshot(buildId) {
                        onDependencyFailure = FailureAction.ADD_PROBLEM
                        onDependencyCancel = FailureAction.CANCEL
                        reuseBuilds = ReuseBuilds.SUCCESSFUL
                    }
                }
            }
        }

        defaultBuildFeatures(vcsRoot.id.toString())
    }
}

private fun Project.createMainCompositeBuild() {
    buildType {
        id("KtorEAPSamplesCompositeBuild")
        name = "Validate All Samples with EAP"
        description = "Run all samples against the EAP version of Ktor"
        type = BuildTypeSettings.Type.COMPOSITE

        vcs { root(VCSCoreEAP) }

        params {
            defaultGradleParams()
            param("env.GIT_BRANCH", "%teamcity.build.branch%")
        }

        triggers {
            finishBuildTrigger {
                buildType = EapConfig.PUBLISH_BUILD_ID
                successfulOnly = true
                branchFilter = EapConfig.BRANCH_FILTER
            }
        }

        artifactRules = """
            lib/** => .
            ktor-metadata.properties => .
        """.trimIndent()

        dependencies {
            dependency(RelativeId("EAP_VersionResolver")) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                }
                artifacts {
                    artifactRules = """
                        ktor-metadata.properties => .
                        lib/** => lib/
                    """.trimIndent()
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
}

private fun createGradleVersionResolver(): String {
    return """
        cat > version-resolver.gradle.kts << EOF
        repositories {
            maven {
                url = uri("${EapConfig.MAVEN_REPO_URL}")
            }
        }
        
        val ktorVersion by configurations.creating {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        
        dependencies {
            add("ktorVersion", "io.ktor:ktor-server-core:+")
        }
        
        tasks.register("printLatestVersion") {
            doLast {
                val artifacts = ktorVersion.resolvedConfiguration.resolvedArtifacts
                if (artifacts.isEmpty()) {
                    throw GradleException("No Ktor versions found in the repository")
                }
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

private fun createVersionEnvironmentScript(): String {
    return """
        #!/bin/bash
        set -euo pipefail
        
        # Load version from properties file
        if [ -f "ktor-version.properties" ]; then
            VERSION=$(grep 'KTOR_VERSION=' ktor-version.properties | cut -d'=' -f2)
            if [ -z "${'$'}VERSION" ]; then
                echo "Error: Version found in properties file is empty"
                exit 1
            fi
            echo "Latest Ktor EAP version from properties: ${'$'}VERSION"
        else
            echo "Error: ktor-version.properties not found"
            exit 1
        fi
        
        # Set TeamCity parameter
        echo "##teamcity[setParameter name='env.KTOR_VERSION' value='${'$'}VERSION']"
        echo "##teamcity[setParameter name='env.VERSION_SUFFIX' value='${'$'}VERSION']"
        
        # Create a metadata file that can be shared as an artifact
        echo "KTOR_VERSION=${'$'}VERSION" > ktor-metadata.properties
    """.trimIndent()
}
