package dsl

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import org.intellij.lang.annotations.*
import subprojects.*
import java.util.Base64
import kotlin.io.path.*

fun BuildType.cancelPreviousBuilds(default: Boolean = true) {
    params {
        checkbox(
            name = "cancelPreviousBuilds",
            label = "Cancel previous builds",
            value = "$default",
            checked = "true",
            unchecked = "false",
        )
    }

    steps {
        script {
            name = "Cancel Previous Builds"
            scriptFile("cancel_previous_builds.sh")
            conditions {
                isNotWindows()
                equals("cancelPreviousBuilds", "true")
            }
        }
    }
}

fun BuildType.addSlackNotifications(
    channel: String = "#ktor-projects-on-eap",
    connection: String = "PROJECT_EXT_5",
    buildFailed: Boolean = true,
    buildFinishedSuccessfully: Boolean = false,
    buildStarted: Boolean = false,
    buildFailedToStart: Boolean = false
) {
    features {
        notifications {
            notifierSettings = slackNotifier {
                this.connection = connection
                sendTo = channel
                messageFormat = verboseMessageFormat {
                    addStatusText = true
                }
            }
            this.buildFailed = buildFailed
            this.buildFinishedSuccessfully = buildFinishedSuccessfully
            this.buildStarted = buildStarted
            this.buildFailedToStart = buildFailedToStart
        }
    }
}

internal fun BuildSteps.platformScript(name: String, os: Agents.OS, unixScript: String, windowsScript: String) {
    if (os == Agents.OS.Windows) {
        powerShell {
            this.name = name
            scriptFile(windowsScript)
        }
    } else {
        script {
            this.name = name
            scriptFile(unixScript)
        }
    }
}

internal fun ScriptBuildStep.scriptFile(fileName: String) {
    scriptContent = scriptContent(fileName)
}

internal fun PowerShellStep.scriptFile(fileName: String) {
    scriptMode = script {
        content = scriptContent(fileName)
    }
}

private fun scriptContent(fileName: String) = Path("scripts/$fileName").readText()

internal fun ScriptBuildStep.flakyNotifierScript() {
    val preamble = buildString {
        appendLine("#!/bin/bash")
        appendLine("set -euo pipefail")
        appendLine("# Helper scripts are base64-embedded by the DSL and materialized here; see BuildSteps.flakyNotifierScript.")
        appendLine("FLAKY_SCRIPTS_DIR=\"\$(mktemp -d)\"")
        appendLine("export FLAKY_SCRIPTS_DIR")
        for (name in FLAKY_HELPER_SCRIPTS) {
            val encoded = Base64.getEncoder().encodeToString(scriptContent(name).toByteArray(Charsets.UTF_8))
            appendLine("base64 -d > \"\$FLAKY_SCRIPTS_DIR/$name\" <<'FLAKY_B64_EOF'")
            appendLine(encoded)
            appendLine("FLAKY_B64_EOF")
        }
    }
    scriptContent = preamble + scriptContent("notify_flaky_tests.sh")
}

private val FLAKY_HELPER_SCRIPTS = listOf(
    "lib_flaky.sh",
    "collect_teamcity.sh",
    "collect_develocity.sh",
    "collect_quarantine.sh",
    "merge_report.sh",
)

private val bashPreamble = """
    #!/bin/bash
    set -e
""".trimIndent()

internal fun bashScript(@Language("bash") content: String): String = "$bashPreamble\n${content.trimIndent()}"

internal fun pythonScript(@Language("python") content: String): String = "#!/usr/bin/env python3\n${content.trimIndent()}"
