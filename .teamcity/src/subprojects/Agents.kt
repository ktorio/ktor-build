package subprojects

import jetbrains.buildServer.configs.kotlin.*
import subprojects.Agents.LARGE
import subprojects.Agents.MEDIUM

object Agents {
    /** Use any available agent for the specified OS. */
    const val ANY = ""

    /**
     * 4 CPU, 8 GB RAM.
     * NOTE: For macOS it is the only available option.
     */
    const val MEDIUM = "Medium"

    /** 8 CPU, 16 GB RAM */
    const val LARGE = "Large"

    /** Operating Systems available to run builds. */
    enum class OS(id: String? = null, family: String? = null) {
        Linux,
        Windows,
        MacOS(id = "macOS", family = "Mac OS");

        /** The OS identifier to be used in build ID. */
        val id: String = id ?: name

        /** The OS family to be used to select the correct agent. */
        val family: String = family ?: name
    }

    /**
     * CPU architectures available to run builds.
     *
     * @property agentArch The architecture name as it is specified on agents.
     */
    enum class Arch(val agentArch: String) {
        X64("x86_64"),
        Arm64("aarch64");

        /** The architecture identifier to be used in build ID. */
        val id: String = name.lowercase()
    }
}

interface AgentSpec {
    val os: Agents.OS
    val arch: Agents.Arch
}

fun Requirements.agent(
    spec: AgentSpec,
    hardwareCapacity: String = MEDIUM
) {
    agent(spec.os, spec.arch, hardwareCapacity)
}

fun Requirements.agent(
    os: Agents.OS?,
    osArch: Agents.Arch = Agents.Arch.X64,
    hardwareCapacity: String = MEDIUM,
) {
    if (os != null) equals("teamcity.agent.jvm.os.family", os.family)
    equals("teamcity.agent.jvm.os.arch", osArch.agentArch)

    // It is better to use memory constraint to select agent as it unlocks the possibility to use more powerful agents
    val memorySizeMb = when (hardwareCapacity) {
        MEDIUM -> "7000"
        LARGE -> "15000"
        else -> ""
    }
    if (memorySizeMb.isNotBlank()) noLessThan("teamcity.agent.hardware.memorySizeMb", memorySizeMb)
}
