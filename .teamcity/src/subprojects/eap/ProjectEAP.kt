package subprojects.eap

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.build.*
import subprojects.build.core.*

object ProjectEAP : Project({
    id("ProjectKtorEAP")
    name = "Release Ktor EAP"
    description = "Release EAP builds. Only binaries. Published to Space"

    subProject(ProjectCore)
    subProject(ProjectPublishEAPToSpace)

    params {
        defaultTimeouts()
        text("releaseVersion", "-eap", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }
})