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
        text("prepublish_script", "", display = ParameterDisplay.PROMPT, allowEmpty = true)
    }
    steps {
        script {
            name = "Script"
            scriptContent = "%prepublish_script%"
        }
        publish("%tasks%", parallel = false)
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
        publish(JVM_PUBLISH_TASKS)
    }
    requirements {
        require(linux.agentString, minMemoryMB = 7000)
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
        publish("publishJsPublicationToMavenRepository")
    }
    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
    }
})

object PublishWasmJsToMaven : BuildType({
    createDeploymentBuild("KtorPublishWasmJsToMavenBuild", "Publish WasmJs to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        createSonatypeRepository("WasmJs")
        publish("publishWasmJsPublicationToMavenRepository")
    }
    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
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
        script {
            name = "Obtain Library Dependencies"
            scriptContent = windowsSoftware
        }
        publish(
            "publishMingwX64PublicationToMavenRepository",
            GPG_WINDOWS_GRADLE_ARGS,
            os = "Windows",
            parallel = false,
        )
    }
    requirements {
        require(windows.agentString, minMemoryMB = 7000)
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
        publish(LINUX_PUBLISH_TASKS)
    }
    requirements {
        require(linux.agentString, minMemoryMB = 7000)
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
        // Publish targets in separate steps to avoid implicit dependency between different targets
        // Issue: https://youtrack.jetbrains.com/issue/KTOR-7556/
        MACOS_PUBLISH_TASKS.forEach {
            createSonatypeRepository(it)
            publish(it, GPG_MACOS_GRADLE_ARGS)
        }
    }
    requirements {
        require(macOS.agentString, minMemoryMB = 7000)
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
        // Publish targets in separate steps to avoid implicit dependency between different targets
        // Issue: https://youtrack.jetbrains.com/issue/KTOR-7556/
        for (task in ANDROID_NATIVE_PUBLISH_TASKS) {
            createSonatypeRepository(task)
            publish(task)
        }
    }
    requirements {
        require(linux.agentString, minMemoryMB = 7000)
    }
})

fun BuildSteps.publish(
    gradleTask: String,
    gradleParams: String = GPG_DEFAULT_GRADLE_ARGS,
    os: String = "",
    parallel: Boolean = true,
) {
    publish(listOf(gradleTask), gradleParams, os, parallel)
}

fun BuildSteps.publish(
    gradleTasks: List<String>,
    gradleParams: String = GPG_DEFAULT_GRADLE_ARGS,
    os: String = "",
    parallel: Boolean = true,
) {
    prepareKeyFile(os)
    gradle {
        name = "Publish"
        tasks = "${gradleTasks.joinToString(" ")} -PreleaseVersion=$releaseVersion $gradleParams " +
            "${if (parallel) "--parallel" else "--no-parallel"} " +
            "--info --stacktrace -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${javaLTS.env}%"
        buildFile = "build.gradle.kts"
    }
    cleanupKeyFile(os)
}
