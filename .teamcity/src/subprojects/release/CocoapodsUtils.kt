package subprojects.release

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

fun BuildSteps.installCocoapods() {
    script {
        name = "Install Cocoapods"
        // Cocoapods is already installed via homebrew, and it uses Ruby 4.
        // We reinstall it to use the default Ruby version.
        scriptContent = bashScript("""
            brew uninstall cocoapods || true
            gem install cocoapods --no-document
            pod --version
        """)
    }
}
