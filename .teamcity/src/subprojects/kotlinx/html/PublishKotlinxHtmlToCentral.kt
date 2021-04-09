package subprojects.kotlinx.html

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*
import subprojects.release.publishing.*
import java.io.*

fun Project.publishToCentral() {

    buildType {
        id("KotlinxHtmlPublishToMavenCentral")
        name = "Publish kotlinx.html to Maven Central"
        type = BuildTypeSettings.Type.DEPLOYMENT
        maxRunningBuilds = 1

        params {
            configureReleaseVersion()
            defaultTimeouts()
            password("env.SIGN_KEY_PASSPHRASE", value = "%sign.key.passphrase%")
            password("env.SIGN_KEY_PRIVATE", value = "%sign.key.private%")
            password("env.PUBLISHING_USER", value = "%sonatype.username%")
            password("env.PUBLISHING_PASSWORD", value = "%sonatype.password%")
            param("env.PUBLISHING_URL", value = "%sonatype.url%")

            password("env.SIGN_KEY_PASSPHRASE", value = "%sign.key.passphrase%")
            password("env.SIGN_KEY_PRIVATE", value = "%sign.key.private%")
            param("env.SIGN_KEY_LOCATION", value = File("%teamcity.build.checkoutDir%").invariantSeparatorsPath)
            param("env.SIGN_KEY_PUBLIC", value = SIGN_KEY_PUBLIC)
        }

        features {
            vcsLabeling {
                vcsRootId = "${VCSCore.id}"
                labelingPattern = releaseVersion
                successfulOnly = true
            }

            perfmon { }
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
}