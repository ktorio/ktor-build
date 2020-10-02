package ktor.subprojects.build.samples

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object ProjectSamples : Project({
    name = "Samples"
    description = "Code samples"
})