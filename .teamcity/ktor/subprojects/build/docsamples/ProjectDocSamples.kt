package ktor.subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.v2019_2.*


object ProjectDocSamples : Project({
  id("KtorDocSamples")
  name = "Doc Code"
  description = "Code samples included in Docs"
})
