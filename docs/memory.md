# Memory settings of Ktor CI builds

> State as of **2026-11-20**: Gradle 9.9.1, Kotlin 2.3.21, ParallelGC.

Read this before changing heap sizes, agent sizes or the split of build steps.

## Current settings

Builds using the Gradle environment ([`useGradleEnvironment`](../.teamcity/src/subprojects/build/gradle/GradleEnvironment.kt)) derive their JVM arguments from the project's `gradle.properties` with some changes ([`consumerJvmArgs`](../.teamcity/src/subprojects/build/gradle/GradleProperties.kt)):

| Setting | Value | Why |
|---|---|---|
| Gradle daemon `-Xmx` | 4g, 6g on macOS | A larger heap makes GC much slower once the 8g agent runs short of memory; macOS agents have 2 GB more |
| Gradle daemon `-Xms` | dropped | Short-lived daemons, no need to reserve memory |
| Kotlin daemon `-Xmx` | 1536m | 2g doesn't make compilation faster, see below |
| build-logic Kotlin daemons | shut down after 30 s idle | Free 0.75 GB per daemon |
| Gradle environment build, publishing, API docs | the project's arguments | They need the larger heap, and publishing runs on Large agents |

To reduce peak RAM usage JS and Wasm builds compile in a separate step before running tests, and native builds link in a separate step.

> [!Warning]
> `org.gradle.jvmargs` and `kotlin.daemon.jvmargs` are configuration cache inputs. Changing them makes the next build of every build type miss the configuration cache, and every open pull request with its own cache misses once too. Batch such changes.

## Agents

| Agent | CPUs | Memory | `teamcity.agent.build.minute.cost` |
|---|---|---|---|
| Linux Medium | 4 | 7.6 GB | 20 |
| Linux Large | 8 | 15.2 GB | 40 |
| Windows Medium | 4 | 7.9 GB | 40 |
| Windows Large | 8 | 15.9 GB | 80 |
| macOS Medium Arm64 | 4 | 10 GB | 150 |

