package subprojects.benchmarks

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*

object ProjectBenchmarks : Project({
    id("ProjectKtorBenchmarks")

    buildType {
        id("AllocationTests")
        name = "Allocation tests"

        vcs {
            root(VCSCore, "+:.=>ktor")
            root(VCSKtorBenchmarks, "+:.=>ktor-benchmarks")
        }

        steps {
            gradle {
                tasks = "publishToMavenLocal -PreleaseVersion=1.0.0-BENCHMARKS"
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
})