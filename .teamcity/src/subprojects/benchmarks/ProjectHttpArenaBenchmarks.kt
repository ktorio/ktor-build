package subprojects.benchmarks

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
            checkoutMode = CheckoutMode.ON_AGENT
        }

        artifactRules = "+:results => results.zip"

        steps {
            script {
                // TeamCity can't provision agents, so install OpenSSL on demand (needs passwordless sudo on the agent)
                name = "Ensure OpenSSL is installed"
                bashScript("""
                    #!/usr/bin/env bash
                    set -euo pipefail
                    if command -v openssl >/dev/null 2>&1 && dpkg -s libssl-dev >/dev/null 2>&1; then
                        openssl version
                        exit 0
                    fi
                    sudo apt-get update
                    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y openssl libssl-dev
                    openssl version
                """)
            }
            script {
                name = "Check host tools"
                bashScript("""
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
                bashScript("""
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
                    ./scripts/benchmark.sh "${'$'}HTTPARENA_FRAMEWORK" ${'$'}HTTPARENA_TESTS
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
            text(
                name = "ktor.branch",
                value = "",
                description = "Ktor branch to build and benchmark (e.g. main, release/3.x, my-feature). Empty uses the default branch",
                allowEmpty = true,
            )
            // Forwarded to the shared Ktor build, which checks out this branch before building
            param("reverse.dep.${BuildKtorForBenchmarks.id}.ktor.branch", "%ktor.branch%")
            param("env.HTTPARENA_FRAMEWORK", "%httparena.framework%")
            param("env.HTTPARENA_TESTS", "%httparena.tests%")
            param("env.KTOR_VERSION", BuildKtorForBenchmarks.depParamRefs.buildNumber.ref)
            param("env.MAVEN_LOCAL_REPO", MAVEN_LOCAL_PATH)
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
