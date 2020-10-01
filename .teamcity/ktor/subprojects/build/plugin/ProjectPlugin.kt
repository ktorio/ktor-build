package ktor.subprojects.build.plugin

import jetbrains.buildServer.configs.kotlin.v2019_2.*

object ProjectPlugin : Project({
    id("KtorPlugin")
    name = "Plugin"
    description = "Code for IntelliJ IDEA plugin"
})