> See: [Agent Pools](https://ktor.teamcity.com/favorite/agentPools)

- The cost covers the agent's lifetime, including its start before the build.
- A Large agent costs twice as much, so it pays off only if a build gets more than twice as fast.
- At most 2 builds run on macOS at the same time, and there are no larger macOS agents. macOS build duration directly adds to the queue of other macOS builds.
- Memory used by processes other than Gradle and its child processes: Linux 0.5–1.3 GB, macOS ~1.3 GB, Windows ~1.8 GB.
  Kotlin daemons don't run as Gradle's child processes, so they count here too: in a cold build this grows by up to 2 GB.

## Experiments

Unless stated otherwise, experiments measure the compile or link step of these builds on Medium agents:

| Build | Gradle tasks | What they do |
|---|---|---|
| Windows, Linux, macOS native | `linkDebugTestMingwX64`, `linkDebugTestLinuxX64`, `linkDebugTestMacosArm64` | Compile and link the test executables of all modules |
| JVM | `jvmTestClasses` | Compile main and test classes of all modules |
| JS, Wasm | `jsTestTestDevelopmentExecutableCompileSync`, `wasmJsTestTestDevelopmentExecutableCompileSync` | Compile the test bundles of all modules |

- **Cold** builds run with `--no-build-cache`, and the configuration cache is missed or disabled, as after a Kotlin update or a change in a core module.
- **Warm** builds use the remote build cache and the configuration cache, as most builds do.
- Durations, GC and memory come from the build scans: GC time and Old Gen of the Gradle daemon, and agent memory as used by all processes on the machine, including the OS, the TeamCity agent and Kotlin daemons (`allProcessesMemory`).
- Each value comes from one or two builds, so differences under ~10% are within noise.

### Gradle daemon heap: 4g vs 6g

> **Q:** Should we reduce the Gradle daemon heap from 6g to 4g?
> **A:** Yes, except on macOS, which keeps 6g: its agents have 2 GB more memory, and there 6g halves GC time of a cold link.

Cold builds:

| Build | Heap | Duration | GC | Old Gen peak / cap | Agent memory used, peak / total |
|---|---|---|---|---|---|
| Windows native | 4g | **1273 s** | **311 s** | 2.67 / 2.67 GB | 8.43 / 8.44 GB |
| Windows native | 6g | 2153 s | 1121 s | 4.27 / 4.29 GB | 8.44 / 8.44 GB |
| Linux native | 4g | **513 s** | **75 s** | 2.63 / 2.67 GB | 6.75 / 7.59 GB |
| Linux native | 6g | 595 s | 146 s | 3.82 / 4.00 GB | 7.30 / 7.59 GB |
| macOS native | 4g | 510 s | 111 s | 2.67 / 2.67 GB | 6.45 / 10 GB |
| macOS native | 6g | **346 s** | **55 s** | 3.84 / 4.00 GB | 7.10 / 10 GB |
| JVM | 4g | 320 s | **12 s** | 1.44 / 2.67 GB | 6.30 / 7.59 GB |
| JVM | 6g | **318 s** | 24 s | 1.41 / 4.00 GB | 6.69 / 7.59 GB |
| JS | 4g | 446 s | 15 s | 1.35 / 2.67 GB | 6.71 / 7.59 GB |

- On Windows and Linux a larger heap is slower: the agent runs out of memory and GC walks a partly paged-out heap. On Windows the 6g run read twice as much from disk.
- On macOS there is enough memory, so 6g halves GC time of a cold link.
- A cold native link keeps Old Gen full at 4g. There is no headroom on Windows, where the whole agent is at its limit.
- Warm builds use far less: the warm Windows test step peaked at 6.0 of 7.9 GB with 0.24 GB of Old Gen.
- JS and Wasm compile in the Kotlin daemon, so the Gradle heap matters for them only while configuring the project on a configuration cache miss: ~1.35 GB of Old Gen stays live (up to 1.9 GB measured earlier). 3g (2.0 GB of Old Gen) would be too tight.
- There is only one JS compile row, because even 4g of Gradle heap is more than enough for JS compilation.

### Agent size: Medium vs Large

> **Q:** Should native builds run on Large agents?
> **A:** No, Medium stays on Linux and Windows. Large is 10–30% faster for twice the price.

Linux native. Cost in credits, including the agent start:

| | Medium 4g | Large 6g | Large 8g |
|---|---|---|---|
| Cold build | 805 s, 301 | 716 s, 481 | 647 s, 448 |
| Warm build | 355–366 s, 120–124 | 294–295 s, 202–231 | — |
| Warm link step | 171–181 s | 133–138 s | 144–146 s |

Windows native:

| | Medium 4g | Large 6g |
|---|---|---|
| Cold link step | 1283 s | 897 s (whole build 1316 s, ~1750 credits) |
| Warm build | 478–479 s | 506 s (configuration cache miss) |
| Warm link step | 164 s | 132 s |

- Large is 10–30% faster for twice the price. The Gradle daemon used only 33–42% CPU during the link on Large Linux agents, so builds are not CPU-bound.
- The agent size is chosen before the build starts, and it isn't known whether a build will be cold. With the cold share below, Large Windows agents would make the average native build ~1.7 times as expensive and ~15% faster.
- A cold link on a Large Windows agent still takes 15 minutes against 7 on Linux, so Windows is slower not only because of memory.

### Kotlin daemon heap

> **Q:** Should the project Kotlin daemon get 2g instead of 1536m?
> **A:** No, 1536m stays. 2g doesn't make compilation faster and leaves less memory for the rest of the agent.

Cold builds, from `-Xlog:gc` logs of the project Kotlin daemon:

| Build | Heap | GC / daemon lifetime | Largest heap after GC / committed | Compile step | Agent memory used, peak |
|---|---|---|---|---|---|
| JS | 1536m | 65 s / 316 s | 1438 / 1476 MB | 446 s | 6.71 GB |
| JS | 2g | 53 s / 316 s | 1926 / 1977 MB | 446 s | 7.24 GB |
| Wasm | 1536m | 92 s / 336 s | 1396 / 1459 MB | 469 s | 6.75 GB |
| Wasm | 2g | 58 s / 299 s | 1400 / 1953 MB | 431 s | 7.21 GB |
| JVM | 1536m | 20 s / 193 s | 1028 / 1390 MB | 320 s | 6.30 GB |

- The daemon fills whatever heap it gets, and compilation is CPU-bound (89% CPU on the agent), so 2g doesn't speed up JS and only saves 38 s on cold Wasm builds.
- 2g leaves ~0.35 GB free on a Medium agent, against ~0.9 GB with 1536m.

### Separate compile step for JS and Wasm

> **Q:** With a 4g Gradle heap, do JS and Wasm builds still need to compile in a separate step before running tests?
> **A:** Yes. Without it, Chrome starves while other modules compile, Karma disconnects after 30 s without a response, and the remaining browser tests are lost.

Without it, Chrome runs while other modules compile, stops responding and Karma disconnects (`Disconnected (0 times) , because no message in 30000 ms.`).
In cold builds 339 JS and 248 Wasm browser tests were lost.
Warm builds without the separate step passed all tests (JS 508–519 s, Wasm 459–468 s).

### JVM flags for cold native links

> **Q:** Do `-XX:ReservedCodeCacheSize=512m` or `-XX:NewRatio=3` make the Gradle daemon spend less time in GC during a cold native link?
> **A:** The 512m code cache cuts GC time by 21–27% where the agent has enough memory. On Windows neither flag helps, because GC there is bound by the agent's memory.

Locally on macOS (12 CPUs, 64 GB), cold `linkDebugTestMacosArm64`, two runs per variant:

| Setup | Variant | Total GC pause | Full GCs caused by the code cache | Duration | Daemon RSS peak |
|---|---|---|---|---|---|
| Like a CI agent (4 CPUs, `-Xmx6g`) | default | 24.7 s | 14 | 168 s | 7.7 GB |
| | code cache 512m | **19.5 s** | 7 | 167 s | 7.6 GB |
| | `NewRatio=3` | 31.3 s (default: 31.3 s in the same session) | 24 | 174 s | — |
| MacBook Pro M2 Max (12 CPUs, project JVM arguments) | default | 18.4 s | 19 | 169 s | 7.7 GB |
| | code cache 512m | **13.4 s** | 6 | 165 s | 7.6 GB |
| | `NewRatio=3` | 16.9 s | 15.5 | 162 s | 7.7 GB |

Cold Windows native builds at 4g, the same day:

| Variant | Duration | GC | Old Gen peak / cap | Agent memory used, peak / total |
|---|---|---|---|---|
| default | 1490 s | 332 s | 2.67 / 2.67 GB | 7.71 / 7.71 GB |
| code cache 512m | 1522 s | 347 s | 2.67 / 2.67 GB | 7.70 / 7.71 GB |
| `NewRatio=3` | 1454 s | 308 s | 3.00 / 3.00 GB | 7.65 / 7.65 GB |

- Kotlin/Native compiles inside the Gradle daemon and fills the default 240 MB code cache. Every time it fills, the JVM runs a full GC to unload compiled code. With 512m the cache peaks at ~230 MB and these GCs drop by half or more, with no extra RSS.
- The live set after a full GC stays at ~2.2 GB even with 12 workers, far below the Old Gen cap, so `NewRatio=3` has nothing to improve locally.
- On Windows the agent's memory is full and Old Gen stays at its cap with either flag.
- Single Windows builds can't show differences under ~15%

## How often builds are cold

Builds that rebuilt most of their compile work, from Develocity before 2026-11-20 (Windows since 2026-10-01, others since 2026-11-11), classified by build cache savings relative to a typical warm build:

| Build | Cold | Partly cold | `main` | `release/*` | Pull requests |
|---|---|---|---|---|---|
| Windows native | 25% | 11% | 12% | 15% | 30% |
| Linux native | 28% | 8% | 7% | 20% | 38% |
| macOS native | 33% | 14% | 10% | 60% | 44% |
| JVM | 9% | 11% | 0% | 0% | 13% |

Most cold builds come from changes in core modules (`ktor-io`, `ktor-http`, `ktor-utils`), which rebuild everything depending on them. `release/3.x` is cold more often as it has few builds to keep the cache warm. The Gradle 9.9.1 update caused the most cold builds of a single day.

This history predates the current settings, so it shows how often builds were cold before them. Recheck it once there are a few weeks of builds with the current settings.

## Running experiments

### How to measure

- Run experiments on a separate branch, never on `main`: builds publish the configuration cache of their branch, see below.
- Start builds with `cancelPreviousBuilds=false`, otherwise each build cancels the earlier ones on the same branch.
- Give repeated runs a distinct dummy parameter: the TeamCity queue merges builds with identical parameters.
- Cold build: `gradle_params=--no-build-cache`. Add `--no-configuration-cache` to keep the configuration cache of the branch intact.
- Another Gradle heap: `gradle_params="-Dorg.gradle.jvmargs=-Xmx6g …"` (the full value, it replaces the derived one).
- Parameters for agent size, heaps, step splitting and Kotlin daemon GC logs were added in 994025b and removed in a47725b. Revert a47725b to bring them back.
- Read from scans (performance): `memoryUsage.totalGarbageCollectionTime`, Old Gen `peakMemory` / `maxMemory`, `gradleResourceUsage.total.buildProcessMemory.max` and `allProcessesMemory.max`.
- Develocity scans don't show Kotlin daemon GC. Use `-Xlog:gc:file=<path>-%p.log` in `kotlin.daemon.jvmargs` (a path without a colon, so not on Windows).
- Compare test counts: a crashed test process or a disconnected browser may not fail the build.

### Pitfalls

- The configuration cache is kept per branch in a single TeamCity build cache, which the last finished build overwrites. Builds running in parallel with different settings evict each other's entries.
- A branch with its own cache doesn't fall back to `main`'s, even if its entries are outdated.
- The REST API ignores `versionedSettingsRevision`, so builds with settings from another ktor-build revision can only be started from the UI. The REST API also can't start a build on a cloud image without a running agent.

## Possible directions

- **Gradle benchmarks:** [gradle-profiler](https://github.com/gradle/gradle-profiler) scenarios (configuration without the configuration cache, an ABI change in `ktor-io`, a cold native link of a few modules) run on a fixed agent type, alternating the old and new setup. Single CI builds are too noisy to judge changes of a few percent.
- **Fewer parallel Kotlin/Native tasks on Windows:** cold Windows links are bound by the agent's memory, so a smaller live set (e.g. `--max-workers=2` for the link step, or fewer Kotlin/Native threads) might cut paging and GC time more than it loses in parallelism.
- **G1 instead of ParallelGC:** marks concurrently instead of collecting Old Gen in long full GCs, and returns unused heap to the OS. Costs CPU on 4-CPU agents and extra native memory.
- **String deduplication:** `-XX:+UseStringDeduplication`, available for ParallelGC too since JDK 18, so it doesn't require G1.
- **JDK 25 for the Gradle daemon** (agents have it as `env.JDK_25_0`), after the benchmarks exist: compact object headers (`-XX:+UseCompactObjectHeaders`, off by default) shrink object headers from 12 to 8 bytes. Compiler IR consists of many small objects, so the live set could shrink noticeably. Check that Gradle and KGP support running on it.

Cold Windows native builds suffer the most, so try those optimizations there first.
