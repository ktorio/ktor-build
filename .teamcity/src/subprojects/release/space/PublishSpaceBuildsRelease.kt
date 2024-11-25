package subprojects.release.space

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*
import subprojects.release.publishing.*

object PublishCustomTaskToSpaceRelease : BuildType({
    createDeploymentBuild("KtorPublishCustomToSpaceReleaseBuild", "Release Custom Task to Space", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        gradle {
            name = "Assemble"
            tasks =
                "%taskList% --i -PreleaseVersion=$releaseVersion --stacktrace --no-parallel -Porg.gradle.internal.network.retry.max.attempts=100000 -psigning.gnupg.homedir=%env.SIGN_KEY_LOCATION%/.gnupg"
            jdkHome = "%env.${javaLTS.env}%"
        }
    }
    params {
        text("releaseVersion", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("taskList", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }

    buildNumberPattern = releaseVersion
})

object PublishJvmToSpaceRelease : BuildType({
    createDeploymentBuild("KtorPublishJvmToSpaceReleaseBuild", "Release JVM to Space", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        releaseToSpace(JVM_AND_COMMON_PUBLISH_TASK)
    }

    requirements {
        require(linux.agentString, minMemoryMB = 7000)
    }
})

object PublishJSToSpaceRelease : BuildType({
    createDeploymentBuild("KtorPublishJSToSpaceReleaseBuild", "Release JS to Space", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        releaseToSpace(JS_PUBLISH_TASK)
    }

    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
    }
})

object PublishWindowsNativeToSpaceRelease : BuildType({
    createDeploymentBuild(
        "KtorPublishWindowsNativeToSpaceReleaseBuild",
        "Release Windows Native to Space",
        "",
        releaseVersion
    )
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        script {
            name = "Obtain Library Dependencies"
            scriptContent = windowsSoftware
        }
        releaseToSpace(
            WINDOWS_PUBLISH_TASK,
            GPG_WINDOWS_GRADLE_ARGS
        )
    }
    requirements {
        require(windows.agentString, minMemoryMB = 7000)
    }
})

object PublishLinuxNativeToSpaceRelease : BuildType({
    createDeploymentBuild(
        "KtorPublishLinuxNativeToSpaceReleaseBuild",
        "Release Linux Native to Space",
        "",
        releaseVersion
    )
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        releaseToSpace(LINUX_PUBLISH_TASK)
    }
    requirements {
        require(linux.agentString, minMemoryMB = 7000)
    }
})

object PublishMacOSNativeToSpaceRelease : BuildType({
    createDeploymentBuild(
        "KtorPublishMacOSNativeToSpaceReleaseBuild",
        "Release Mac Native to Space",
        "",
        releaseVersion
    )
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        releaseToSpace(DARWIN_PUBLISH_TASK, GPG_MACOS_GRADLE_ARGS)
    }

    requirements {
        require(macOS.agentString, minMemoryMB = 7000)
    }
})

object PublishAndroidNativeToSpaceRelease : BuildType({
    createDeploymentBuild("KtorPublishAndroidNativeToSpaceReleaseBuild", "Publish Android Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        releaseToSpace(ANDROID_NATIVE_PUBLISH_TASK)
    }
    requirements {
        require(linux.agentString, minMemoryMB = 7000)
    }
})

private fun BuildSteps.releaseToSpace(
    gradleTasks: String,
    gradleParams: String = GPG_DEFAULT_GRADLE_ARGS,
    optional: Boolean = false,
) {
    prepareKeyFile()
    gradle {
        name = "Assemble"
        tasks =
            "$gradleTasks --i -PreleaseVersion=$releaseVersion $gradleParams --stacktrace --no-parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${javaLTS.env}%"
        if (optional) executionMode = BuildStep.ExecutionMode.ALWAYS
    }
    cleanupKeyFile()
}
