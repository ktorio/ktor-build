package subprojects.benchmarks

import jetbrains.buildServer.configs.kotlin.*

object ProjectBenchmarks : Project({
    id("ProjectKtorBenchmarks")
    name = "Benchmarks"

    // Shared by all benchmark projects
    buildType(BuildKtorForBenchmarks)

    subProject(ProjectAllocationBenchmarks)
    subProject(ProjectHttpArenaBenchmarks)
})
