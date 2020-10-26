package subprojects.release

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import subprojects.release.apidocs.ProjectReleaseAPIDocs

object ProjectRelease : Project({
    id("ProjectKtorRelease")
    name = "Release"
    description = "Build configuration that release Ktor"

    params {
        param("sign.key.id", value = "7C30F7B1329DBA87")
        password("sign.key.passphrase", value = "credentialsJSON:59f4247e-21d9-4354-a0f0-3051fd16ef5d")
        password("sign.key.private", value = "credentialsJSON:1196162d-f166-4302-b179-6e463bc5c327")
    }

    subProject(ProjectReleaseAPIDocs)
})
