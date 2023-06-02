package subprojects.kotlinx.html

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.publishing.*
import java.io.*
import java.util.*

object PublishKotlinxHtml : Project({
    id("ProjectKotlinxHtml")
    name = "Project kotlinx.html"

    publishKotlinxHtmlToSpace()
})

fun Project.publishKotlinxHtmlToSpace(): Project = subProject {
    id("PublishKotlinxHtmlToSpace")
    name = "Release kotlinx.html to Space"
    description = "publish kotlinx.html project to Space Packages"

    params {
        defaultTimeouts()
        param("env.SIGN_KEY_ID", value = "0x7c30f7b1329dba87")
        param("env.PUBLISHING_USER", value = "%space.packages.kotlinx.html.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.kotlinx.html.secret%")
        param("env.PUBLISHING_URL", value = "%space.packages.kotlinx.html.url%")

        password("env.SIGN_KEY_PASSPHRASE", value = "%sign.key.passphrase%")
        password("env.SIGN_KEY_PRIVATE", value = "%sign.key.private%")
        param("env.SIGN_KEY_LOCATION", value = File("%teamcity.build.checkoutDir%").invariantSeparatorsPath)
        param("env.SIGN_KEY_PUBLIC", value = SIGN_KEY_PUBLIC)
    }

    val builds = listOf(
        publishKotlinxHtmlJvmToSpace(),
        publishKotlinxHtmlJsToSpace(),
        publishKotlinxHtmlMacOsToSpace(),
        publishKotlinxHtmlLinuxToSpace(),
        publishKotlinxHtmlMingwToSpace(),
    )

    buildType {
        id("PublishKotlinxHtmlToSpaceAll")
        name = "Publish All builds to Space"
        type = BuildTypeSettings.Type.COMPOSITE

        params {
            text("reverse.dep.*.releaseVersion", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        }

        vcs {
            root(VCSKotlinxHtml)
        }

        dependencies {
            builds.mapNotNull { it.id }.forEach { id ->
                snapshot(id) {
                    reuseBuilds = ReuseBuilds.NO
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
        }
    }
}

fun Project.publishKotlinxHtmlJvmToSpace() = buildType {
    val tasks = listOf(
        "publishJvmPublicationToMavenRepository",
        "publishKotlinMultiplatformPublicationToMavenRepository",
    )

    releaseToSpace(
        "Jvm",
        linux.agentString,
        tasks.joinToString(" "),
        "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
    )
}

fun Project.publishKotlinxHtmlJsToSpace() = buildType {
    releaseToSpace(
        "Js",
        linux.agentString,
        "publishJsPublicationToMavenRepository",
        "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
    )
}

fun Project.publishKotlinxHtmlMacOsToSpace() = buildType {
    val tasks = listOf(
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
    )
    releaseToSpace(
        "NativeMacos",
        macOS.agentString,
        tasks.joinToString(" "),
        "-Psigning.gnupg.executable=gpg -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
    )
}

fun Project.publishKotlinxHtmlLinuxToSpace() = buildType {
    releaseToSpace(
        "NativeLinux",
        linux.agentString,
        "publishLinuxX64PublicationToMavenRepository",
        "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
    )
}

fun Project.publishKotlinxHtmlMingwToSpace() = buildType {
    releaseToSpace(
        "NativeWindows",
        windows.agentString,
        "publishMingwX64PublicationToMavenRepository",
        "-Psigning.gnupg.executable=gpg.exe"
    )
}

private fun BuildType.releaseToSpace(platformName: String, agent: String, publishTasks: String, gradleParameters: String) {
    id("PublishKotlinxHtmlToSpace_$platformName")
    name = "Publish kotlinx.html $platformName to Space"
    description = "Publish kotlinx.html $platformName to Space"

    vcs {
        root(VCSKotlinxHtml)
    }

    params {
        text("releaseVersion", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }


    steps {
        prepareKeyFile(agent)

        gradle {
            name = "Publish"
            tasks = "$publishTasks " +
                    "--i -Prelease -PreleaseVersion=%releaseVersion% --stacktrace --no-parallel " +
                    "-Porg.gradle.internal.network.retry.max.attempts=100000 " +
                    gradleParameters
            jdkHome = "%env.${java11.env}%"
            buildFile = "build.gradle.kts"
        }

        cleanupKeyFile(agent)
    }

    requirements {
        require(agent)
    }
}