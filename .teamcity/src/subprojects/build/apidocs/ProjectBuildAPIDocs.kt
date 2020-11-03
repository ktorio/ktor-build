package subprojects.build.apidocs

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
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

object BuildDokka: BuildType({
    id("KtorAPIDocs_Dokka")
    name = "Build Dokka artifacts"

    vcs {
        root(VCSCore)
    }

    requirements {
        require(os = "Linux", minMemoryMB = 7000)
    }

    artifactRules = formatArtifacts("+:apidoc => apidoc.zip")

    steps {
        gradle {
            name = "Run Dokka"
            tasks = "dokkaWebsite"
        }
    }
})
