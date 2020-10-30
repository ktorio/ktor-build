package subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.release.*

const val relativeDir = "codeSnippets"

object ProjectDocSamples : Project({
  id("ProjectKtorDocs")
  name = "Docs"
  description = "Code samples included in Docs and API Docs"

  vcsRoot(VCSDocs)

  samplesBuild = buildType {
    id("KtorDocs_ValidateSamples")
    name = "Build and test code samples"

    vcs {
      root(VCSDocs)
    }

    steps {
      gradle {
        name = "Build"
        tasks = "clean build"
        workingDir = relativeDir
      }
      gradle {
        name = "Test"
        tasks = "test"
        workingDir = relativeDir
      }
    }
  }
})