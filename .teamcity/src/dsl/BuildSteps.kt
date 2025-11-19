package dsl

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
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
