package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.core.*
import subprojects.build.gradle.*
import subprojects.release.*

const val releaseVersion = "%releaseVersion%"

class PublishCustomTaskToMavenBuild(private val repo: KtorRepository) : BuildType({
    createMavenPublishBuild(repo, "KtorPublishCustomToMavenBuild", "Publish Custom to Maven")
    vcs {
        root(repo.vcsRoot)
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
        // No Gradle environment for an agent of any OS, so the configuration cache is disabled here
        publish("%tasks%", "%gpg_args% --no-configuration-cache", os = "Auto")
    }
    requirements {
        // Allow selection of any OS and arch
        agent(os = null, osArch = null, hardwareCapacity = Agents.MEDIUM)
    }
})

class PublishJvmToMavenBuild(private val repo: KtorRepository) : BuildType({
    createMavenPublishBuild(repo, "KtorPublishJvmToMavenBuild", "Publish JVM to Maven")
    vcs {
        root(repo.vcsRoot)
    }
    params {
        publishingParams()
    }
    usePublishingGradleEnvironment(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
    steps {
        publish(JVM_AND_COMMON_PUBLISH_TASK)
    }
})

class PublishJSToMavenBuild(private val repo: KtorRepository) : BuildType({
    createMavenPublishBuild(repo, "KtorPublishJSToMavenBuild", "Publish JS to Maven")
    vcs {
        root(repo.vcsRoot)
    }
    params {
        publishingParams()
    }
    usePublishingGradleEnvironment(Agents.OS.Linux, hardwareCapacity = Agents.LARGE, version = GradleUserHome.Light)
    steps {
        publish(JS_PUBLISH_TASK)
    }
})

class PublishWindowsNativeToMavenBuild(private val repo: KtorRepository) : BuildType({
    createMavenPublishBuild(repo, "KtorPublishWindowsNativeToMavenBuild", "Publish Windows Native to Maven")
    vcs {
        root(repo.vcsRoot)
    }
    params {
        publishingParams()
    }
    usePublishingGradleEnvironment(Agents.OS.Windows, hardwareCapacity = Agents.LARGE)
    steps {
        publish(
            WINDOWS_PUBLISH_TASK,
            GPG_WINDOWS_GRADLE_ARGS,
            os = "Windows",
        )
    }
})

class PublishLinuxNativeToMavenBuild(private val repo: KtorRepository) : BuildType({
    createMavenPublishBuild(repo, "KtorPublishLinuxNativeToMavenBuild", "Publish Linux Native to Maven")
    vcs {
        root(repo.vcsRoot)
    }
    params {
        publishingParams()
    }
    usePublishingGradleEnvironment(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
    steps {
        publish(LINUX_PUBLISH_TASK)
    }
})

class PublishMacOSNativeToMavenBuild(private val repo: KtorRepository) : BuildType({
    createMavenPublishBuild(repo, "KtorPublishMacOSNativeToMavenBuild", "Publish Mac Native to Maven")
    vcs {
        root(repo.vcsRoot)
    }
    params {
        publishingParams()
    }
    usePublishingGradleEnvironment(Agents.OS.MacOS, Agents.Arch.Arm64)
    steps {
        installCocoapods()
        publish(DARWIN_PUBLISH_TASK, GPG_MACOS_GRADLE_ARGS)
    }
    failureConditions {
        executionTimeoutMin = 8 * 60
    }
})

class PublishAndroidNativeToMavenBuild(private val repo: KtorRepository) : BuildType({
    createMavenPublishBuild(repo, "KtorPublishAndroidNativeToMavenBuild", "Publish Android Native to Maven")
    vcs {
        root(repo.vcsRoot)
    }
    params {
        publishingParams()
    }
    usePublishingGradleEnvironment(Agents.OS.Linux, hardwareCapacity = Agents.LARGE)
    steps {
        publish(ANDROID_NATIVE_PUBLISH_TASK)
    }
})

val PublishCustomTaskToMaven = PublishCustomTaskToMavenBuild(Core)
val PublishJvmToMaven = PublishJvmToMavenBuild(Core)
val PublishJSToMaven = PublishJSToMavenBuild(Core)
val PublishWindowsNativeToMaven = PublishWindowsNativeToMavenBuild(Core)
val PublishLinuxNativeToMaven = PublishLinuxNativeToMavenBuild(Core)
val PublishMacOSNativeToMaven = PublishMacOSNativeToMavenBuild(Core)
val PublishAndroidNativeToMaven = PublishAndroidNativeToMavenBuild(Core)

val ProtectedPublishCustomTaskToMaven = PublishCustomTaskToMavenBuild(Protected)
val ProtectedPublishJvmToMaven = PublishJvmToMavenBuild(Protected)
val ProtectedPublishJSToMaven = PublishJSToMavenBuild(Protected)
val ProtectedPublishWindowsNativeToMaven = PublishWindowsNativeToMavenBuild(Protected)
val ProtectedPublishLinuxNativeToMaven = PublishLinuxNativeToMavenBuild(Protected)
val ProtectedPublishMacOSNativeToMaven = PublishMacOSNativeToMavenBuild(Protected)
val ProtectedPublishAndroidNativeToMaven = PublishAndroidNativeToMavenBuild(Protected)

/**
 * Publishing keeps the project's JVM arguments, as it needs the project's Gradle heap.
 * The configuration cache is not supported for publishing tasks.
 */
fun BuildType.usePublishingGradleEnvironment(
    os: Agents.OS,
    arch: Agents.Arch = Agents.Arch.X64,
    hardwareCapacity: String = Agents.MEDIUM,
    version: GradleUserHome = GradleUserHome.Full,
) {
    useGradleEnvironment(
        os,
        version,
        configurationCache = false,
        deriveJvmArgs = false,
        arch = arch,
        hardwareCapacity = hardwareCapacity,
    )
}

private fun BuildType.createMavenPublishBuild(repo: KtorRepository, id: String, name: String) {
    createDeploymentBuild(repo.id(id), repo.name(name), "", releaseVersion)
}

private fun ParametrizedWithType.publishingParams() {
    configureReleaseVersion()
    extraGradleParams()
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
            "--info --stacktrace -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = Env.JDK_LTS
    }
    cleanupKeyFile(os)
}
