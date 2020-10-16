package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*

class NativeBuild(private val osEntry: OSEntry) : BuildType({
    id("KtorMatrixNative_${osEntry.name}".toExtId())
    name = "Native on ${osEntry.name}"
    vcs {
        root(VCSCore)
    }
    triggers {
        setupDefaultVcsTrigger()
    }
    steps {
        gradle {
            name = "Build and Run Tests"
            tasks = "ktor-client:ktor-client-curl:${osEntry.taskName} --info"
            jdkHome = "%env.JDK_11%"
        }
    }
    features {
        monitorPerformance()
    }
    requirements {
        defineOSAndMemoryAgentRequirements(osEntry.agentString, 7000)
    }
})
