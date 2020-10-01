package ktor.subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.v2019_2.*

object ProjectSamples : Project({
    id("KtorSamples")
    name = "Samples"
    description = "Code samples"
})