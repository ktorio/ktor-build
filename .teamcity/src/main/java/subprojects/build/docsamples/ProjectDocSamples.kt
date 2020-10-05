package subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.v2019_2.Project


object ProjectDocSamples : Project({
  id("ProjectKtorDocs")
  name = "Docs"
  description = "Code samples included in Docs and API Docs"
})
