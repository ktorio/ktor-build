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
    steps {
        script {
            name = "Check for Rust module changes"
            scriptContent = """
                #!/bin/bash
                
                CHANGED_FILES="%system.teamcity.build.changedFiles.file%"
                OPERATING_SYSTEM="${os.name}"
               
                # Check if any files match your path pattern
                if echo "${'$'}CHANGED_FILES" | grep -q "-rs"; then
                    echo "Changes detected for files with '-rs' in their path"
                    export KTOR_RUST_COMPILATION="true"
                    echo "##teamcity[setParameter name='env.KTOR_RUST_COMPILATION' value='${'$'}KTOR_RUST_COMPILATION']"
                fi 
                
                # Set platform-specific Konan properties for Linux
                if [ "${'$'}OPERATING_SYSTEM" = "Linux" ] && [ "${'$'}KTOR_RUST_COMPILATION" = "true" ]; then
                    echo "Setting Linux-specific Konan properties"
                    export KTOR_OVERRIDE_KONAN_PROPERTIES="targetSysRoot.linux_x64=/;libGcc.linux_x64=/usr/lib/gcc/x86_64-linux-gnu/13;linker.linux_x64=/usr/bin/ld.bfd;crtFilesLocation.linux_x64=/usr/lib/x86_64-linux-gnu/"
                    echo "##teamcity[setParameter name='env.KTOR_OVERRIDE_KONAN_PROPERTIES' value='${'$'}KTOR_OVERRIDE_KONAN_PROPERTIES']"
                fi
            """.trimIndent()
            executionMode = BuildStep.ExecutionMode.ALWAYS
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
