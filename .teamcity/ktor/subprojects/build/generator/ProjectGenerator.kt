package ktor.subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object ProjectGenerator : Project({
    id("KtorGenerator".toExtId())
    name = "Generator"
    description = "Code for start.ktor.io"
})