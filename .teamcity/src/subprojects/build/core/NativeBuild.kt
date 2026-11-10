package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.Agents.OS
import subprojects.build.*

class NativeBuild(
    private val entry: NativeEntry,
    private val repo: KtorRepository = Core,
) : BuildType({
    id(repo.id("KtorMatrixNative_${entry.id}".toId()))
    name = repo.name("${entry.name} ${entry.arch}")
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.klib")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(repo.vcsRoot)
    }

    params {
        extraGradleParams()
    }

    cancelPreviousBuilds()
    val rustEnabled = enableRustForRelevantChanges(entry.os)

    steps {
        if (entry.os == OS.Windows) {
            powerShell {
                name = "Remove git from PATH"
                scriptMode = script {
                    content = """
                        ${'$'}oldPath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
                        ${'$'}newPath = (${'$'}oldPath.Split(';') | Where-Object { ${'$'}_ -ne "C:\Program Files\Git\usr\bin" }) -join ';'
                        [Environment]::SetEnvironmentVariable('Path', ${'$'}newPath, 'Machine')
                    """.trimIndent()
                }
            }
            defineTCPPortRange()
        }

        if (rustEnabled) {
            installRust(entry.os)
        }

        // Compile and link test executables before running tests.
        // Use separate JVMs to keep compilation-related GC pressure out of test execution.
        gradle {
            name = "Build tests"
            tasks = entry.targetTask(prefix = "linkDebugTest")
            // KT-89880 KotlinNativeLink cache key changes after cold native installation with
            // configuration cache enabled
            gradleParams = "--info --continue --no-daemon --no-configuration-cache $GradleParams"
            jdkHome = Env.JDK_LTS
        }

        gradle {
            name = "Build Tests"
            tasks = entry.targetTask(suffix = "TestClasses")
            gradleParams = "$GradleParams"
            jdkHome = Env.JDK_LTS
        }

        gradle {
            name = "Run Tests"
            tasks = entry.targetTask(suffix = "Test")
            gradleParams = "--info --continue $GradleParams"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()

    requirements {
        agent(entry)
    }
})
