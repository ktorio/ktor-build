package subprojects.benchmarks

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.release.publishing.*

private const val MAVEN_LOCAL_PATH = "%system.teamcity.build.checkoutDir%/ktor-repository"

object ProjectBenchmarks : Project({
    id("ProjectKtorBenchmarks")
    name = "Benchmarks"

    vcsRoot(VCSKtorBenchmarks)

    buildType(BuildKtorForAllocationBenchmarks)

    buildType {
        id("AllocationTests")
        name = "Allocation tests"

        val ktorVersion = BuildKtorForAllocationBenchmarks.depParamRefs.buildNumber.ref

        vcs {
            root(VCSCore, "+:.=>ktor")
            root(VCSKtorBenchmarks, "+:.=>ktor-benchmarks")
            checkoutMode = CheckoutMode.ON_AGENT
        }

        artifactRules = """
            +:ktor-benchmarks/allocation-benchmark/allocations => old_allocations.zip
            +:ktor-benchmarks/allocation-benchmark/build/allocations => new_allocations.zip
        """.trimIndent()

        triggers {
            onChangeDefaultOrPullRequest()
        }

        steps {
            script {
                name = "Resolve allocation baseline"
                scriptFile("resolve_allocation_baseline.sh")
            }
            gradle {
                tasks = "allocationTests"
                gradleParams = "-PktorVersion=$ktorVersion " +
                    "-PallocationBaseline=%allocationBaseline% " +
                    "-Dmaven.repo.local=$MAVEN_LOCAL_PATH"
                workingDir = "ktor-benchmarks/allocation-benchmark"
                jdkHome = Env.JDK_LTS
            }
        }

        dependencies {
            dependency(BuildKtorForAllocationBenchmarks) {
                snapshot {
                    reuseBuilds = ReuseBuilds.SUCCESSFUL
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                }
                artifacts {
                    buildRule = sameChainOrLastFinished()
                    artifactRules = "ktor-repository.zip!** => ktor-repository"
                    cleanDestination = true
                }
            }
        }

        requirements {
            agent(Agents.OS.Linux)
        }

        params {
            text(
                name = "allocationBaseline",
                value = "",
                description = "Allocation baseline override: main or release/MAJOR.x",
                allowEmpty = true,
            )
            param("env.ALLOCATION_BASELINE", "%allocationBaseline%")
            param("env.ALLOCATION_TARGET_BRANCH", "%teamcity.pullRequest.target.branch%")
            param("env.ALLOCATION_BUILD_BRANCH", "%teamcity.build.branch%")
            param("system.teamcity.default.properties", "ktor/teamcity.default.properties")
        }

        defaultBuildFeatures(VCSCore.id)
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

private object BuildKtorForAllocationBenchmarks : BuildType({
    id("BuildKtorForAllocationBenchmarks")
    name = "Build Ktor for allocation benchmarks"

    vcs {
        root(VCSCore)
    }

    artifactRules = "+:ktor-repository => ktor-repository.zip"

    steps {
        script {
            name = "Set Ktor benchmark version"
            scriptContent = bashScript(
                """
                ktorVersion="${'$'}(<VERSION)"
                ktorVersion="${'$'}{ktorVersion%-SNAPSHOT}-BENCHMARKS.%build.counter%"
                echo "##teamcity[buildNumber '${'$'}ktorVersion']"
                """
            )
        }
        gradle {
            tasks = JVM_AND_COMMON_PUBLISH_TASK
            gradleParams = "$EXCLUDE_DOKA_GENERATION " +
                "-PreleaseVersion=%build.number% " +
                "-Dmaven.repo.local=$MAVEN_LOCAL_PATH"
            jdkHome = Env.JDK_LTS
        }
    }

    requirements {
        agent(Agents.OS.Linux)
    }

    params {
        param("system.teamcity.default.properties", "teamcity.default.properties")
    }

    defaultBuildFeatures(VCSCore.id)
})
