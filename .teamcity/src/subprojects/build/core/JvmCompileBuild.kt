package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.gradle.*

class JvmCompileBuild(private val repo: KtorRepository) : BuildType({
    id(repo.id("KtorMatrixCore_JvmCompile"))
    name = repo.name("Build JVM Test Classes")

    vcs {
        root(repo.vcsRoot)
    }

    params {
        extraGradleParams()
    }

    useGradleEnvironment(Agents.OS.Linux, GradleUserHome.Light)
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
        "+:**/.gradle/** => jvm-build.zip",
        "-:**/caches/**",
        "-:**/build/**/*.lock",
        "-:**/reports/**",
    )
    publishArtifacts = PublishMode.SUCCESSFUL

    defaultBuildFeatures()
})
