package subprojects.plugins

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import subprojects.VCSKtorBuildPlugins
import subprojects.build.defaultTimeouts

object ProjectKtorGradleEAPPlugin : Project({
    id("ProjectKtorGradlePlugin")
    name = "Ktor Gradle EAP Plugin"
    description = "Publish Ktor Gradle EAP Plugin to Space Packages"

    params {
        defaultTimeouts()
        password("env.PUBLISHING_USER", value = "%space.packages.user%")
        password("env.PUBLISHING_PASSWORD", value = "%space.packages.secret%")
        param("env.PUBLISHING_URL", value = "%space.packages.url%")
    }

    buildType {
        id("PublishGradleEAPPlugin")
        name = "Build and publish Ktor Gradle EAP Plugin to Space Packages"

        vcs {
            root(VCSKtorBuildPlugins)
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