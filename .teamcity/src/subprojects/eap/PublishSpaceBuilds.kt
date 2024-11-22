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
            jdkHome = Env.JDK_LTS
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
        releaseToSpace(JVM_AND_COMMON_PUBLISH_TASK)
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
        agent(Agents.OS.Linux)
    }
})

object PublishJSToSpace : BuildType({
    createDeploymentBuild("KtorPublishJSToSpaceBuild", "Publish JS to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        releaseToSpace(JS_PUBLISH_TASK)
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
        agent(Agents.OS.Linux)
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
        releaseToSpace(WINDOWS_PUBLISH_TASK)
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
        agent(Agents.OS.Windows)
    }
})

object PublishLinuxNativeToSpace : BuildType({
    createDeploymentBuild("KtorPublishLinuxNativeToSpaceBuild", "Publish Linux Native to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        releaseToSpace(LINUX_PUBLISH_TASK)
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
        agent(Agents.OS.Linux)
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
        releaseToSpace(DARWIN_PUBLISH_TASK)
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
        agent(Agents.OS.MacOS)
    }
})

object PublishAndroidNativeToSpace : BuildType({
    createDeploymentBuild("KtorPublishAndroidNativeToSpaceBuild", "Publish Android Native to Space", "", SetBuildNumber.depParamRefs.buildNumber.ref)
    vcs {
        root(VCSCoreEAP)
    }
    steps {
        releaseToSpace(ANDROID_NATIVE_PUBLISH_TASK)
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
        agent(Agents.OS.Linux)
    }
})

private fun BuildSteps.releaseToSpace(
    gradleTasks: String,
    gradleParams: String = "",
    optional: Boolean = false,
) {
    gradle {
        name = "Publish"
        tasks =
            "$gradleTasks --i -PeapVersion=%eapVersion% $gradleParams --stacktrace --parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = Env.JDK_LTS
        if (optional)
            executionMode = BuildStep.ExecutionMode.ALWAYS
    }
}
