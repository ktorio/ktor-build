package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*

class CoreBuild(private val osEntry: OSEntry, private val jdkEntry: JDKEntry) : BuildType({
    id("KtorMatrix_${osEntry.name}${jdkEntry.name}".toExtId())
    name = "${jdkEntry.name} on ${osEntry.name}"
    vcs {
        root(VCSCore)
    }
    triggers {
        setupDefaultVcsTrigger()
    }
    steps {
        gradle {
            name = "Assemble"
            tasks = "assemble --info"
            jdkHome = "%env.${jdkEntry.env}%"
        }
        gradle {
            name = "Build and Run Tests"
            tasks = "clean jvmTest --no-parallel --continue --info"
            jdkHome = "%env.${jdkEntry.env}%"
        }
    }
    features {
        setupPerformanceMonitoring()
    }
    requirements {
        noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
        contains("teamcity.agent.jvm.os.name", osEntry.agentString)
    }
})