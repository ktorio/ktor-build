package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.release.*

const val releaseVersion = "%releaseVersion%"

object PublishCustomTaskToMaven : BuildType({
    createDeploymentBuild("KtorPublishCustomToMavenBuild", "Publish Custom to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        publishingParams()
        text("tasks", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("prepublish_script", "", display = ParameterDisplay.PROMPT, allowEmpty = true)
        select(
            name = "gpg_args",
            value = GPG_DEFAULT_GRADLE_ARGS,
            label = "GPG Arguments",
            display = ParameterDisplay.PROMPT,
            options = listOf(
                "Default" to GPG_DEFAULT_GRADLE_ARGS,
                "macOS" to GPG_MACOS_GRADLE_ARGS,
                "Windows" to GPG_WINDOWS_GRADLE_ARGS,
            )
        )
    }
    steps {
        script {
            name = "Prepublish Script"
            scriptContent = "%prepublish_script%"
            conditions { doesNotEqual("prepublish_script", "") }
        }
        publish("%tasks%", "%gpg_args%", os = "Auto")
    }
    requirements {
        // Allow selection of any OS and arch
        agent(os = null, osArch = null, hardwareCapacity = Agents.MEDIUM)
    }
})

object PublishJvmToMaven : BuildType({
    createDeploymentBuild("KtorPublishJvmToMavenBuild", "Publish JVM to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        publishingParams()
    }
    steps {
        publish(JVM_AND_COMMON_PUBLISH_TASK)
    }
    requirements {
        agent(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
    }
})

object PublishJSToMaven : BuildType({
    createDeploymentBuild("KtorPublishJSToMavenBuild", "Publish JS to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        publishingParams()
    }
    steps {
        publish(JS_PUBLISH_TASK)
    }
    requirements {
        agent(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
    }
})

object PublishWindowsNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishWindowsNativeToMavenBuild", "Publish Windows Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        publishingParams()
    }
    steps {
        publish(
            WINDOWS_PUBLISH_TASK,
            GPG_WINDOWS_GRADLE_ARGS,
            os = "Windows",
        )
    }
    requirements {
        agent(Agents.OS.Windows, hardwareCapacity = Agents.LARGE)
    }
})

object PublishLinuxNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishLinuxNativeToMavenBuild", "Publish Linux Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        publishingParams()
    }
    steps {
        publish(LINUX_PUBLISH_TASK)
    }
    requirements {
        agent(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
    }
})

object PublishMacOSNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishMacOSNativeToMavenBuild", "Publish Mac Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        publishingParams()
    }
    steps {
        publish(DARWIN_PUBLISH_TASK, GPG_MACOS_GRADLE_ARGS)
    }
    requirements {
        agent(Agents.OS.MacOS)
    }
    failureConditions {
        executionTimeoutMin = 8 * 60
    }
})

object PublishAndroidNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishAndroidNativeToMavenBuild", "Publish Android Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        publishingParams()
    }
    steps {
        publish(ANDROID_NATIVE_PUBLISH_TASK)
    }
    requirements {
        agent(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
    }
})

private fun ParametrizedWithType.publishingParams() {
    configureReleaseVersion()
    text(
        name = "gradle_params",
        label = "Gradle Parameters",
        description = "Additional Gradle parameters to pass to the build",
        value = "",
        display = ParameterDisplay.NORMAL,
        allowEmpty = true
    )
}

fun BuildSteps.publish(
    gradleTasks: List<String>,
    gradleParams: String = GPG_DEFAULT_GRADLE_ARGS,
    os: String = "",
) {
    publish(gradleTasks.joinToString(" "), gradleParams, os)
}

fun BuildSteps.publish(
    gradleTasks: String,
    gradleParams: String = GPG_DEFAULT_GRADLE_ARGS,
    os: String = "",
) {
    prepareKeyFile(os)
    gradle {
        name = "Publish"
        tasks = "$gradleTasks -PreleaseVersion=$releaseVersion %gradle_params% $gradleParams " +
            "--no-configuration-cache " +
            "--info --stacktrace -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = Env.JDK_LTS
    }
    cleanupKeyFile(os)
}
