package subprojects.kotlinx.html

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.vcsLabeling
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import subprojects.VCSCore
import subprojects.VCSCoreEAP
import subprojects.VCSKotlinxHtml
import subprojects.build.core.require
import subprojects.build.defaultTimeouts
import subprojects.build.java11
import subprojects.build.linux
import subprojects.defaultBranch
import subprojects.eap.SetBuildNumber
import subprojects.release.configureReleaseVersion
import subprojects.release.createDeploymentBuild
import subprojects.release.publishing.cleanupKeyFile
import subprojects.release.publishing.prepareKeyFile
import subprojects.release.publishing.releaseVersion

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
        id("KotlinxHtmlReleaseSpace")
        name = "Deploy Kotlinx.html to Space"

        params {
            configureReleaseVersion()
        }

        createDeploymentBuild(
            "KotlinxHtmlPublishToSpaceBuild",
            "Publish kotlinx.html to Space",
            "",
            SetBuildNumber.depParamRefs.buildNumber.ref
        )

        features {
            vcsLabeling {
                vcsRootId = "${VCSCore.id}"
                labelingPattern = releaseVersion
                successfulOnly = true
                branchFilter = "+:$defaultBranch"
            }
        }

        vcs {
            root(VCSKotlinxHtml)
        }

        steps {
            prepareKeyFile(linux.agentString)
            gradle {
                name = "Publish"
                tasks =
                    "publish --i -PreleaseVersion=%releaseVersion% $gradleParams --stacktrace --no-parallel " +
                            "-Porg.gradle.internal.network.retry.max.attempts=100000 " +
                            "-Psigning.gnupg.executable=gpg -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
                jdkHome = "%env.${java11.env}%"
            }

            cleanupKeyFile(linux.agentString)
        }

        requirements {
            require(linux.agentString)
        }
    }
})