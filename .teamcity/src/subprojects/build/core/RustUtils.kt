package subprojects.build.core

import dsl.scriptFile
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.buildSteps.powerShell
import jetbrains.buildServer.configs.kotlin.buildSteps.script
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

fun BuildTypeSettings.enableRustCompilation(os: OS) {
    params {
        param("env.KTOR_RUST_COMPILATION", "true")
        if (os == OS.Linux) {
            param(
                "env.KTOR_OVERRIDE_KONAN_PROPERTIES",
                "targetSysRoot.linux_x64=/;libGcc.linux_x64=/usr/lib/gcc/x86_64-linux-gnu/13;linker.linux_x64=/usr/bin/ld.bfd;crtFilesLocation.linux_x64=/usr/lib/x86_64-linux-gnu/"
            )
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
