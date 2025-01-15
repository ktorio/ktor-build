package subprojects.kotlinx.html

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.publishing.*
import java.io.*

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
        defaultGradleParams()
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
        publishKotlinxHtmlWasmWasiToSpace(),
        publishKotlinxHtmlMacOsToSpace(),
        publishKotlinxHtmlLinuxToSpace(),
        publishKotlinxHtmlMingwToSpace(),
        publishKotlinxHtmlAndroidNativeToSpace(),
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
                    onDependencyFailure = FailureAction.ADD_PROBLEM
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
        Agents.OS.Linux,
        tasks.joinToString(" "),
    )
}

fun Project.publishKotlinxHtmlJsToSpace() = buildType {
    releaseToSpace(
        "Js",
        Agents.OS.Linux,
        "publishJsPublicationToMavenRepository publishWasmJsPublicationToMavenRepository",
    )
}

fun Project.publishKotlinxHtmlWasmWasiToSpace() = buildType {
    releaseToSpace(
        "WasmWasi",
        Agents.OS.Linux,
        "publishWasmWasiPublicationToMavenRepository",
    )
}

fun Project.publishKotlinxHtmlMacOsToSpace() = buildType {
    releaseToSpace(
        "NativeMacos",
        Agents.OS.MacOS,
        MACOS_PUBLISH_TASKS.joinToString(" "),
        GPG_MACOS_GRADLE_ARGS,
    )
}

fun Project.publishKotlinxHtmlLinuxToSpace() = buildType {
    releaseToSpace(
        "NativeLinux",
        Agents.OS.Linux,
        "publishLinuxX64PublicationToMavenRepository publishLinuxArm64PublicationToMavenRepository",
    )
}

fun Project.publishKotlinxHtmlMingwToSpace() = buildType {
    releaseToSpace(
        "NativeWindows",
        Agents.OS.Windows,
        "publishMingwX64PublicationToMavenRepository",
        GPG_WINDOWS_GRADLE_ARGS,
    )
}

fun Project.publishKotlinxHtmlAndroidNativeToSpace() = buildType {
    releaseToSpace(
        "AndroidNative",
        Agents.OS.Linux,
        listOf(
            "publishAndroidNativeArm32PublicationToMavenRepository",
            "publishAndroidNativeArm64PublicationToMavenRepository",
            "publishAndroidNativeX86PublicationToMavenRepository",
            "publishAndroidNativeX64PublicationToMavenRepository",
        ).joinToString(" "),
    )
}

private fun BuildType.releaseToSpace(
    platformName: String,
    os: Agents.OS,
    publishTasks: String,
    gradleParameters: String = GPG_DEFAULT_GRADLE_ARGS,
) {
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
        prepareKeyFile(os.name)

        gradle {
            name = "Publish"
            tasks = "$publishTasks " +
                    "--i -Prelease -PreleaseVersion=%releaseVersion% --stacktrace --no-parallel " +
                    "-Porg.gradle.internal.network.retry.max.attempts=100000 " +
                    gradleParameters
            jdkHome = Env.JDK_LTS
        }

        cleanupKeyFile(os.name)
    }

    requirements {
        agent(os)
    }
}
