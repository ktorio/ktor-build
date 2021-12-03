package subprojects.eap

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
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
            jdkHome = "%env.${java11.env}%"
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
        publishToSpace(
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
        snapshot(jvmBuild!!) {
        }
    }
    requirements {
        require(linux.agentString)
    }
})

object PublishJSToSpace : BuildType({
    createDeploymentBuild("KtorPublishJSToSpaceBuild", "Publish JS to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        publishToSpace(
            listOf(
                "publishJsPublicationToMavenRepository"
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
        snapshot(jsBuild!!) {
        }
        snapshot(PublishJvmToSpace) {
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
        powerShell {
            name = "Get dependencies and environment ready"
            scriptMode = script {
                content = """
                $libcurlSoftware
                """.trimIndent()
            }
        }
        publishToSpace(
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
        snapshot(nativeWindowsBuild!!) {
        }
        snapshot(PublishJSToSpace) {
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
        publishToSpace(
            listOf(
                "publishLinuxX64PublicationToMavenRepository"
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
        snapshot(nativeLinuxBuild!!) {
        }
        snapshot(PublishWindowsNativeToSpace) {
        }
    }
    requirements {
        require(linux.agentString)
    }
})

object PublishMacOSNativeToSpace : BuildType({
    createDeploymentBuild("KtorPublishMacOSNativeToSpaceBuild", "Publish Mac Native to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        publishToSpace(
            listOf(
                "publishIosArm32PublicationToMavenRepository",
                "publishIosArm64PublicationToMavenRepository",
                "publishIosX64PublicationToMavenRepository",
                "publishIosX64PublicationToMavenRepository",
                "publishIosSimulatorArm64PublicationToMavenRepository",

                "publishMacosX64PublicationToMavenRepository",
                "publishMacosArm64PublicationToMavenRepository",

                "publishTvosArm64PublicationToMavenRepository",
                "publishTvosX64PublicationToMavenRepository",
                "publishTvosSimulatorArm64PublicationToMavenRepository",

                "publishWatchosArm32PublicationToMavenRepository",
                "publishWatchosArm64PublicationToMavenRepository",
                "publishWatchosX86PublicationToMavenRepository",
                "publishWatchosX64PublicationToMavenRepository",
                "publishWatchosSimulatorArm64PublicationToMavenRepository"
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
        snapshot(nativeMacOSBuild!!) {
        }
        snapshot(PublishLinuxNativeToSpace) {
        }
    }
    requirements {
        require(macOS.agentString)
    }
})

private fun BuildSteps.publishToSpace(gradleTasks: List<String>, gradleParams: String = "", os: String = "Linux") {
    gradle {
        name = "Assemble"
        tasks =
            "${gradleTasks.joinToString(" ")} --i -PeapVersion=%eapVersion% $gradleParams --stacktrace --no-parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${java11.env}%"
        buildFile = "build.gradle.kts"
    }
}
