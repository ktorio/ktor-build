package ktor.subprojects.release

import jetbrains.buildServer.configs.kotlin.v2019_2.*

object ProjectRelease : Project({
    id("KtorRelease")
    name = "Release"
    description = "Build configuration that release Ktor"
})
