package subprojects.release

import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object ProjectRelease : Project({
    id("ProjectKtorRelease")
    name = "Release"
    description = "Build configuration that release Ktor"
})
