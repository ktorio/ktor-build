package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureConditions
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.*
import subprojects.*
import subprojects.build.*
import subprojects.release.*

class CoreBuild(private val osJdkEntry: OSJDKEntry) : BuildType({
    id("KtorMatrixCore_${osJdkEntry.osEntry.name}${osJdkEntry.jdkEntry.name}".toExtId())
    name = "${osJdkEntry.jdkEntry.name} on ${osJdkEntry.osEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
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
            tasks = "cleanJvmTest jvmTest --no-parallel --continue --info"
            jdkHome = "%env.${osJdkEntry.jdkEntry.env}%"
        }
    }
    features {
        monitorPerformance()
    }
    failureConditions {
        failureOnDecreaseTestCount()
    }
    requirements {
        require(os = osJdkEntry.osEntry.agentString, minMemoryMB = 7000)
    }
    if (osJdkEntry.osEntry == linux && osJdkEntry.jdkEntry == java11) {
        jvmBuild = this
    }
})

fun FailureConditions.failureOnDecreaseTestCount() {
    failOnMetricChange {
        id = "BuildFailureCondition_TestCount".toExtId()
        metric = BuildFailureOnMetric.MetricType.TEST_COUNT
        threshold = 10
        units = BuildFailureOnMetric.MetricUnit.DEFAULT_UNIT
        comparison = BuildFailureOnMetric.MetricComparison.LESS
        compareTo = build {
            buildRule = lastSuccessful()
        }
    }
}

fun formatArtifacts(vararg artifacts: String): String {
    return artifacts.joinToString("\n")
}
