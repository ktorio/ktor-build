package subprojects.kotlinx.html

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.vcsLabeling
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import subprojects.*
import subprojects.build.core.require
import subprojects.build.defaultTimeouts
import subprojects.build.java11
import subprojects.build.linux
import subprojects.eap.SetBuildNumber
import subprojects.release.configureReleaseVersion
import subprojects.release.createDeploymentBuild
import subprojects.release.publishing.cleanupKeyFile
import subprojects.release.publishing.prepareKeyFile
import subprojects.release.publishing.releaseVersion
import java.io.File

object PublishKotlinxHtmlToSpace : Project({
    id("ProjectKotlinxHtmlToSpace")
    name = "Release kotlinx.html"

    params {
        defaultTimeouts()
        param("env.SIGN_KEY_ID", value = "")
        param("env.PUBLISHING_USER", value = "%space.packages.kotlinx.html.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.kotlinx.html.secret%")
        param("env.PUBLISHING_URL", value = "%space.packages.kotlinx.html.url%")

        password("env.SIGN_KEY_PASSPHRASE", value = "%sign.key.passphrase%")
        password("env.SIGN_KEY_PRIVATE", value = "%sign.key.private%")
        param("env.SIGN_KEY_LOCATION", value = File("%teamcity.build.checkoutDir%").invariantSeparatorsPath)
        param("env.SIGN_KEY_PUBLIC", value = SIGN_KEY_PUBLIC)
    }

    buildType {
        id("KotlinxHtmlPublishToSpaceBuild")
        name = "Publish kotlinx.html to Space"
        type = BuildTypeSettings.Type.DEPLOYMENT
        maxRunningBuilds = 1
        features {
            perfmon { }
        }

        params {
            configureReleaseVersion()
        }

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
            checkoutMode = CheckoutMode.ON_AGENT
        }

        steps {
            prepareKeyFile(linux.agentString)
            gradle {
                name = "Publish"
                tasks =
                    "publish --i -Prelease -PreleaseVersion=%releaseVersion% --stacktrace --no-parallel " +
                            "-Porg.gradle.internal.network.retry.max.attempts=100000 " +
                            "-Psigning.gnupg.executable=gpg -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
                jdkHome = "%env.${java11.env}%"
                buildFile = "build.gradle.kts"
            }

            cleanupKeyFile(linux.agentString)
        }

        requirements {
            require(linux.agentString)
        }
    }
})