package subprojects.train

import subprojects.Agents

/**
 * Sample-to-OS routing shared by the external & internal sample-validation scripts.
 *
 * A single TeamCity build runs on a single agent, so the consolidated validation is split into
 * one validator build per OS. Each sample is routed to exactly one OS — the one that can build all
 * of its declared native targets — so no sample is validated (or counted) twice.
 */
object EapSampleRouting {
    val supported: List<Agents.OS> = listOf(Agents.OS.Linux, Agents.OS.MacOS, Agents.OS.Windows)
    val active: List<Agents.OS> = listOf(Agents.OS.Linux, Agents.OS.MacOS)

    fun osId(os: Agents.OS): String = when (os) {
        Agents.OS.Linux -> "linux"
        Agents.OS.MacOS -> "macos"
        Agents.OS.Windows -> "windows"
    }

    val activeIds: String = active.joinToString(" ", transform = ::osId)

    val routingBash: String = """
        # Map a sample's declared native families to the single OS that should build it.
        route_os_for_sample() {
            local dir="${'$'}1" nf target
            nf="${'$'}(detect_sample_native "${'$'}dir")"
            if echo "${'$'}nf" | grep -qw apple; then
                target="macos"
            elif echo "${'$'}nf" | grep -qw mingw; then
                target="windows"
            elif echo "${'$'}nf" | grep -qw linux; then
                target="linux"
            else
                target="linux"
            fi
            # Fall back to linux when the routed OS has no validator in this run (e.g. Windows deferred).
            if ! echo "${'$'}{EAP_ACTIVE_OSES}" | grep -qw "${'$'}target"; then
                target="linux"
            fi
            echo "${'$'}target"
        }

        sample_routes_here() {
            local dir="${'$'}1" target
            target="${'$'}(route_os_for_sample "${'$'}dir")"
            [ "${'$'}target" = "${'$'}EAP_TARGET_OS" ] && return 0
            echo "⏭️  Skipping ${'$'}(basename "${'$'}dir"): routed to ${'$'}target (this agent is ${'$'}EAP_TARGET_OS)"
            return 1
        }
    """.trimIndent()
}
