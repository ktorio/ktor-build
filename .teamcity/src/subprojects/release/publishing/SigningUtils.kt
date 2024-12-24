package subprojects.release.publishing

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

internal fun BuildSteps.prepareKeyFile(os: String) {
    if (os == "Windows" || os == "Auto") {
        powerShell {
            name = "Prepare Keys (Windows)"
            scriptFile("prepare_keys.ps1")

            conditions { isWindows() }
        }
    }

    if (os != "Windows") {
        script {
            name = "Prepare Keys (Unix)"
            scriptFile("prepare_keys.sh")

            conditions { isNotWindows() }
        }
    }
}

internal fun BuildSteps.cleanupKeyFile(os: String) {
    if (os == "Windows" || os == "Auto") {
        powerShell {
            name = "Cleanup Keys (Windows)"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptMode = script {
                content = """
                    rm -r -fo C:\Users\builduser\.gnupg
                """
            }

            conditions { isWindows() }
        }
    }

    if (os != "Windows") {
        script {
            name = "Cleanup Keys (Unix)"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = "rm -rf .gnupg"

            conditions { isNotWindows() }
        }
    }
}
