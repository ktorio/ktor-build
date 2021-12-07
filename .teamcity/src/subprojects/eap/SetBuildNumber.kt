package subprojects.eap

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*


object SetBuildNumber : BuildType({
    id("SetBuildNumberBuild")
    name = "Set EAP Build Number"
    buildNumberPattern = eapVersion
    requirements {
        require(linux.agentString)
    }
    vcs {
        root(VCSCoreEAP)
    }
})
