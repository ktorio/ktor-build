package subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object ProjectSamples : Project({
    id("ProjectKtorSamples")
    name = "Samples"
    description = "Code samples"
})