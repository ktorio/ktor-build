package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.*

class StressTestBuild(private val osJVMComboEntry: OSJDKEntry) : BuildType({
    id("KtorMatrixStressTest_${osJVMComboEntry.id}".toId())
    name = "Stress Test on ${osJVMComboEntry.os.id} and ${osJVMComboEntry.jdkEntry.name}"
    vcs {
        root(VCSCore)
    }
//    triggers {
//        schedule {
//            schedulingPolicy = daily {
//                hour = 8
//                timezone = "Europe/Moscow"
//            }
//            branchFilter = BranchFilter.DefaultBranch
//            triggerBuild = always()
//            param("revisionRuleBuildBranch", "<default>")
//        }
//    }
    steps {
        gradle {
            name = "Run stress tests"
            tasks = "stressTest --info -Ptest.jdk=${osJVMComboEntry.jdkEntry.version}"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(osJVMComboEntry)
    }
})
