package ktor.subprojects.release

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object ProjectRelease : Project({
    id("KtorRelease".toExtId())
    name = "Release"
    description = "Build configuration that release Ktor"
})
