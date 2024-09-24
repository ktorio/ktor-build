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
            buildFile = "build.gradle.kts"
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
        releaseToSpace(
            listOf(
                "publishJvmPublicationToMavenRepository",
                "publishKotlinMultiplatformPublicationToMavenRepository",
                "publishMavenPublicationToMavenRepository"
            ),
            gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
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
        releaseToSpace(
            listOf("publishJsPublicationToMavenRepository"),
            gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
        releaseToSpace(
            listOf("publishWasmJsPublicationToMavenRepository"),
            gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
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
            listOf("publishMingwX64PublicationToMavenRepository"),
            gradleParams = "-P\"signing.gnupg.executable=gpg.exe\"",
            os = "Windows"
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
        releaseToSpace(
            listOf("publishLinuxX64PublicationToMavenRepository"),
            gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
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
        releaseToSpace(
            listOf(
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
                "publishWatchosX64PublicationToMavenRepository",
                "publishWatchosSimulatorArm64PublicationToMavenRepository"
            ),
            gradleParams = "-Psigning.gnupg.executable=gpg -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
    }

    requirements {
        require(macOS.agentString, minMemoryMB = 7000)
    }
})

private fun BuildSteps.releaseToSpace(
    gradleTasks: List<String>,
    gradleParams: String = "",
    os: String = "",
    optional: Boolean = false,
) {
    prepareKeyFile(os)
    gradle {
        name = "Assemble"
        tasks =
            "${gradleTasks.joinToString(" ")} --i -PreleaseVersion=$releaseVersion $gradleParams --stacktrace --no-parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${javaLTS.env}%"
        buildFile = "build.gradle.kts"
        if (optional)
            executionMode = BuildStep.ExecutionMode.ALWAYS
    }
    cleanupKeyFile(os)
}
