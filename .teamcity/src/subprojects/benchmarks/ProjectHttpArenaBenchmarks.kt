package subprojects.benchmarks

import dsl.aptInstall
import dsl.bashScript
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
        name = "HttpArena benchmark"

        vcs {
            root(VCSHttpArenaBenchmarks)
            // Not checked out (all files excluded). It makes the build run on a Ktor branch (chosen when running the
            // build, shown in the build list) and shares that revision with the BuildKtorForBenchmarks dependency,
            // so up-to-date builds of that revision are reused
            root(VCSCore, "-:.")
            checkoutMode = CheckoutMode.ON_AGENT
        }

        artifactRules = "+:results => results.zip"

        steps {
            // TeamCity can't provision agents, so install OpenSSL on demand (needs passwordless sudo on the agent)
            aptInstall("openssl", "libssl-dev", name = "Ensure OpenSSL is installed")
            script {
                name = "Check host tools"
                scriptContent = bashScript("""
                    #!/usr/bin/env bash
                    set -euo pipefail
                    missing=0
                    for tool in docker python3 curl sudo sysctl taskset cpupower xargs; do
                        command -v ${'$'}tool >/dev/null 2>&1 || { echo "Missing required tool on agent: ${'$'}tool"; missing=1; }
                    done
                    exit ${'$'}missing
                """)
            }
            script {
                name = "Run HttpArena benchmark"
                // Assigns CPU counts.  This might not be necessary anymore...
                scriptContent = bashScript("""
                    #!/usr/bin/env bash
                    set -euo pipefail
                    cpus=${'$'}(docker info --format '{{.NCPU}}')
                    half=${'$'}(( cpus / 2 ))
                    [ "${'$'}half" -ge 1 ] || half=1
                    if [ "${'$'}cpus" -le 2 ]; then
                        export GCANNON_CPUS="0-${'$'}(( cpus - 1 ))"
                    else
                        export GCANNON_CPUS="${'$'}half-${'$'}(( cpus - 1 ))"
                    fi
                    export THREADS=${'$'}half ASYNC_THREADS=${'$'}half H2THREADS=${'$'}half H3THREADS=${'$'}half

                    echo "Docker CPUs: ${'$'}cpus, GCANNON_CPUS=${'$'}GCANNON_CPUS, threads=${'$'}half"
                    # Tests are intentionally unquoted so that several can be passed space-separated; empty runs all
                    ./scripts/benchmark.sh "${'$'}HTTPARENA_FRAMEWORK" ${'$'}HTTPARENA_TESTS --save
                """)
            }
        }

        dependencies {
            benchmarksSnapshotBuildDependency()
        }

        failureConditions {
            executionTimeoutMin = 120
        }

        params {
            text(
                name = "httparena.framework",
                value = "ktor-tuned",
                description = "Framework to benchmark",
                allowEmpty = false,
            )
            text(
                name = "httparena.tests",
                value = "",
                description = "Space-separated benchmark tests to run (e.g. baseline). Leave empty to run all benchmarks",
                allowEmpty = true,
            )
            param("env.HTTPARENA_FRAMEWORK", "%httparena.framework%")
            param("env.HTTPARENA_TESTS", "%httparena.tests%")
            // Passed to `docker build`: the Ktor version to build against, and the local Maven repository (with the
            // artifacts built by the dependency) as the `m2repo` named build context
            param(
                "env.FRAMEWORK_BUILD_ARGS",
                "--build-arg ENABLE_JFR=true " +
                    "--build-arg KTOR_VERSION=${BuildKtorForBenchmarks.depParamRefs.buildNumber.ref} " +
                    "--build-context m2repo=$MAVEN_LOCAL_PATH"
            )
            param("env.RUNS", "5")

            // GCANNON_CPUS and the thread counts are computed in the run step from the CPUs Docker reports
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
