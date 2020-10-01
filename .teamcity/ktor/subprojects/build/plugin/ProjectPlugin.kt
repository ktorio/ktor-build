package ktor.subprojects.build.plugin

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object ProjectPlugin : Project({
    id("KtorPlugin".toExtId())
    name = "Plugin"
    description = "Code for IntelliJ IDEA plugin"
})