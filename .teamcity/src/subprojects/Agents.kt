package subprojects

import jetbrains.buildServer.configs.kotlin.*
import subprojects.Agents.EXTRA_LARGE
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

    /** 16 CPU, 32 GB RAM */
    const val EXTRA_LARGE = "XLarge"

    /**
     * Operating Systems available to run builds.
     *
     * @property agentOsName The pinned OS version as it is specified on agents in `teamcity.agent.os.name`.
     *  We usually use the default OS version (`teamcity.agent.os.latest=true`), except for macOS,
     *  where we use the latest.
     */
    enum class OS(val agentOsName: String, id: String? = null) {
        Linux("Ubuntu-24.04"),
        Windows("Windows-Server-2022"),
        MacOS("macOS-26-Tahoe", id = "macOS");

        /** The OS identifier to be used in build ID. */
        val id: String = id ?: name
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
    osArch: Agents.Arch? = Agents.Arch.X64,
    hardwareCapacity: String = MEDIUM,
) {
    if (os != null) equals("teamcity.agent.os.family", os.name)
    if (osArch != null) equals("teamcity.agent.cpuArchitecture", osArch.agentArch)
    // macOS X64 agents are available only with an old macOS version
    val isMacOSX64 = os == Agents.OS.MacOS && osArch == Agents.Arch.X64
    if (os != null && !isMacOSX64) equals("teamcity.agent.os.name", os.agentOsName)

    // It is better to use memory constraint to select agent as it unlocks the possibility to use more powerful agents
    val memorySizeMb = when (hardwareCapacity) {
        MEDIUM -> "7000"
        LARGE -> "15000"
        EXTRA_LARGE -> "30000"
        else -> ""
    }
    if (memorySizeMb.isNotBlank()) noLessThan("teamcity.agent.hardware.memorySizeMb", memorySizeMb)
}
