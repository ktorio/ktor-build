package subprojects.release.space

import jetbrains.buildServer.configs.kotlin.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*
import java.io.*

object ProjectPublishReleaseToSpace : Project({
    id("ProjectKtorPublishReleaseToSpace")
    name = "Release Ktor To Space"
    description = "Publish Release to Space"

    params {
        defaultTimeouts()

        text("reverse.dep.*.releaseVersion", "", display = ParameterDisplay.PROMPT, allowEmpty = false)

        param("env.SIGN_KEY_ID", value = "0x7c30f7b1329dba87")
        param("env.SIGN_KEY_LOCATION", value = File("%teamcity.build.checkoutDir%").invariantSeparatorsPath)
        param("env.SIGN_KEY_PUBLIC", value = SIGN_KEY_PUBLIC)
        param("env.PUBLISHING_URL", value = "%space.packages.release.url%")

        password("env.SIGN_KEY_PASSPHRASE", value = "%sign.key.passphrase%")
        password("env.SIGN_KEY_PRIVATE", value = "%sign.key.private%")
        password("env.PUBLISHING_USER", value = "%space.packages.release.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.release.secret%")
    }

    buildType(PublishCustomTaskToSpaceRelease)

    val builds = listOf(
        PublishJvmToSpaceRelease,
        PublishJSToSpaceRelease,
        PublishWindowsNativeToSpaceRelease,
        PublishLinuxNativeToSpaceRelease,
        PublishMacOSNativeToSpaceRelease,
        PublishAndroidNativeToSpaceRelease,
    )

    builds.forEach { buildType(it) }

    publishAllEAPBuild = buildType {
        id("KtorPublish_ReleaseToSpace")
        name = "Publish Release to Space"
        type = BuildTypeSettings.Type.COMPOSITE
        vcs {
            root(VCSCore)
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
})
