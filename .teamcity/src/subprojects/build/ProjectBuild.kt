package subprojects.build


import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
import subprojects.*
import subprojects.build.core.*
import subprojects.build.docsamples.*
import subprojects.build.generator.*
import subprojects.build.plugin.*
import subprojects.build.samples.*

object ProjectBuild : Project({
    id("ProjectKtorBuild")
    name = "Build"
    description = "Build configurations that build Ktor"

    vcsRoot(VCSCore)

    subProject(ProjectGenerator)
    subProject(ProjectSamples)
    subProject(ProjectCore)
    subProject(ProjectDocSamples)
    subProject(ProjectPlugin)

    cleanup {
        all(days = 1)
        keepRule {
            id = "KtorKeepRule_DefaultBranchArtifacts"
            dataToKeep = allArtifacts()
            applyToBuilds {
                successful()
                inBranches {
                    branchFilter = patterns(defaultBranch)
                }
            }
        }
    }
})

fun BuildFeatures.monitorPerformance() {
    perfmon {
    }
}
