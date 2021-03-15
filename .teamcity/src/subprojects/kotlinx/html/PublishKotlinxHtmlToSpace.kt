package subprojects.kotlinx.html

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import subprojects.VCSCoreEAP
import subprojects.build.defaultTimeouts
import subprojects.build.java11
import subprojects.eap.SetBuildNumber
import subprojects.release.createDeploymentBuild

object PublishKotlinxHtmlToSpace : Project({
    id("ProjectKotlinxHtmlToSpace")
    name = "Release kotlinx.html"

    params {
        defaultTimeouts()
        param("env.SIGN_KEY_ID", value = "")
        password("env.PUBLISHING_USER", value = "%space.packages.kotlinx.html.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.kotlinx.html.secret%")
        param("env.PUBLISHING_URL", value = "%space.packages.kotlinx.html.url%")
    }


    buildType {
        params {
            param("releaseVersion", SetBuildNumber.depParamRefs.buildNumber.ref)
        }

        createDeploymentBuild(
            "KotlinxHtmlPublishToSpaceBuild",
            "Publish kotlinx.html to Space",
            "",
            SetBuildNumber.depParamRefs.buildNumber.ref
        )

        vcs {
            root(VCSCoreEAP)
        }
        steps {
            gradle {
                name = "Publish"
                tasks =
                    "publish --i -PreleaseVersion=%releaseVersion% $gradleParams --stacktrace --no-parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
                jdkHome = "%env.${java11.env}%"
            }
        }
    }
})