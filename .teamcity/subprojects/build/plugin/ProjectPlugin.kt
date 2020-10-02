package subprojects.build.plugin

import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object ProjectPlugin : Project({
    name = "Plugin"
    description = "Code for IntelliJ IDEA plugin"
})