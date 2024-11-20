package subprojects

import jetbrains.buildServer.configs.kotlin.*
import subprojects.Agents.LARGE
import subprojects.Agents.MEDIUM
import subprojects.build.*

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
}

fun Requirements.agent(
    os: OSEntry,
    hardwareCapacity: String = MEDIUM
) {
    agent(os.agentString, os.agentString, hardwareCapacity)
}

fun Requirements.agent(
    os: String,
    osArch: String? = null,
    hardwareCapacity: String = MEDIUM,
) {
    contains("teamcity.agent.jvm.os.name", os)
    if (osArch != null) contains("teamcity.agent.jvm.os.arch", osArch)

    // It is better to use memory constraint to select agent as it unlocks the possibility to use more powerful agents
    val memorySizeMb = when (hardwareCapacity) {
        MEDIUM -> "7000"
        LARGE -> "15000"
        else -> ""
    }
    if (memorySizeMb.isNotBlank()) noLessThan("teamcity.agent.hardware.memorySizeMb", memorySizeMb)
}
