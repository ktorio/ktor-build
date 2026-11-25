package dsl

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import org.intellij.lang.annotations.*
import subprojects.*
import java.nio.file.*
import java.util.*
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

/** Runs [unixScript] or [windowsScript] depending on [os], with [suffix] appended to it. */
internal fun BuildSteps.platformScript(
    name: String,
    os: Agents.OS,
    unixScript: String,
    windowsScript: String,
    suffix: String = "",
) {
    if (os == Agents.OS.Windows) {
        powerShell {
            this.name = name
            scriptFile(windowsScript, suffix)
        }
    } else {
        script {
            this.name = name
            scriptFile(unixScript, suffix)
        }
    }
}

/** Installs [packages] on a Linux agent from the Ubuntu archive, unless they are installed already. */
internal fun BuildSteps.aptInstall(vararg packages: String, name: String = "Install ${packages.joinToString()}") {
    script {
        this.name = name
        scriptFile("apt_install.sh", "apt_install ${packages.joinToString(" ")}")
    }
}

internal fun ScriptBuildStep.scriptFile(fileName: String, suffix: String = "") {
    scriptContent = scriptContent(fileName) + suffix
}

internal fun PowerShellStep.scriptFile(fileName: String, suffix: String = "") {
    scriptMode = script {
        content = scriptContent(fileName) + suffix
    }
}

private fun scriptContent(fileName: String) = dslFile("scripts/$fileName").readText()

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
    set -eo pipefail
""".trimIndent()

internal fun bashScript(@Language("bash") content: String): String = "$bashPreamble\n${content.trimIndent()}"

internal fun pythonScript(@Language("python") content: String): String = "#!/usr/bin/env python3\n${content.trimIndent()}"

internal fun powerShellScript(@Language("powershell") content: String): String = content.trimIndent()

private object DslFiles

/**
 * Finds a file that the DSL reads, such as a script from `scripts`.
 * A settings checkout has these files in the working directory, where a file overrides the one from the classpath.
 * A project that uses the DSL as a library has them only on the classpath, in the library jar.
 */
internal fun dslFile(path: String): Path {
    val local = Path(path)
    if (local.exists()) return local

    val resource = requireNotNull(DslFiles::class.java.getResource("/$path")) {
        "$path is missing from the working directory and the classpath"
    }
    val uri = resource.toURI()
    return try {
        uri.toPath()
    } catch (_: FileSystemNotFoundException) {
        // The file system of the jar stays open, as the DSL reads other files from it
        FileSystems.newFileSystem(uri, emptyMap<String, Any>())
        uri.toPath()
    }
}

/** Reads a file to be written by a build step, escaping `%`, which TeamCity would otherwise take for a parameter reference. */
internal fun readFileForStep(file: Path): String = file.readText().trimEnd().replace("%", "%%")

/**
 * A step running [prepare], then writing [files] by path with `/` separators, which can refer to PowerShell variables.
 * Contents are written with verbatim here-strings, which PowerShell doesn't expand.
 * Uses PowerShell Core, which is installed on agents of all operating systems, so the same step works on any agent.
 */
internal fun writeFilesStep(name: String, files: Map<String, String>, @Language("powershell") prepare: String = "") =
    PowerShellStep {
        this.name = name
        edition = PowerShellStep.Edition.Core
        scriptMode = script {
            val directories = files.keys.map { it.substringBeforeLast('/') }.distinct()
            val createDirectories = directories.joinToString(
                prefix = "New-Item -ItemType Directory -Force ",
                postfix = " | Out-Null",
                transform = { "\"$it\"" }
            )
            val writeFiles = files.map { (path, content) -> "[IO.File]::WriteAllText(\"$path\", @'\n$content\n'@)" }
            content =
                (listOf("\$ErrorActionPreference = 'Stop'", prepare.trimIndent(), createDirectories) + writeFiles)
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
        }
    }
