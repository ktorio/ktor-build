package subprojects.release

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

fun BuildSteps.installCocoapods() {
    script {
        name = "Install Cocoapods"
        scriptContent = bashScript("""
            if ! command -v pod >/dev/null 2>&1; then                                                                                                                                                                                                                                                                                
              gem install cocoapods --no-document                                                                                                                                                                                                                                                                                    
            fi         
            pod --version
        """)
    }
}
