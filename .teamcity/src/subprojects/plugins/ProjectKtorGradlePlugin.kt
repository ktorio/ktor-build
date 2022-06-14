package subprojects.plugins

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import subprojects.VCSKtorBuildPlugins
import subprojects.build.defaultTimeouts
import subprojects.nightlyEAPBranchesTrigger

object ProjectKtorGradlePlugin : Project({
    id("ProjectKtorGradlePlugin")
    name = "Ktor Gradle Plugin"
    description = "Publish Ktor Gradle Plugin"

    params {
        defaultTimeouts()
        password("env.PUBLISHING_USER", value = "%space.packages.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.secret%")
        param("env.PUBLISHING_URL", value = "%space.packages.url%")
    }

    buildType {
        id("PublishGradlePlugin")
        name = "Build and publish Ktor Gradle Plugin to Gradle Plugin Repository"

        vcs {
            root(VCSKtorBuildPlugins)
        }

        steps {
            gradle {
                name = "Publish"
                tasks = ":plugin:publishPlugins"
                buildFile = "build.gradle.kts"
            }
        }
    }

    buildType {
        id("PublishGradleEAPPlugin")
        name = "Build and publish Ktor Gradle EAP Plugin to Space Packages"

        vcs {
            root(VCSKtorBuildPlugins)
        }

        triggers {
            nightlyEAPBranchesTrigger()
        }

        steps {
            gradle {
                name = "Publish to Space Packages"
                tasks = ":plugin:publish"
                buildFile = "build.gradle.kts"
            }
        }
    }
})
