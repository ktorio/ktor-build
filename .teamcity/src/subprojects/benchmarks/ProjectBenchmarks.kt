package subprojects.benchmarks

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.publishing.*

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

        artifactRules = """
            +:ktor-benchmarks/allocation-benchmark/allocations => old_allocations.zip
            +:ktor-benchmarks/allocation-benchmark/build/allocations => new_allocations.zip
        """.trimIndent()

        triggers {
            onChangeDefaultOrPullRequest()
        }

        steps {
            gradle {
                tasks = "$JVM_AND_COMMON_PUBLISH_TASK $EXCLUDE_DOKA_GENERATION -PreleaseVersion=1.0.0-BENCHMARKS"
                workingDir = "ktor"
                jdkHome = Env.JDK_LTS
            }
            gradle {
                tasks = "test -PktorVersion=1.0.0-BENCHMARKS"
                workingDir = "ktor-benchmarks/allocation-benchmark"
                jdkHome = Env.JDK_LTS
            }
        }

        requirements {
            agent(Agents.OS.Linux)
        }

        params {
            param("system.teamcity.default.properties", "ktor/teamcity.default.properties")
        }

        defaultBuildFeatures(VCSCore.id.toString())
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
