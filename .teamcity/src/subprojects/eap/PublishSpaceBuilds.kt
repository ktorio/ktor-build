package subprojects.eap

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*

const val eapVersion = "%teamcity.build.branch%-%build.counter%"
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
        publishToSpace(
            listOf(
                "publishMingwX64PublicationToMavenRepository"
            )
        )
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
                "publishMacosX64PublicationToMavenRepository",
                "publishTvosArm64PublicationToMavenRepository",
                "publishTvosX64PublicationToMavenRepository",
                "publishWatchosArm32PublicationToMavenRepository",
                "publishWatchosArm64PublicationToMavenRepository",
                "publishWatchosX86PublicationToMavenRepository"
            )
        )
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


private fun BuildSteps.publishToSpace(gradleTasks: List<String>, gradleParams: String = "") {
    gradle {
        name = "Assemble"
        tasks =
            "${gradleTasks.joinToString(" ")} --i -PreleaseVersion=%releaseVersion% $gradleParams --stacktrace --no-parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${java11.env}%"
    }
}


