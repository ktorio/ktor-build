package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object ProjectGenerator : Project({
    id("ProjectKtorGenerator")
    name = "Generator"
    description = "Code for start.ktor.io"
})