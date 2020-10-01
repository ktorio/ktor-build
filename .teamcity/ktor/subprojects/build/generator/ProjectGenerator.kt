package ktor.subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.*

object ProjectGenerator : Project({
    id("KtorGenerator")
    name = "Generator"
    description = "Code for start.ktor.io"
})