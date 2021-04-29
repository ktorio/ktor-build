package subprojects.benchmarks

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*

object ProjectBenchmarks : Project({
    id("ProjectKtorBenchmarks")
    name = "Benchmarks"

    buildType {
        id("AllocationTests")
        name = "Allocation tests"

        vcs {
            root(VCSCore, "+:.=>ktor")
            root(VCSKtorBenchmarks, "+:.=>ktor-benchmarks")
        }

        artifactRules = "+:allocation-benchmark/allocations => allocations.zip"

        steps {
            gradle {
                tasks =
                    "publishJvmPublicationToMavenLocal publishKotlinMultiplatformPublicationToMavenLocal -xdokka -PreleaseVersion=1.0.0-BENCHMARKS"
                workingDir = "ktor"
                buildFile = "build.gradle"
                jdkHome = "%env.${java11.env}%"
            }
            gradle {
                tasks = "test -PktorVersion=1.0.0-BENCHMARKS"
                workingDir = "ktor-benchmarks/allocation-benchmark"
                buildFile = "build.gradle.kts"
                jdkHome = "%env.${java11.env}%"
            }
        }

        features {
            githubCommitStatusPublisher()
        }

        triggers {
            onChangeAllBranchesTrigger()
        }
    }

    features {
        feature {
            id = "benchmarks_allocations_report_classes"
            type = "ReportTab"
            param("title", "Allocated classes")

            param("buildTypeId", "Ktor_AllocationTests")
            param("startPage", "allocations.zip!previewClasses.html")
            param("revisionRuleName", "lastSuccessful")
            param("revisionRuleRevision", "latest.lastSuccessful")
            param("type", "BuildReportTab")
        }

        feature {
            id = "benchmarks_allocations_report_sites"
            type = "ReportTab"

            param("title", "Allocation sites")

            param("buildTypeId", "Ktor_AllocationTests")
            param("startPage", "allocations.zip!previewSites.html")
            param("revisionRuleName", "lastSuccessful")
            param("revisionRuleRevision", "latest.lastSuccessful")
            param("type", "BuildReportTab")
        }
    }
})
