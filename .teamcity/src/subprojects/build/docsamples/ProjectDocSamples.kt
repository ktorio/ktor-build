package subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.samples.validateSamples

const val relativeDir = "codeSnippets"

object ProjectDocSamples : Project({
  id("ProjectKtorDocs")
  name = "Docs"
  description = "Code samples included in Docs and API Docs"

  vcsRoot(VCSDocs)

  buildType {
    id("KtorDocs_ValidateSamples")
    name = "Build and test code samples"

    vcs {
      root(VCSDocs)
    }

    steps {
      validateSamples(relativeDir)
    }
  }
})