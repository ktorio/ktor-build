package subprojects.eap

import jetbrains.buildServer.configs.kotlin.v2019_2.*


object SetBuildNumber: BuildType( {
    id("SetBuildNumberBuild")
    name = "Set EAP Build Number"
    buildNumberPattern = eapVersion
})
