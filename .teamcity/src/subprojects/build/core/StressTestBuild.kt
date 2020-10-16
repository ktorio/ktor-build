package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.*
import subprojects.*

class StressTestBuild(private val osJVMComboEntry: OSJVMComboEntry) : BuildType({
    id("KtorMatrixStressTest_${osJVMComboEntry.osEntry.name}${osJVMComboEntry.jdkEntry.name}".toExtId())
    name = "Stress Test on ${osJVMComboEntry.osEntry.name} and ${osJVMComboEntry.jdkEntry.name}"
    vcs {
        root(VCSCore)
    }
    triggers {
        schedule {
            schedulingPolicy = daily {
                hour = 8
                timezone = "Europe/Moscow"
            }
            branchFilter = """
                    +:master
                    """.trimIndent()
            triggerBuild = always()
            param("revisionRuleBuildBranch", "<default>")
        }
    }
    steps {
        gradle {
            name = "Run stress tests"
            tasks = "stressTest --info"
            jdkHome = "%env.${osJVMComboEntry.jdkEntry.env}%"
        }
    }
    features {
        monitorPerformance()
    }
    requirements {
        require(os = osJVMComboEntry.osEntry.agentString, minimumMemoryMB = 7000)
    }
})
