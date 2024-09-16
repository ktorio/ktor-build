package subprojects.build.plugin

import jetbrains.buildServer.configs.kotlin.*

object ProjectPlugin : Project({
    id("ProjectKtorPlugin")
    name = "Intellij Plugin"
    description = "Code for IntelliJ IDEA plugin"
})
