package subprojects.plugins

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import subprojects.VCSKtorBuildPlugins

object ProjectKtorGradlePlugin : Project({
    id("ProjectKtorGradlePlugin")
    name = "Ktor Gradle Plugin"
    description = "Publish Ktor Gradle Plugin"

    params {
        password("env.GRADLE_PUBLISH_KEY", "%gradle.publish.key%")
        password("env.GRADLE_PUBLISH_SECRET", "%gradle.publish.secret%")
    }

    buildType {
        id("PublishGradlePlugin")
        name = "Build and publish Ktor Gradle Plugin"

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
})
