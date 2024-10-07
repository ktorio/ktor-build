package subprojects.eap

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*
import subprojects.release.publishing.*

const val eapVersion = "%build.counter%"

object PublishCustomTaskToSpace : BuildType({
    createDeploymentBuild("KtorPublishCustomToSpaceBuild", "Publish Custom to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        gradle {
            name = "Assemble"
            tasks =
                "%taskList% --i -PeapVersion=%eapVersion% --stacktrace --no-parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
            jdkHome = "%env.${javaLTS.env}%"
            buildFile = "build.gradle.kts"
        }
    }
    params {
        text("eapVersion", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("taskList", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }

    buildNumberPattern = "%eapVersion%"
})

object PublishJvmToSpace : BuildType({
    createDeploymentBuild("KtorPublishJvmToSpaceBuild", "Publish JVM to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        releaseToSpace(
            listOf(
                "publishJvmPublicationToMavenRepository",
                "publishKotlinMultiplatformPublicationToMavenRepository",
                "publishMavenPublicationToMavenRepository"
            )
        )
    }
    params {
        param("eapVersion", SetBuildNumber.depParamRefs.buildNumber.ref)
    }

    dependencies {
        snapshot(SetBuildNumber) {
            reuseBuilds = ReuseBuilds.NO
        }
    }
    requirements {
        require(linux.agentString, minMemoryMB = 7000)
    }
})

object PublishJSToSpace : BuildType({
    createDeploymentBuild("KtorPublishJSToSpaceBuild", "Publish JS to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        releaseToSpace(listOf("publishJsPublicationToMavenRepository"))
        releaseToSpace(listOf("publishWasmJsPublicationToMavenRepository"), optional = true)
    }
    params {
        param("eapVersion", SetBuildNumber.depParamRefs.buildNumber.ref)
    }
    dependencies {
        snapshot(SetBuildNumber) {
            reuseBuilds = ReuseBuilds.NO
        }
    }
    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
    }
})

object PublishWindowsNativeToSpace : BuildType({
    createDeploymentBuild("KtorPublishWindowsNativeToSpaceBuild", "Publish Windows Native to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        script {
            name = "Obtain Library Dependencies"
            scriptContent = windowsSoftware
        }
        releaseToSpace(
            listOf(
                "publishMingwX64PublicationToMavenRepository"
            ),
            gradleParams = "-P\"signing.gnupg.executable=gpg.exe\"",
            os = "Windows"
        )
    }
    params {
        param("eapVersion", SetBuildNumber.depParamRefs.buildNumber.ref)
    }
    dependencies {
        snapshot(SetBuildNumber) {
            reuseBuilds = ReuseBuilds.NO
        }
    }
    requirements {
        require(windows.agentString, minMemoryMB = 7000)
    }
})

object PublishLinuxNativeToSpace : BuildType({
    createDeploymentBuild("KtorPublishLinuxNativeToSpaceBuild", "Publish Linux Native to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        releaseToSpace(
            listOf(
                "publishLinuxX64PublicationToMavenRepository",
                "publishLinuxArm64PublicationToMavenRepository",
            )
        )
    }
    params {
        param("eapVersion", SetBuildNumber.depParamRefs.buildNumber.ref)
    }
    dependencies {
        snapshot(SetBuildNumber) {
            reuseBuilds = ReuseBuilds.NO
        }
    }
    requirements {
        require(linux.agentString, minMemoryMB = 7000)
    }
})

object PublishMacOSNativeToSpace : BuildType({
    createDeploymentBuild("KtorPublishMacOSNativeToSpaceBuild", "Publish Mac Native to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        script {
            name = "Obtain Library Dependencies"
            scriptContent = macSoftware
        }
        releaseToSpace(MACOS_PUBLISH_TASKS)
    }
    params {
        param("eapVersion", SetBuildNumber.depParamRefs.buildNumber.ref)
    }
    dependencies {
        snapshot(SetBuildNumber) {
            reuseBuilds = ReuseBuilds.NO
        }
    }
    requirements {
        require(macOS.agentString, minMemoryMB = 7000)
    }
})

object PublishAndroidNativeToSpace : BuildType({
    createDeploymentBuild("KtorPublishAndroidNativeToSpaceBuild", "Publish Android Native to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        releaseToSpace(
            listOf(
                "publishAndroidNativeArm64PublicationToMavenRepository",
                "publishAndroidNativeArm32PublicationToMavenRepository",
                "publishAndroidNativeX64PublicationToMavenRepository",
                "publishAndroidNativeX86PublicationToMavenRepository",
            )
        )
    }
    params {
        param("eapVersion", SetBuildNumber.depParamRefs.buildNumber.ref)
    }
    dependencies {
        snapshot(SetBuildNumber) {
            reuseBuilds = ReuseBuilds.NO
        }
    }
    requirements {
        require(linux.agentString, minMemoryMB = 7000)
    }
})

private fun BuildSteps.releaseToSpace(
    gradleTasks: List<String>,
    gradleParams: String = "",
    os: String = "Linux",
    optional: Boolean = false,
) {
    gradle {
        name = "Publish"
        tasks =
            "${gradleTasks.joinToString(" ")} --i -PeapVersion=%eapVersion% $gradleParams --stacktrace --parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${javaLTS.env}%"
        buildFile = "build.gradle.kts"
        if (optional)
            executionMode = BuildStep.ExecutionMode.ALWAYS
    }
}
