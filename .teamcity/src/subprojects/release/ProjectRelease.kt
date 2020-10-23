package subprojects.release

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import subprojects.release.apidocs.ProjectReleaseAPIDocs

object ProjectRelease : Project({
    id("ProjectKtorRelease")
    name = "Release"
    description = "Build configuration that release Ktor"

    subProject(ProjectReleaseAPIDocs)
})
