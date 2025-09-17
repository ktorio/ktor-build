package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.Agents.OS

fun BuildSteps.installRust(os: OS) {
    if (os == OS.Windows) {
        powerShell {
            name = "Install Rust (rustup)"
            scriptFile("install_rust_windows.ps1")
        }
    } else {
        script {
            name = "Install Rust (rustup)"
            scriptFile("install_rust_unix.sh")
        }
    }
}

fun BuildType.enableRustForRelevantChanges(os: OS) {
    params {
        param("env.OPERATING_SYSTEM", os.name)
    }
    steps {
        // TODO: Add a script for Windows to
        if (os != OS.Windows) {
            script {
                name = "Check for Rust module changes"
                scriptFile("check_modified_rust_modules.sh")
            }
        }
    }
}

fun BuildSteps.setupRustAarch64CrossCompilation(os: OS) {
    require(os == OS.Linux) { "Can be used only on Linux" }
    script {
        name = "Setup Rust aarch64 cross compilation"
        scriptContent = """
            sudo apt-get update
            sudo apt-get install -y --no-install-recommends gcc-aarch64-linux-gnu libc6-dev-arm64-cross
            
            rustup target add aarch64-unknown-linux-gnu
            
            mkdir -p .cargo
            echo '[target.aarch64-unknown-linux-gnu]' > .cargo/config.toml
            echo 'linker = "aarch64-linux-gnu-gcc"' >> .cargo/config.toml
        """.trimIndent()
    }
}
