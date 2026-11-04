package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object JvmCompileBuild : BuildType({
    id("KtorMatrixCore_JvmCompile")
    name = "Build JVM Test Classes"

    vcs {
        root(VCSCore)
    }

    params {
        extraGradleParams()
    }

    cancelPreviousBuilds()
    enableRustForRelevantChanges(Agents.OS.Linux)

    steps {
        gradle {
            name = "Build JVM Test Classes"
            tasks = "jvmTestClasses"
            gradleParams = "$GradleParams"
            jdkHome = Env.JDK_LTS
        }
    }

    artifactRules = formatArtifacts(
        "+:**/build/** => jvm-build.zip",
        "+:.gradle/** => jvm-build.zip",
        "-:**/caches/**",
        "-:**/*.lock",
        "-:**/reports/**",
    )
    publishArtifacts = PublishMode.SUCCESSFUL

    defaultBuildFeatures()

    requirements {
        agent(Agents.OS.Linux)
    }
})