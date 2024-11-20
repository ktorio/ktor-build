package subprojects.eap

import jetbrains.buildServer.configs.kotlin.*
import subprojects.*
import subprojects.build.*

object SetBuildNumber : BuildType({
    id("SetBuildNumberBuild")
    name = "Set EAP Build Number"
    buildNumberPattern = eapVersion
    requirements {
        agent(linux, Agents.ANY)
    }
    vcs {
        root(VCSCoreEAP)
    }
})
