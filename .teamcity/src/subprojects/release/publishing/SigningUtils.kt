package subprojects.release.publishing

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

internal fun BuildSteps.prepareKeyFile() {
    powerShell {
        name = "Prepare Keys (Windows)"
        scriptMode = script {
            content = """
                md ${'$'}Env:SIGN_KEY_LOCATION -force
                cd ${'$'}Env:SIGN_KEY_LOCATION
                echo "Stopping gpg-agent and removing GNUPG folder"
                Stop-Process -Name "gpg-agent" -ErrorAction SilentlyContinue
                rm -r -fo C:\Users\builduser\.gnupg

                # Hard-coding path for GPG since this fails on TeamCity
                # ${'$'}gpg=(get-command gpg.exe).Path
                ${'$'}gpg="C:\Program Files\Git\usr\bin\gpg.exe"
                Set-Alias -Name gpg2.exe -Value ${'$'}gpg

                echo "Exporting public key"
                [System.IO.File]::WriteAllText("${'$'}pwd\keyfile", ${'$'}Env:SIGN_KEY_PUBLIC)
                & ${'$'}gpg --batch --import keyfile
                rm keyfile


                echo "Exporting private key"
                [System.IO.File]::WriteAllText("${'$'}pwd\keyfile", ${'$'}Env:SIGN_KEY_PRIVATE)
                & ${'$'}gpg --allow-secret-key-import --batch --import keyfile
                rm keyfile
                & ${'$'}gpg --list-keys

                echo "Sending keys"
                & ${'$'}gpg --keyserver hkps://keyserver.ubuntu.com --send-keys %env.SIGN_KEY_ID%

                & "gpgconf" --kill gpg-agent
                & "gpgconf" --homedir "/c/Users/builduser/.gnupg" --launch gpg-agent
            """.trimIndent()
        }

        conditions { isWindows() }
    }

    script {
        name = "Prepare Keys (Unix)"
        scriptContent = """
            #!/bin/sh
            set -eux pipefail
            mkdir -p %env.SIGN_KEY_LOCATION%
            cd "%env.SIGN_KEY_LOCATION%"
            export HOME=${'$'}(pwd)
            export GPG_TTY=${'$'}(tty)
            rm -rf .gnupg
            echo "Exporting public key"
            cat >keyfile <<EOT
            %env.SIGN_KEY_PUBLIC%
            EOT
            gpg --batch --import keyfile
            rm -v keyfile
            echo "Exporting private key"
            cat >keyfile <<EOT
            %env.SIGN_KEY_PRIVATE%
            EOT
            gpg --allow-secret-key-import --batch --import keyfile
            rm -v keyfile
            echo "Sending keys"
            gpg --keyserver hkps://keyserver.ubuntu.com --send-keys %env.SIGN_KEY_ID%
        """.trimIndent()
        workingDir = "."

        conditions { isNotWindows() }
    }
}

internal fun BuildSteps.cleanupKeyFile() {
    powerShell {
        name = "Cleanup Keys (Windows)"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptMode = script {
            content = """
                rm -r -fo C:\Users\builduser\.gnupg
            """.trimIndent()
        }

        conditions { isWindows() }
    }

    script {
        name = "Cleanup Keys (Unix)"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
            cd .
            rm -rf .gnupg
        """.trimIndent()
        workingDir = "."

        conditions { isNotWindows() }
    }
}
