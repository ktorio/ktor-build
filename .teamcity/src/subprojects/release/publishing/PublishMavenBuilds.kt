package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
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
    }
    steps {
        publish(
            "%tasks%",
            gradleParams = "-Psigning.gnupg.homedir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
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
        publish(
            listOf(
                "publishJvmPublicationToMavenRepository",
                "publishKotlinMultiplatformPublicationToMavenRepository",
                "publishMavenPublicationToMavenRepository"
            ).joinToString(" "),
            gradleParams = "--parallel -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
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
        publish(
            "publishJsPublicationToMavenRepository",
            gradleParams = "--parallel -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
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
        powerShell {
            name = "Get dependencies and environment ready"
            scriptMode = script {
                content = """
                $libcurlSoftware
                """.trimIndent()
            }
        }
        publish(
            "publishMingwX64PublicationToMavenRepository",
            gradleParams = "-P\"signing.gnupg.executable=gpg.exe\"",
            os = "Windows"
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
        publish(
            "publishLinuxX64PublicationToMavenRepository",
            gradleParams = "--parallel -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
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
        MACOS_PUBLISH_TASKS.forEach {
            createSonatypeRepository(it)
            publish(it, MACOS_GRADLE_ARGS)
        }
    }
    requirements {
        require(macOS.agentString, minMemoryMB = 7000)
    }
    failureConditions {
        executionTimeoutMin = 8 * 60
    }
})

fun BuildSteps.publish(gradleTask: String, gradleParams: String = "", os: String = "") {
    prepareKeyFile(os)
    gradle {
        name = "Publish $gradleTask"
        tasks = "$gradleTask --i -PreleaseVersion=$releaseVersion $gradleParams" +
                " --stacktrace -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${java11.env}%"
        buildFile = "build.gradle.kts"
    }
    cleanupKeyFile(os)
}
