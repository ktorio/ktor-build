package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*

class CoreBuild(private val coreEntry: CoreEntry) : BuildType({
    id("KtorMatrix_${coreEntry.osEntry.name}${coreEntry.jdkEntry.name}".toExtId())
    name = "${coreEntry.jdkEntry.name} on ${coreEntry.osEntry.name}"
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
            jdkHome = "%env.${coreEntry.jdkEntry.env}%"
        }
        gradle {
            name = "Build and Run Tests"
            tasks = "clean jvmTest --no-parallel --continue --info"
            jdkHome = "%env.${coreEntry.jdkEntry.env}%"
        }
    }
    features {
        setupPerformanceMonitoring()
    }
    requirements {
        noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
        contains("teamcity.agent.jvm.os.name", coreEntry.osEntry.agentString)
    }
})
