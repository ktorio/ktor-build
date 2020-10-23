package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*

class CoreBuild(private val osJVMComboEntry: OSJDKEntry) : BuildType({
    id("KtorMatrix_${osJVMComboEntry.osEntry.name}${osJVMComboEntry.jdkEntry.name}".toExtId())
    name = "${osJVMComboEntry.jdkEntry.name} on ${osJVMComboEntry.osEntry.name}"
    artifactRules = addArtifacts("+:**/build/**/*.jar", junitReportArtifact, memoryReportArtifact)
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
            jdkHome = "%env.${osJVMComboEntry.jdkEntry.env}%"
        }
        gradle {
            name = "Build and Run Tests"
            tasks = "clean jvmTest --no-parallel --continue --info"
            jdkHome = "%env.${osJVMComboEntry.jdkEntry.env}%"
        }
    }
    features {
        monitorPerformance()
    }
    requirements {
        require(os = osJVMComboEntry.osEntry.agentString, minMemoryDB = 7000)
    }
})

fun addArtifacts(vararg artifacts: String): String {
    return artifacts.joinToString("\n")
}
