package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*

const val releaseVersion = "%releaseVersion%"

object PublishCustomTaskToMaven : BuildType({
    createDeploymentBuild("KtorPublishCustomToMavenBuild", "Publish Custom to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
        text("tasks", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("repo_name", "Custom Task", display = ParameterDisplay.PROMPT, allowEmpty = false)
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
        prepareEnvironment()
        createSonatypeRepository("%repo_name%")
        publish("%tasks%", "%gpg_args%", os = "Auto", parallel = false)
    }
    requirements {
        agent(os = null, hardwareCapacity = Agents.MEDIUM)
    }
})

object PublishJvmToMaven : BuildType({
    createDeploymentBuild("KtorPublishJvmToMavenBuild", "Publish JVM to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        createSonatypeRepository("Jvm")
        publish(JVM_AND_COMMON_PUBLISH_TASK)
    }
    requirements {
        agent(Agents.OS.Linux)
    }
})

object PublishJSToMaven : BuildType({
    createDeploymentBuild("KtorPublishJSToMavenBuild", "Publish JS to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        createSonatypeRepository("Js")
        publish(JS_PUBLISH_TASK)
    }
    requirements {
        agent(Agents.OS.Linux)
    }
})

object PublishWindowsNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishWindowsNativeToMavenBuild", "Publish Windows Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        prepareEnvironment()
        publish(
            WINDOWS_PUBLISH_TASK,
            GPG_WINDOWS_GRADLE_ARGS,
            os = "Windows",
            parallel = false,
        )
    }
    requirements {
        agent(Agents.OS.Windows)
    }
})

object PublishLinuxNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishLinuxNativeToMavenBuild", "Publish Linux Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        createSonatypeRepository("Linux")
        publish(LINUX_PUBLISH_TASK)
    }
    requirements {
        agent(Agents.OS.Linux)
    }
})

object PublishMacOSNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishMacOSNativeToMavenBuild", "Publish Mac Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        script {
            name = "Obtain Library Dependencies"
            scriptContent = macSoftware
        }
        createSonatypeRepository("Mac Native")
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
        configureReleaseVersion()
    }
    steps {
        createSonatypeRepository("Android Native")
        publish(ANDROID_NATIVE_PUBLISH_TASK)
    }
    requirements {
        agent(Agents.OS.Linux)
    }
})

fun BuildSteps.publish(
    gradleTasks: List<String>,
    gradleParams: String = GPG_DEFAULT_GRADLE_ARGS,
    os: String = "",
    parallel: Boolean = true,
) {
    publish(gradleTasks.joinToString(" "), gradleParams, os, parallel)
}

fun BuildSteps.publish(
    gradleTasks: String,
    gradleParams: String = GPG_DEFAULT_GRADLE_ARGS,
    os: String = "",
    parallel: Boolean = true,
) {
    prepareKeyFile(os)
    gradle {
        name = "Publish"
        tasks = "$gradleTasks -PreleaseVersion=$releaseVersion $gradleParams " +
            "${if (parallel) "--parallel" else "--no-parallel"} " +
            "--info --stacktrace -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${javaLTS.env}%"
    }
    cleanupKeyFile(os)
}
