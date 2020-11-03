package subprojects.build.apidocs

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.build.*
import subprojects.release.*

object ProjectBuildAPIDocs : Project({
    id("ProjectKtorBuildAPIDocs")
    name = "API Docs"

    params {
        defaultTimeouts()
    }
    apiBuild = BuildDokka
    buildType(apiBuild ?: throw RuntimeException("ProjectBuildApiDocs is null"))
})

