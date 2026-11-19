package subprojects.benchmarks

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.Agents.EXTRA_LARGE

object ProjectHttpArenaBenchmarks : Project({
    id("ProjectKtorHttpArenaBenchmarks")
    name = "HttpArena Benchmarks"

    vcsRoot(VCSHttpArenaBenchmarks)

    buildType {
        id("HttpArenaKtorTunedBenchmark")
        name = "HttpArena ktor-tuned benchmark"

        vcs {
            root(VCSHttpArenaBenchmarks)
            checkoutMode = CheckoutMode.ON_AGENT
        }

        steps {
            script {
                name = "Run ktor-tuned benchmark"
                scriptContent = "./scripts/benchmark.sh ktor-tuned baseline"
            }
        }

        dependencies {
            benchmarksSnapshotBuildDependency()
        }

        params {
            param("env.KTOR_VERSION", BuildKtorForBenchmarks.depParamRefs.buildNumber.ref)
            param("env.MAVEN_LOCAL_REPO", MAVEN_LOCAL_PATH)

            // XLarge agent: 16 CPU, 32 GB RAM. Load generator gets the upper half of the cores,
            // leaving the lower half (0-7) for the server, with thread counts matching the 8 cores.
            param("env.GCANNON_CPUS", "8-15")
            param("env.THREADS", "8")
            param("env.ASYNC_THREADS", "8")
            param("env.H2THREADS", "8")
            param("env.H3THREADS", "8")
            param("env.LOADGEN_DOCKER", "true")
        }

        requirements {
            agent(
                os = Agents.OS.Linux,
                hardwareCapacity = EXTRA_LARGE
            )
            exists("docker.server.version")
        }
    }
})
