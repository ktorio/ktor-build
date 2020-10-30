package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.publishing.*

class NativeBuild(private val osEntry: OSEntry) : BuildType({
    id("KtorMatrixNative_${osEntry.name}".toExtId())
    name = "Native on ${osEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.klib")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
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
        require(os = osEntry.agentString, minMemoryDB =  7000)
    }
    generatedBuilds[osEntry.name] = BuildData(this.id!!, artifactsToPublish)
})
