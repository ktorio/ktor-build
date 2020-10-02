package ktor.subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project


object ProjectDocSamples : Project({
  name = "Doc Code"
  description = "Code samples included in Docs"
})
