package subprojects.build


import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
import subprojects.*
import subprojects.build.apidocs.ProjectBuildAPIDocs
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
    subProject(ProjectBuildAPIDocs)
    subProject(ProjectPlugin)

    cleanup {
        keepRule {
            id = "KtorKeepRule_DefaultBranchArtifacts"
            dataToKeep = allArtifacts()
            days(2)
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

fun ParametrizedWithType.defaultTimeouts() {
    param("system.org.gradle.internal.http.connectionTimeout", "120000")
    param("system.org.gradle.internal.http.socketTimeout", "120000")
}