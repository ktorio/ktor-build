package subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.v2019_2.Project


object ProjectDocSamples : Project({
  id("ProjectKtorDocSamples")
  name = "Doc Code"
  description = "Code samples included in Docs"
})
