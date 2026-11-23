package subprojects.build.gradle

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import subprojects.*

/**
 * Changes to JVM arguments from the project's `gradle.properties` for builds using the environment, as recommended there for CI.
 * Each property maps to patterns, the first match of which is replaced.
 *
 * Agents have about 8 GB of memory, and the project's heaps leave too little of it for tests, browsers and the OS.
 * For one-shot builds 4 GB of heap is enough, and a larger heap makes GC much slower once the agent runs short of memory.
 * macOS agents have 2 GB more, which a cold native link uses to spend half as much time in GC.
 * Measurements are in `docs/memory.md`.
 */
private fun consumerJvmArgs(os: Agents.OS) = mapOf(
    "org.gradle.jvmargs" to replacements(
        "-Xms[0-9]+[kKmMgG] " to "",
        "-Xmx[0-9]+[kKmMgG] " to if (os == Agents.OS.MacOS) "-Xmx6g " else "-Xmx4g ",
        "autoshutdownIdleSeconds=[0-9]+" to "autoshutdownIdleSeconds=30",
    ),
    "kotlin.daemon.jvmargs" to replacements(
        "-Xmx[0-9]+[kKmMgG] " to "-Xmx1536m ",
    ),
)

private fun replacements(vararg replacements: Pair<String, String>): String =
    replacements.joinToString(" ") { (pattern, replacement) -> "'$pattern' '$replacement'" }

/**
 * Adds properties overriding the project's `gradle.properties` to the user home.
 * [consumerJvmArgs] are derived from the checked out branch, as the environment is packed from another branch,
 * and its own build needs the project's Gradle heap.
 */
internal fun BuildSteps.addGradleProperties(os: Agents.OS, deriveJvmArgs: Boolean, configurationCache: Boolean) {
    val calls = buildList {
        if (deriveJvmArgs) {
            for ((property, replacements) in consumerJvmArgs(os)) {
                add("derive '$property' $replacements")
            }
        }
        if (!configurationCache) add("append 'org.gradle.configuration-cache=false'")
    }
    if (calls.isEmpty()) return

    platformScript(
        name = "Add Gradle properties",
        os = os,
        unixScript = "gradle_properties.sh",
        windowsScript = "gradle_properties.ps1",
        suffix = calls.joinToString("\n"),
    )
}
