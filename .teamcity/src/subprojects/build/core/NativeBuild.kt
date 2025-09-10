package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.Agents.OS
import subprojects.build.*

class NativeBuild(private val entry: NativeEntry) : BuildType({
    id("KtorMatrixNative_${entry.id}".toId())
    name = "${entry.name} ${entry.arch}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.klib")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    cancelPreviousBuilds()
    enableRustForRelevantChanges(entry.os)

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

        installRust(entry.os)

        gradle {
            name = "Build and Run Tests"
            tasks = "${entry.testTasks} --info --continue"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(entry)
    }
})
