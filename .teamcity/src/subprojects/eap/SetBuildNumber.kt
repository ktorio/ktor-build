package subprojects.eap

import jetbrains.buildServer.configs.kotlin.*
import subprojects.*

object SetBuildNumber : BuildType({
    id("SetBuildNumberBuild")
    name = "Set EAP Build Number"
    buildNumberPattern = eapVersion
    requirements {
        agent(Agents.OS.Linux, hardwareCapacity = Agents.ANY)
    }
    vcs {
        root(VCSCoreEAP)
    }
})
